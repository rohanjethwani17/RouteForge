variable "project_name" {
  description = "Project name for resource naming"
  type        = string
  default     = "routeforge"
}

variable "environment" {
  description = "Environment name (dev, staging, production)"
  type        = string
  default     = "production"
}

variable "aws_region" {
  description = "AWS region for resources"
  type        = string
  default     = "us-east-1"
}

# VPC Configuration
variable "vpc_cidr" {
  description = "CIDR block for VPC"
  type        = string
  default     = "10.0.0.0/16"
}

# RDS Configuration
variable "db_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t3.micro"
}

variable "db_allocated_storage" {
  description = "Allocated storage for RDS in GB"
  type        = number
  default     = 20
}

variable "db_password" {
  description = "Database password (use AWS Secrets Manager in production)"
  type        = string
  sensitive   = true
  default     = "" # Must be provided via tfvars or environment variable
}

# ElastiCache Configuration
variable "redis_node_type" {
  description = "ElastiCache Redis node type"
  type        = string
  default     = "cache.t3.micro"
}

# MSK Configuration
variable "kafka_instance_type" {
  description = "MSK Kafka broker instance type"
  type        = string
  default     = "kafka.t3.small"
}

variable "kafka_broker_count" {
  description = "Number of Kafka brokers"
  type        = number
  default     = 2
  
  validation {
    condition     = var.kafka_broker_count >= 2
    error_message = "Kafka broker count must be at least 2 for high availability."
  }
}

# ECS Configuration
variable "ecs_task_cpu" {
  description = "ECS task CPU units (256 = 0.25 vCPU)"
  type        = string
  default     = "256"
}

variable "ecs_task_memory" {
  description = "ECS task memory in MB"
  type        = string
  default     = "512"
}
