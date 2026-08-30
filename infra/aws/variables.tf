variable "aws_region" {
  description = "AWS region for all resources."
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Short name used in resource names and tags."
  type        = string
  default     = "seatforge"
}

variable "environment" {
  description = "Deployment environment name."
  type        = string
  default     = "production"
}

variable "availability_zone_count" {
  description = "Number of availability zones. The baseline requires two."
  type        = number
  default     = 2

  validation {
    condition     = var.availability_zone_count == 2
    error_message = "This baseline currently supports exactly two availability zones."
  }
}

variable "allowed_cidr" {
  description = "CIDR allowed to reach the public HTTPS listener."
  type        = string
  default     = "0.0.0.0/0"
}

variable "certificate_arn" {
  description = "ACM certificate ARN used by the public HTTPS listener."
  type        = string
}

variable "api_image" {
  description = "Immutable API container image URI."
  type        = string
}

variable "worker_image" {
  description = "Immutable notification worker container image URI."
  type        = string
}

variable "web_image" {
  description = "Immutable web container image URI built with the production public API URL."
  type        = string
}

variable "database_username" {
  description = "PostgreSQL administrator username."
  type        = string
  default     = "ticketing"
}

variable "database_password" {
  description = "PostgreSQL administrator password. Supply through an encrypted variable store."
  type        = string
  sensitive   = true
}

variable "rabbitmq_username" {
  description = "RabbitMQ application username."
  type        = string
  default     = "ticketing"
}

variable "rabbitmq_password" {
  description = "RabbitMQ application password. Supply through an encrypted variable store."
  type        = string
  sensitive   = true
}

variable "jwt_secret" {
  description = "Random HMAC secret containing at least 32 bytes."
  type        = string
  sensitive   = true
}

variable "rabbitmq_engine_version" {
  description = "Amazon MQ RabbitMQ engine version available in the selected region."
  type        = string
  default     = "3.13"
}

variable "api_desired_count" {
  description = "Number of API tasks."
  type        = number
  default     = 2
}

variable "worker_desired_count" {
  description = "Number of notification worker tasks."
  type        = number
  default     = 2
}

variable "web_desired_count" {
  description = "Number of web tasks."
  type        = number
  default     = 2
}
