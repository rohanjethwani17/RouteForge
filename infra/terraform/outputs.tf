output "vpc_id" {
  description = "VPC ID"
  value       = module.vpc.vpc_id
}

output "msk_bootstrap_servers" {
  description = "MSK Kafka bootstrap servers"
  value       = module.msk.bootstrap_servers
}

output "redis_endpoint" {
  description = "ElastiCache Redis endpoint"
  value       = module.elasticache.redis_endpoint
}

output "rds_endpoint" {
  description = "RDS PostgreSQL endpoint"
  value       = module.rds.db_endpoint
}

output "alb_dns_name" {
  description = "Application Load Balancer DNS name"
  value       = module.alb.alb_dns_name
}

output "ecs_cluster_name" {
  description = "ECS cluster name"
  value       = module.ecs.cluster_name
}

output "api_gateway_url" {
  description = "API Gateway URL"
  value       = "http://${module.alb.alb_dns_name}/api"
}
