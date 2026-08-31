output "aws_region" {
  description = "AWS region used by deployment commands."
  value       = var.aws_region
}

output "instance_id" {
  description = "EC2 instance managed through AWS Systems Manager."
  value       = aws_instance.demo.id
}

output "public_ipv4" {
  description = "Stable public IPv4 address. Public IPv4 hourly charges apply."
  value       = aws_eip.demo.public_ip
}

output "site_url" {
  description = "Expected public URL after the application is deployed."
  value       = var.site_address == ":80" ? "http://${aws_eip.demo.public_ip}" : "https://${var.site_address}"
}

output "deployment_bucket" {
  description = "Private short-lived bucket used by the deployment script."
  value       = aws_s3_bucket.deployment.id
}

output "ssm_session_command" {
  description = "Command for opening an administrative shell without SSH."
  value       = "aws ssm start-session --region ${var.aws_region} --target ${aws_instance.demo.id}"
}
