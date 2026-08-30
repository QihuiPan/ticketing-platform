output "load_balancer_dns_name" {
  description = "Create a DNS alias to this load balancer."
  value       = aws_lb.main.dns_name
}

output "database_address" {
  description = "Private PostgreSQL writer address."
  value       = aws_db_instance.main.address
}

output "redis_address" {
  description = "Private Redis primary address."
  value       = aws_elasticache_replication_group.main.primary_endpoint_address
}

output "rabbitmq_endpoint" {
  description = "Private RabbitMQ TLS endpoint."
  value       = local.rabbitmq_endpoint
  sensitive   = true
}
