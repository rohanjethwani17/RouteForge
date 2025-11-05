# RouteForge AWS Infrastructure (Terraform)

⚠️ **COST WARNING**: Running these resources in AWS will incur charges. Review costs before applying.

## Overview

This Terraform configuration provisions AWS resources for RouteForge production deployment:

- **MSK (Managed Kafka)**: Event streaming backbone
- **ElastiCache (Redis)**: Hot vehicle position cache
- **RDS (PostgreSQL)**: Historical data storage
- **ECS Fargate**: Container orchestration for microservices
- **Application Load Balancer**: HTTPS ingress with path-based routing
- **VPC, Security Groups, IAM Roles**: Network and security infrastructure

## Cost Estimate (us-east-1, as of 2024)

| Resource | Configuration | Monthly Cost (USD) |
|----------|--------------|-------------------|
| MSK Kafka | 2x kafka.t3.small brokers | ~$150 |
| ElastiCache Redis | 1x cache.t3.micro | ~$15 |
| RDS PostgreSQL | 1x db.t3.micro | ~$15 |
| ECS Fargate | 3 services @ 0.25 vCPU, 0.5 GB | ~$30 |
| ALB | 1 load balancer + data transfer | ~$20 |
| NAT Gateway | 1 NAT gateway | ~$35 |
| **Total Estimated** | | **~$265/month** |

*Costs may vary based on usage, data transfer, and region. Add 20% buffer for overages.*

## Prerequisites

1. **AWS Account** with appropriate permissions
2. **Terraform** >= 1.6.0
3. **AWS CLI** configured with credentials
4. **S3 Bucket** for Terraform state (optional but recommended)

```bash
# Verify installations
terraform version
aws sts get-caller-identity
```

## Quick Start

### 1. Configure Variables

Copy the example variables file:

```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars`:

```hcl
project_name = "routeforge"
environment  = "production"
aws_region   = "us-east-1"

# VPC Configuration
vpc_cidr = "10.0.0.0/16"

# RDS Configuration
db_instance_class = "db.t3.micro"
db_allocated_storage = 20

# ElastiCache Configuration
redis_node_type = "cache.t3.micro"

# MSK Configuration
kafka_instance_type = "kafka.t3.small"
kafka_broker_count  = 2

# ECS Configuration
ecs_task_cpu    = "256"  # 0.25 vCPU
ecs_task_memory = "512"  # 0.5 GB
```

### 2. Initialize Terraform

```bash
terraform init
```

### 3. Review Plan

```bash
terraform plan -out=tfplan
```

**Review carefully:**
- Check estimated costs
- Verify resource names
- Ensure security group rules are correct

### 4. Apply Configuration

```bash
terraform apply tfplan
```

**Deployment takes ~20-30 minutes** for MSK cluster creation.

### 5. Get Outputs

```bash
terraform output
```

Outputs include:
- MSK bootstrap servers
- Redis endpoint
- RDS endpoint
- ALB DNS name
- ECS cluster name

## Module Structure

```
terraform/
├── README.md
├── main.tf                 # Root module
├── variables.tf            # Input variables
├── outputs.tf              # Output values
├── terraform.tfvars.example # Example configuration
├── modules/
│   ├── vpc/               # VPC, subnets, NAT
│   ├── msk/               # Managed Kafka cluster
│   ├── elasticache/       # Redis cluster
│   ├── rds/               # PostgreSQL database
│   ├── ecs/               # ECS Fargate services
│   └── alb/               # Application Load Balancer
└── scripts/
    └── destroy-safely.sh  # Safe teardown script
```

## Modules

### VPC Module

Creates:
- VPC with public/private subnets across 2 AZs
- Internet Gateway
- NAT Gateway (1 for cost optimization)
- Route tables

**Cost:** ~$35/month (NAT Gateway)

### MSK Module

Creates:
- MSK Kafka cluster with 2+ brokers
- Security groups
- CloudWatch log groups
- Topics: `vehicle_positions`, `vehicle_positions.dlq`

**Cost:** ~$150/month (2x kafka.t3.small)

**Production Recommendations:**
- Use kafka.m5.large for production workloads
- Enable encryption at rest and in transit
- Configure broker logs to CloudWatch

### ElastiCache Module

Creates:
- Redis replication group (1 primary, optional replicas)
- Subnet group
- Security group
- Parameter group with optimal settings

**Cost:** ~$15/month (cache.t3.micro)

**Production Recommendations:**
- Enable automatic failover (requires 2+ nodes)
- Use cache.m5.large for production
- Enable encryption in transit

### RDS Module

Creates:
- PostgreSQL instance
- Subnet group
- Security group
- Parameter group with connection pooling settings
- Automated backups (7-day retention)

**Cost:** ~$15/month (db.t3.micro)

**Production Recommendations:**
- Use db.m5.large or larger
- Enable Multi-AZ for high availability
- Increase backup retention to 30 days

### ECS Module

Creates:
- ECS Fargate cluster
- Task definitions for 3 services
- IAM roles and policies
- Service discovery (Cloud Map)
- Auto-scaling policies

**Cost:** ~$30/month (3 services @ 0.25 vCPU, 0.5 GB)

**Production Recommendations:**
- Increase CPU/memory: 0.5 vCPU, 1 GB minimum
- Enable auto-scaling (min: 2, max: 10)
- Use application-level health checks

### ALB Module

