# AWS Production Baseline

This Terraform baseline creates a two-availability-zone VPC, private ECS Fargate services, an HTTPS Application Load Balancer, encrypted PostgreSQL, encrypted Redis, an active/standby Amazon MQ RabbitMQ broker, Secrets Manager storage, CloudWatch logs, and API CPU autoscaling.

It intentionally does not create a Terraform state backend, DNS zone, image registry, paging integration, or payment-provider secret. Those resources normally belong to an organization's shared platform account.

## Prerequisites

- Terraform 1.15 or newer
- AWS credentials with permission to create the declared resources
- Three immutable images in ECR or another registry reachable from ECS
- An ACM certificate in the deployment region
- A DNS name whose alias will target the output load balancer

Build the web image with its public same-origin API address:

```bash
docker build --build-arg NEXT_PUBLIC_API_URL=https://tickets.example.com -t <web-image> web
```

Create a private `terraform.tfvars` file. Never commit it:

```hcl
certificate_arn   = "arn:aws:acm:us-east-1:123456789012:certificate/example"
api_image         = "123456789012.dkr.ecr.us-east-1.amazonaws.com/seatforge-api@sha256:example"
worker_image      = "123456789012.dkr.ecr.us-east-1.amazonaws.com/seatforge-worker@sha256:example"
web_image         = "123456789012.dkr.ecr.us-east-1.amazonaws.com/seatforge-web@sha256:example"
database_password = "replace-with-a-secret"
rabbitmq_password = "replace-with-a-secret"
jwt_secret        = "replace-with-at-least-32-random-bytes"
```

Initialize, review, and apply:

```bash
terraform init
terraform fmt -check
terraform validate
terraform plan -out production.tfplan
terraform apply production.tfplan
```

Before serving real traffic, configure remote state locking, DNS, AWS WAF, alert routing, OpenTelemetry export, secret rotation, restore testing, budget alarms, and a deployment pipeline with an approval-protected production environment.
