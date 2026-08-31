variable "aws_region" {
  description = "AWS region for the demo resources."
  type        = string
  default     = "eu-west-2"
}

variable "project_name" {
  description = "Short lowercase name used in resources and tags."
  type        = string
  default     = "seatforge"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,20}$", var.project_name))
    error_message = "project_name must start with a lowercase letter and contain only lowercase letters, digits, or hyphens."
  }
}

variable "environment" {
  description = "Deployment environment name."
  type        = string
  default     = "demo"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,16}$", var.environment))
    error_message = "environment must start with a lowercase letter and contain only lowercase letters, digits, or hyphens."
  }
}

variable "instance_type" {
  description = "Burstable x86 instance size. t3.small is intended only for low-traffic demonstrations."
  type        = string
  default     = "t3.small"

  validation {
    condition     = contains(["t3.small", "t3.medium", "t3.large"], var.instance_type)
    error_message = "instance_type must be t3.small, t3.medium, or t3.large so the x86 release images and memory plan remain compatible."
  }
}

variable "root_volume_size_gib" {
  description = "Encrypted gp3 root volume size in GiB."
  type        = number
  default     = 30

  validation {
    condition     = var.root_volume_size_gib >= 24 && var.root_volume_size_gib <= 100
    error_message = "root_volume_size_gib must be between 24 and 100 GiB."
  }
}

variable "site_address" {
  description = "Caddy site address. Use :80 for public-IP HTTP or a DNS hostname for automatic HTTPS."
  type        = string
  default     = ":80"

  validation {
    condition     = var.site_address == ":80" || can(regex("^[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$", var.site_address))
    error_message = "site_address must be :80 or a plain DNS hostname without a scheme or path."
  }
}

variable "public_cidr" {
  description = "IPv4 CIDR allowed to access HTTP and HTTPS."
  type        = string
  default     = "0.0.0.0/0"

  validation {
    condition     = can(cidrnetmask(var.public_cidr))
    error_message = "public_cidr must be a valid IPv4 CIDR."
  }
}

variable "budget_email" {
  description = "Email address that receives AWS Budget notifications."
  type        = string

  validation {
    condition     = can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", var.budget_email))
    error_message = "budget_email must be a valid email address."
  }
}

variable "monthly_budget_usd" {
  description = "Monthly cost budget in US dollars. Notifications are sent at 50, 80, and 100 percent."
  type        = number
  default     = 25

  validation {
    condition     = var.monthly_budget_usd >= 1 && var.monthly_budget_usd <= 500
    error_message = "monthly_budget_usd must be between 1 and 500."
  }
}

variable "seed_demo" {
  description = "Create the portfolio event and buyer account. The EC2 bootstrap randomizes the organizer password."
  type        = bool
  default     = true
}

variable "deployment_artifact_retention_days" {
  description = "Number of days before uploaded deployment archives expire from the private S3 bucket."
  type        = number
  default     = 1

  validation {
    condition     = floor(var.deployment_artifact_retention_days) == var.deployment_artifact_retention_days && var.deployment_artifact_retention_days >= 1 && var.deployment_artifact_retention_days <= 7
    error_message = "deployment_artifact_retention_days must be an integer between 1 and 7."
  }
}