Creates:
- Application Load Balancer
- Target groups for each service
- Listener rules (path-based routing)
- Security group

**Cost:** ~$20/month (ALB + data transfer)

**Path Routing:**
- `/api/*` → API Gateway
- `/actuator/*` → Service actuator endpoints (internal only)

## Connecting Services

After deployment, update your `.env` file:

```bash
# Get outputs
KAFKA_SERVERS=$(terraform output -raw msk_bootstrap_servers)
REDIS_HOST=$(terraform output -raw redis_endpoint)
RDS_HOST=$(terraform output -raw rds_endpoint)

# Update .env
cat > .env << EOF
KAFKA_BOOTSTRAP_SERVERS=$KAFKA_SERVERS
REDIS_HOST=$REDIS_HOST
POSTGRES_HOST=$RDS_HOST
# ... other variables
EOF
```

## Deployment to ECS

### Option 1: GitHub Actions (Recommended)

```yaml
# .github/workflows/deploy.yml
- name: Deploy to ECS
  run: |
    aws ecs update-service \
      --cluster routeforge-cluster \
      --service api-gateway-service \
      --force-new-deployment
```

### Option 2: Manual Deployment

```bash
# Build and push Docker images
./gradlew bootBuildImage

docker tag routeforge/api-gateway:latest \
  $ECR_REPO/api-gateway:latest

aws ecr get-login-password | docker login --username AWS --password-stdin $ECR_REPO

docker push $ECR_REPO/api-gateway:latest

# Update ECS service
aws ecs update-service \
  --cluster routeforge-cluster \
  --service api-gateway-service \
  --force-new-deployment
```

## Monitoring

After deployment:

1. **CloudWatch Logs**: `/aws/ecs/routeforge/*`
2. **CloudWatch Metrics**: ECS, MSK, RDS, ElastiCache dashboards
3. **Prometheus**: Point to `http://<ALB-DNS>/actuator/prometheus`

## Security Best Practices

1. **Secrets Management**:
   - Store database credentials in AWS Secrets Manager
   - Reference secrets in ECS task definitions
   - Rotate credentials regularly

2. **Network Security**:
   - Services in private subnets only
   - ALB in public subnets
   - Security groups restrict access by service

3. **Encryption**:
   - Enable encryption at rest for RDS
   - Enable encryption in transit for MSK
   - Use HTTPS/TLS for all external communication

4. **IAM**:
   - Use least-privilege IAM roles
   - Enable MFA for console access
   - Audit IAM policies regularly

## Scaling

### Horizontal Scaling

```bash
# Scale ECS services
aws ecs update-service \
  --cluster routeforge-cluster \
  --service processing-service \
  --desired-count 5
```

### Vertical Scaling

Update `terraform.tfvars`:

```hcl
ecs_task_cpu    = "512"  # 0.5 vCPU → more processing power
ecs_task_memory = "1024" # 1 GB → more memory
```

Then apply:

```bash
terraform apply
```

## Disaster Recovery

### Backup Strategy

1. **RDS**: Automated daily backups (7-day retention)
2. **MSK**: No backups (ephemeral event stream)
3. **Redis**: No backups (cache can be rebuilt)

### Recovery Steps

```bash
# Restore RDS from snapshot
aws rds restore-db-instance-from-db-snapshot \
  --db-instance-identifier routeforge-restored \
  --db-snapshot-identifier <snapshot-id>

# Rebuild cache by replaying Kafka (if available)
# Or let cache warm up naturally
```

## Cost Optimization

### Development Environment

Use smaller instance types:

```hcl
db_instance_class   = "db.t3.micro"
redis_node_type     = "cache.t3.micro"
kafka_instance_type = "kafka.t3.small"
ecs_task_cpu        = "256"
ecs_task_memory     = "512"
```

**Estimated cost: ~$200/month**

### Stop Services Outside Business Hours

```bash
# Stop ECS services
aws ecs update-service \
  --cluster routeforge-cluster \
  --service api-gateway-service \
  --desired-count 0

# Resume services
aws ecs update-service \
  --cluster routeforge-cluster \
  --service api-gateway-service \
  --desired-count 2
```

**Savings: ~$20/day when stopped**

## Teardown

### Option 1: Terraform Destroy

```bash
# Review what will be destroyed
terraform plan -destroy

# Destroy all resources
terraform destroy
```

⚠️ **Warning**: This deletes all data. Backup RDS first if needed.

### Option 2: Safe Teardown Script

```bash
./scripts/destroy-safely.sh
```

This script:
1. Takes final RDS snapshot
2. Drains ECS services
3. Destroys resources in correct order
4. Preserves S3 logs

## Troubleshooting

### MSK Cluster Stuck Creating

- **Cause**: Insufficient subnet configuration
- **Fix**: Ensure subnets in 2+ AZs

### ECS Tasks Failing to Start

- **Cause**: IAM permissions or image pull errors
- **Fix**: Check CloudWatch logs, verify ECR permissions

### High Costs

- **Cause**: NAT Gateway data transfer
- **Fix**: Use VPC endpoints for AWS services

### Database Connection Failures

- **Cause**: Security group rules
- **Fix**: Verify ECS security group has ingress to RDS port 5432

## Support

- **Terraform Registry**: Module documentation
- **AWS Support**: Enterprise support recommended for production
- **GitHub Issues**: Report infrastructure bugs

## License

Apache 2.0 - See LICENSE file
