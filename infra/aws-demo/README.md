# AWS Single-Node Portfolio Demo

This Terraform stack provides a cost-controlled public demonstration environment for SeatForge. It keeps PostgreSQL, Redis, RabbitMQ, the API, notification worker, web application, and Caddy on one Amazon EC2 instance. The full multi-AZ production baseline remains unchanged in [`../aws`](../aws/README.md).

This stack is deliberately **not** a production substitute. It has one host, one availability zone, no automatic database snapshots, no horizontal scaling, and a maintenance window whenever the instance is replaced. AWS credits may cover it temporarily, but the resources are not permanently free.

## Design

```text
Internet
   |
   v
Caddy :80/:443
   |---- /api/* ----------> Spring Boot API
   |---- everything else -> Next.js web
                               |
       PostgreSQL <---- API/worker ----> RabbitMQ
                           |
                         Redis
```

The Terraform stack creates:

- One encrypted Amazon Linux 2023 `t3.small` instance with a 4 GiB swap file
- One encrypted gp3 root volume for the host and Docker volumes
- One stable public IPv4 address
- A VPC with one public subnet and no NAT Gateway or load balancer
- A security group exposing only HTTP and HTTPS
- AWS Systems Manager access instead of public SSH
- A private, short-lived S3 deployment bucket
- An AWS Budget with forecasted and actual email notifications

The instance generates the database, RabbitMQ, and JWT secrets locally during first boot. Terraform does not receive these secret values. They are stored at `/opt/seatforge/.env` with owner-only permissions and persist across application deployments.

## Cost boundaries

The account-wide monthly budget defaults to USD 25 and sends notifications at:

- 50 percent forecasted spend
- 80 percent actual spend
- 100 percent actual spend

AWS Budgets is an alerting control, not a hard spending cap. It will not stop or delete resources. EC2 runtime, EBS storage, the public IPv4 address, outbound data, and any retained S3 objects can still incur charges. New-account credits may offset these charges until the credits expire or are depleted.

Use the multi-AZ production baseline only when availability, managed backups, scaling, and organizational operations justify the higher cost.

## Prerequisites

- Terraform 1.15 or newer
- AWS CLI v2 authenticated to the target account
- Git
- PowerShell 7 for the provided deployment script
- Permission to create EC2, VPC, IAM, S3, Systems Manager, Elastic IP, and AWS Budgets resources

The deployment is managed through Systems Manager, so an SSH key is not required.

## 1. Configure Terraform

Copy the example variables file:

```powershell
Set-Location infra/aws-demo
Copy-Item terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars` and set at least the budget notification address:

```hcl
aws_region         = "eu-west-2"
budget_email       = "owner@example.com"
monthly_budget_usd = 25
instance_type      = "t3.small"
site_address       = ":80"
seed_demo          = true
```

With `seed_demo = true`, the public buyer credential remains `buyer@example.com` / `DemoBuyer123!` so reviewers can exercise the booking flow. The EC2 bootstrap generates a random organizer password and stores it only in `/opt/seatforge/.env`, preventing public use of the privileged account. Set `seed_demo = false` if the site should start without the portfolio event or demonstration accounts.

## 2. Review and create the infrastructure

```powershell
terraform init
terraform fmt -check
terraform validate
terraform plan -out demo.tfplan
terraform apply demo.tfplan
```

Review the plan before applying it. `terraform apply` creates billable AWS resources even when promotional credits are available.

The useful outputs are:

```powershell
terraform output public_ipv4
terraform output site_url
terraform output ssm_session_command
```

## 3. Optional domain and automatic HTTPS

The default `site_address = ":80"` serves HTTP on the public IPv4 address. To use automatic HTTPS:

1. Apply the stack and copy the `public_ipv4` output.
2. Create a DNS A record for the chosen hostname.
3. Set `site_address` to the hostname without `https://` or a path.
4. Apply the updated Terraform plan.

Example:

```hcl
site_address = "tickets.example.com"
```

Caddy requests and renews the public certificate after DNS resolves to the instance.

## 4. Deploy the application

Run the script from a clean, committed Git revision:

```powershell
./scripts/deploy.ps1
```

The script:

1. Archives the current Git commit without local secrets or uncommitted files.
2. Uploads the archive to the private S3 deployment bucket.
3. Uses Systems Manager Run Command to download it to the instance.
4. Builds the API, worker, and web images sequentially to limit memory pressure.
5. Starts the single-node Compose stack and verifies API health.
6. Removes the uploaded archive after a successful or failed deployment attempt.

The first build on `t3.small` can be slow because Maven, npm, and container layers are created on the host. Use `t3.medium` if the build cannot complete within the default 40-minute script timeout, then return to `t3.small` after the images exist if the runtime has sufficient memory.

## Operations

Open a shell without exposing SSH:

```powershell
aws ssm start-session --region eu-west-2 --target (terraform output -raw instance_id)
```

Inside the instance:

```bash
cd /opt/seatforge/current
sudo docker compose --env-file /opt/seatforge/.env -f infra/aws-demo/runtime/docker-compose.yml ps
sudo docker compose --env-file /opt/seatforge/.env -f infra/aws-demo/runtime/docker-compose.yml logs --tail=200
```

Retrieve the generated organizer credential only through the administrative session:

```bash
sudo grep '^DEMO_ORGANIZER_PASSWORD=' /opt/seatforge/.env
```

Deploy an update by committing it and running `./scripts/deploy.ps1` again. Named Docker volumes preserve PostgreSQL, Redis, RabbitMQ, and Caddy data between deployments.

## Shut down before credits expire

Destroy the demo when it is no longer needed:

```powershell
terraform plan -destroy -out destroy.tfplan
terraform apply destroy.tfplan
```

Before destroying the instance, export any data that must be retained. The encrypted root volume and its Docker volumes are deleted with the instance. Merely stopping the instance can leave EBS and public IPv4 charges in place.

## Limitations

- One host failure takes down every service.
- PostgreSQL data is stored on the instance root volume.
- Container builds share CPU, memory, and disk with the running application.
- Prometheus, Grafana, and the OpenTelemetry collector are omitted to reduce memory use.
- The budget notifies but does not enforce a spending limit.
- The shared buyer account can create demonstration holds and simulated orders; no real payment provider is connected.
- This deployment should not process real payments or sensitive personal data.

For real traffic, use the separate [`../aws`](../aws/README.md) baseline and complete its DNS, certificate, remote-state, alerting, restore-testing, secret-rotation, and payment-provider requirements.
