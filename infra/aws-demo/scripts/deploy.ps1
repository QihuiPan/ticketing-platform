[CmdletBinding()]
param(
    [string]$TerraformDirectory = (Join-Path $PSScriptRoot ".."),
    [int]$TimeoutSeconds = 2400
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Assert-Command {
    param([Parameter(Mandatory = $true)][string]$Name)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command was not found: $Name"
    }
}

function Invoke-Native {
    param(
        [Parameter(Mandatory = $true)][string]$Command,
        [Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments
    )

    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Command failed with exit code $LASTEXITCODE."
    }
}

Assert-Command "git"
Assert-Command "terraform"
Assert-Command "aws"

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$terraformRoot = (Resolve-Path $TerraformDirectory).Path
$temporaryRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("seatforge-deploy-" + [guid]::NewGuid().ToString("N"))
$bundlePath = Join-Path $temporaryRoot "seatforge-demo.zip"
$parametersPath = Join-Path $temporaryRoot "ssm-parameters.json"
$uploadedArtifact = $null

try {
    New-Item -ItemType Directory -Path $temporaryRoot | Out-Null

    Push-Location $repositoryRoot
    try {
        $workingTreeChanges = (& git status --porcelain) -join "`n"
        if ($LASTEXITCODE -ne 0) {
            throw "Unable to inspect the Git working tree."
        }
        if ($workingTreeChanges) {
            throw "Commit or stash all changes before deployment so the archive has a reproducible revision."
        }

        $revision = (& git rev-parse HEAD).Trim()
        if ($LASTEXITCODE -ne 0 -or -not $revision) {
            throw "Unable to resolve the Git revision."
        }

        Invoke-Native git archive --format=zip --output=$bundlePath $revision
    }
    finally {
        Pop-Location
    }

    $region = (& terraform "-chdir=$terraformRoot" output -raw aws_region).Trim()
    $instanceId = (& terraform "-chdir=$terraformRoot" output -raw instance_id).Trim()
    $bucket = (& terraform "-chdir=$terraformRoot" output -raw deployment_bucket).Trim()
    $siteUrl = (& terraform "-chdir=$terraformRoot" output -raw site_url).Trim()
    if ($LASTEXITCODE -ne 0 -or -not $region -or -not $instanceId -or -not $bucket) {
        throw "Terraform outputs are unavailable. Apply the aws-demo stack before running this script."
    }

    $objectKey = "releases/$revision.zip"
    $uploadedArtifact = "s3://$bucket/$objectKey"
    Write-Host "Uploading revision $revision to the private deployment bucket..."
    Invoke-Native aws s3 cp $bundlePath $uploadedArtifact --region $region --only-show-errors

    Write-Host "Waiting for the EC2 instance to register with AWS Systems Manager..."
    $registrationDeadline = [DateTime]::UtcNow.AddMinutes(10)
    do {
        $pingStatus = (& aws ssm describe-instance-information --region $region --filters "Key=InstanceIds,Values=$instanceId" --query "InstanceInformationList[0].PingStatus" --output text 2>$null).Trim()
        if ($LASTEXITCODE -eq 0 -and $pingStatus -eq "Online") {
            break
        }
        Start-Sleep -Seconds 10
    } while ([DateTime]::UtcNow -lt $registrationDeadline)

    if ($pingStatus -ne "Online") {
        throw "The EC2 instance did not become available in Systems Manager within ten minutes."
    }

    $remoteScript = @"
set -euo pipefail
archive='/tmp/seatforge-$revision.zip'
release_dir='/opt/seatforge/releases/$revision'
mkdir -p "`$release_dir"
aws s3 cp 's3://$bucket/$objectKey' "`$archive" --region '$region' --only-show-errors
unzip -oq "`$archive" -d "`$release_dir"
ln -sfn "`$release_dir" /opt/seatforge/current
cd /opt/seatforge/current
COMPOSE_PARALLEL_LIMIT=1 docker compose --env-file /opt/seatforge/.env -f infra/aws-demo/runtime/docker-compose.yml build --pull
systemctl restart seatforge-demo.service
docker compose --env-file /opt/seatforge/.env -f infra/aws-demo/runtime/docker-compose.yml exec -T caddy wget -qO- http://api:8080/actuator/health
docker compose --env-file /opt/seatforge/.env -f infra/aws-demo/runtime/docker-compose.yml ps
docker image prune --force
rm -f "`$archive"
"@

    $commands = @($remoteScript -split "`r?`n" | Where-Object { $_.Trim().Length -gt 0 })
    $parameters = @{
        commands         = $commands
        executionTimeout = @([string]$TimeoutSeconds)
    } | ConvertTo-Json -Depth 4
    [System.IO.File]::WriteAllText($parametersPath, $parameters, [System.Text.UTF8Encoding]::new($false))

    Write-Host "Building and starting the stack on the EC2 instance..."
    $commandId = (& aws ssm send-command --region $region --instance-ids $instanceId --document-name "AWS-RunShellScript" --comment "Deploy SeatForge $revision" --parameters "file://$parametersPath" --query "Command.CommandId" --output text).Trim()
    if ($LASTEXITCODE -ne 0 -or -not $commandId) {
        throw "Unable to start the Systems Manager deployment command."
    }

    $terminalStates = @("Success", "Cancelled", "TimedOut", "Failed", "Cancelling")
    $deploymentDeadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds + 120)
    do {
        Start-Sleep -Seconds 10
        $status = (& aws ssm get-command-invocation --region $region --command-id $commandId --instance-id $instanceId --query "Status" --output text 2>$null).Trim()
    } while ($terminalStates -notcontains $status -and [DateTime]::UtcNow -lt $deploymentDeadline)

    $stdout = (& aws ssm get-command-invocation --region $region --command-id $commandId --instance-id $instanceId --query "StandardOutputContent" --output text 2>$null) -join "`n"
    $stderr = (& aws ssm get-command-invocation --region $region --command-id $commandId --instance-id $instanceId --query "StandardErrorContent" --output text 2>$null) -join "`n"
    if ($stdout) {
        Write-Host $stdout
    }
    if ($status -ne "Success") {
        if ($stderr) {
            Write-Error $stderr
        }
        throw "Deployment ended with Systems Manager status: $status"
    }

    Write-Host "Deployment completed successfully."
    Write-Host "Site: $siteUrl"
}
finally {
    if ($uploadedArtifact) {
        & aws s3 rm $uploadedArtifact --region $region --only-show-errors 2>$null | Out-Null
    }
    $systemTemporaryRoot = [System.IO.Path]::GetTempPath()
    if ((Test-Path -LiteralPath $temporaryRoot) -and $temporaryRoot.StartsWith($systemTemporaryRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        Remove-Item -LiteralPath $temporaryRoot -Recurse -Force
    }
}
