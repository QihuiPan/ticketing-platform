[CmdletBinding()]
param(
    [ValidateRange(30, 1800)]
    [int]$TimeoutSeconds = 300
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

function Stop-Quickstart {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    [Console]::Error.WriteLine("ERROR: $Message")
    exit 1
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Stop-Quickstart "Docker is not installed. Install Docker Desktop and enable Docker Compose v2."
}

function Test-DockerCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    $previousPreference = $ErrorActionPreference
    $script:ErrorActionPreference = "SilentlyContinue"
    try {
        & docker @Arguments *> $null
        return $LASTEXITCODE -eq 0
    }
    finally {
        $script:ErrorActionPreference = $previousPreference
    }
}

if (-not (Test-DockerCommand -Arguments @("info"))) {
    Stop-Quickstart "The Docker engine is not running. Start Docker Desktop and run this script again."
}

if (-not (Test-DockerCommand -Arguments @("compose", "version"))) {
    Stop-Quickstart "Docker Compose v2 is required. Update Docker Desktop and run this script again."
}

if (-not (Test-Path -LiteralPath ".env")) {
    Copy-Item -LiteralPath ".env.example" -Destination ".env"
    Write-Host "Created .env from the local-development defaults."
}

Write-Host "Building and starting SeatForge..."
& docker compose up --build --detach
if ($LASTEXITCODE -ne 0) {
    Stop-Quickstart "Docker Compose could not start SeatForge. Review the output above."
}

$deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
while ([DateTimeOffset]::UtcNow -lt $deadline) {
    try {
        $health = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" -TimeoutSec 5
        if ($health.status -eq "UP") {
            Write-Host "SeatForge is ready."
            Write-Host "Web:        http://localhost:3000"
            Write-Host "API health: http://localhost:8080/actuator/health"
            Write-Host "Grafana:    http://localhost:3001"
            Write-Host "Run the documented smoke-test command to verify the booking journey."
            exit 0
        }
    }
    catch {
    }
    Start-Sleep -Seconds 3
}

[Console]::Error.WriteLine("ERROR: SeatForge did not become healthy within $TimeoutSeconds seconds.")
& docker compose ps
& docker compose logs --tail 80 api
exit 1
