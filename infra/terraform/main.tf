terraform {
  required_version = ">= 1.6.0"
  
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
  
  # Backend configuration for state management (optional but recommended)
  # backend "s3" {
  #   bucket         = "routeforge-terraform-state"
  #   key            = "production/terraform.tfstate"
  #   region         = "us-east-1"
  #   encrypt        = true
  #   dynamodb_table = "routeforge-terraform-locks"
  # }
}

provider "aws" {
  region = var.aws_region
  
  default_tags {
    tags = {
      Project     = var.project_name
      Environment = var.environment
      ManagedBy   = "Terraform"
    }
  }
}

# VPC and Networking
module "vpc" {
  source = "./modules/vpc"
  
  project_name = var.project_name
  environment  = var.environment
  vpc_cidr     = var.vpc_cidr
}

# MSK (Managed Kafka)
module "msk" {
  source = "./modules/msk"
  
  project_name        = var.project_name
  environment         = var.environment
  vpc_id              = module.vpc.vpc_id
  subnet_ids          = module.vpc.private_subnet_ids
  kafka_instance_type = var.kafka_instance_type
  kafka_broker_count  = var.kafka_broker_count
}

# ElastiCache (Redis)
module "elasticache" {
  source = "./modules/elasticache"
  
  project_name    = var.project_name
  environment     = var.environment
  vpc_id          = module.vpc.vpc_id
  subnet_ids      = module.vpc.private_subnet_ids
  redis_node_type = var.redis_node_type
}

# RDS (PostgreSQL)
module "rds" {
  source = "./modules/rds"
  
  project_name         = var.project_name
  environment          = var.environment
  vpc_id               = module.vpc.vpc_id
  subnet_ids           = module.vpc.private_subnet_ids
  db_instance_class    = var.db_instance_class
  db_allocated_storage = var.db_allocated_storage
  db_name              = "routeforge"
  db_username          = "routeforge"
  # In production, use AWS Secrets Manager for password
  db_password = var.db_password
}

# ECS Fargate
module "ecs" {
  source = "./modules/ecs"
  
  project_name  = var.project_name
  environment   = var.environment
  vpc_id        = module.vpc.vpc_id
  subnet_ids    = module.vpc.private_subnet_ids
  task_cpu      = var.ecs_task_cpu
  task_memory   = var.ecs_task_memory
  
  # Service endpoints from other modules
  kafka_bootstrap_servers = module.msk.bootstrap_servers
  redis_endpoint          = module.elasticache.redis_endpoint
  postgres_endpoint       = module.rds.db_endpoint
}

# Application Load Balancer
module "alb" {
  source = "./modules/alb"
  
  project_name   = var.project_name
  environment    = var.environment
  vpc_id         = module.vpc.vpc_id
  public_subnets = module.vpc.public_subnet_ids
  target_groups  = module.ecs.target_group_arns
}
