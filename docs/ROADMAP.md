# RouteForge Roadmap & Future Enhancements

This document outlines planned features, known limitations, and recommended improvements for RouteForge.

## Current Status: Production-Ready MVP ✅

RouteForge currently provides:
- ✅ Real-time GTFS-RT vehicle position ingestion
- ✅ Kafka-based event streaming
- ✅ Redis hot cache + PostgreSQL historical storage
- ✅ RESTful APIs with rate limiting
- ✅ Server-Sent Events (SSE) for real-time streaming
- ✅ JWT-based authentication and authorization
- ✅ Admin operations (cache management, DLQ monitoring, replay)
- ✅ Observability (Prometheus, Grafana)
- ✅ CI/CD with GitHub Actions
- ✅ AWS Infrastructure as Code (Terraform)

---

## Phase 8: GTFS Static Data Integration 🚀

**Priority:** High  
**Effort:** Medium (2-3 days)  
**Impact:** Significantly improves ETA accuracy

### Motivation

Current ETA calculations use:
- Haversine distance from vehicle to stop
- Average historical speed from PostgreSQL
- Current vehicle speed (if available)

**Limitations:**
- Stop locations are not stored (hardcoded in tests)
- No scheduled arrival times
- No route geometry (vehicles travel on roads, not straight lines)
- Cannot determine which direction a vehicle is heading

### Proposed Implementation

#### 1. Database Schema Extensions

```sql
-- GTFS Static Tables
CREATE TABLE gtfs_stops (
    stop_id VARCHAR(50) PRIMARY KEY,
    stop_name VARCHAR(255) NOT NULL,
    stop_lat DOUBLE PRECISION NOT NULL,
    stop_lon DOUBLE PRECISION NOT NULL,
    stop_code VARCHAR(50),
    location_type INT DEFAULT 0,
    parent_station VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE gtfs_routes (
    route_id VARCHAR(50) PRIMARY KEY,
    route_short_name VARCHAR(50),
    route_long_name VARCHAR(255),
    route_type INT NOT NULL,
    route_color VARCHAR(6),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE gtfs_trips (
    trip_id VARCHAR(100) PRIMARY KEY,
    route_id VARCHAR(50) NOT NULL REFERENCES gtfs_routes(route_id),
    service_id VARCHAR(50) NOT NULL,
    trip_headsign VARCHAR(255),
    direction_id INT,
    shape_id VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE gtfs_stop_times (
    trip_id VARCHAR(100) NOT NULL REFERENCES gtfs_trips(trip_id),
    arrival_time TIME NOT NULL,
    departure_time TIME NOT NULL,
    stop_id VARCHAR(50) NOT NULL REFERENCES gtfs_stops(stop_id),
    stop_sequence INT NOT NULL,
    pickup_type INT DEFAULT 0,
    drop_off_type INT DEFAULT 0,
    PRIMARY KEY (trip_id, stop_sequence)
);

CREATE INDEX idx_stop_times_stop_id ON gtfs_stop_times(stop_id);
CREATE INDEX idx_stop_times_trip_id ON gtfs_stop_times(trip_id);
```

#### 2. GTFS Static Loader Service

Create a new service or scheduled job to:
1. Download GTFS static feed (ZIP file)
2. Parse CSV files (stops.txt, routes.txt, trips.txt, stop_times.txt)
3. Load into PostgreSQL tables
4. Run on startup and refresh daily

**New Classes:**
- `GtfsStaticLoaderService.java` - Download and parse GTFS static
- `GtfsStaticRepository.java` - JPA repositories for GTFS tables
- `GtfsStopEntity.java`, `GtfsRouteEntity.java`, etc. - JPA entities

#### 3. Enhanced ETA Calculation

Update `EtaCalculationService` to:
- Join vehicle positions with `gtfs_trips` to determine trip and direction
- Look up `gtfs_stop_times` for scheduled arrival at the target stop
- Calculate delay: `predicted_arrival - scheduled_arrival`
- Use route geometry (shapes.txt) for accurate distance calculation
- Consider traffic patterns and historical delays

**Improved Algorithm:**
```java
// 1. Find vehicle's current trip and direction
String tripId = getTripIdFromVehicle(vehicleId);

// 2. Get scheduled arrival at target stop
LocalTime scheduledArrival = getScheduledArrival(tripId, stopId);

// 3. Calculate distance along route (not straight line)
double distanceAlongRoute = calculateDistanceAlongRoute(vehiclePosition, stopId, tripId);

// 4. Predict arrival using historical data + current delay
int currentDelay = getCurrentDelay(vehicleId);
LocalTime predictedArrival = scheduledArrival.plusSeconds(currentDelay);

// 5. Calculate confidence based on sample size and variance
double confidence = calculateConfidence(tripId, stopId, timeOfDay);
```

#### 4. API Changes

Update ETA response to include:
```json
{
  "vehicleId": "V123",
  "tripId": "TRIP_456",
  "tripHeadsign": "Manhattan",
  "stopId": "STOP_789",
  "stopName": "Times Square - 42 St",
  "scheduledArrival": "2024-01-01T12:15:00Z",
  "predictedArrival": "2024-01-01T12:17:30Z",
  "delaySeconds": 150,
  "confidence": 0.92
}
```

### Testing Plan

1. **Unit Tests:** CSV parsing, database loading
2. **Integration Tests:** Full GTFS static load with Testcontainers
3. **Accuracy Tests:** Compare predictions vs. actual arrivals
4. **Performance Tests:** Query optimization with large datasets

### Resources

- **GTFS Static Specification:** https://gtfs.org/schedule/reference/
- **NYC MTA GTFS Static:** http://web.mta.info/developers/developer-data-terms.html
- **GTFS Parser Library:** Consider `onebusaway-gtfs` or custom implementation

---

## Phase 9: Secrets Management for Production 🔒

**Priority:** Critical for AWS deployment  
**Effort:** Low (1 day)  
**Impact:** Essential security requirement

### Current State

Secrets are stored in:
- `.env` files (local development) ✅ OK for dev
- `docker-compose.yml` environment variables ✅ OK for dev
- Terraform `variables.tf` (AWS) ⚠️ Not secure for production

**Example of insecure configuration:**
```hcl
variable "postgres_password" {
  default = "routeforge123"  # ❌ Hardcoded
}
```

### Proposed Solution: AWS Secrets Manager

#### 1. Create Secrets in AWS

```bash
# Create database password
aws secretsmanager create-secret \
  --name /routeforge/prod/postgres-password \
  --secret-string "$(openssl rand -base64 32)"

# Create Keycloak client secret
aws secretsmanager create-secret \
  --name /routeforge/prod/keycloak-client-secret \
  --secret-string "$(openssl rand -base64 32)"

# Create Redis password
aws secretsmanager create-secret \
  --name /routeforge/prod/redis-password \
  --secret-string "$(openssl rand -base64 32)"
```

#### 2. Update Terraform to Reference Secrets

```hcl
# infra/terraform/secrets.tf
data "aws_secretsmanager_secret" "postgres_password" {
  name = "/routeforge/prod/postgres-password"
}

data "aws_secretsmanager_secret_version" "postgres_password" {
  secret_id = data.aws_secretsmanager_secret.postgres_password.id
}

# RDS module
module "rds" {
  source = "./modules/rds"
  
  master_password = data.aws_secretsmanager_secret_version.postgres_password.secret_string
  # ... other config
}
```

#### 3. ECS Task Definitions with Secrets

```hcl
resource "aws_ecs_task_definition" "api_gateway" {
  family = "routeforge-api-gateway"
  
  container_definitions = jsonencode([{
    name  = "api-gateway"
    image = "${aws_ecr_repository.api_gateway.repository_url}:latest"
    
    secrets = [
      {
        name      = "POSTGRES_PASSWORD"
        valueFrom = data.aws_secretsmanager_secret.postgres_password.arn
      },
      {
        name      = "KEYCLOAK_CLIENT_SECRET"
        valueFrom = data.aws_secretsmanager_secret.keycloak_client_secret.arn
      }
    ]
    
    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "prod" }
    ]
  }])
}
```

#### 4. IAM Permissions

```hcl
resource "aws_iam_role_policy" "ecs_secrets_access" {
  name = "routeforge-ecs-secrets-access"
  role = aws_iam_role.ecs_task_execution_role.id
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue"
        ]
        Resource = [
          data.aws_secretsmanager_secret.postgres_password.arn,
          data.aws_secretsmanager_secret.keycloak_client_secret.arn,
          data.aws_secretsmanager_secret.redis_password.arn
        ]
      }
    ]
  })
}
```

#### 5. Rotation Strategy

Enable automatic secret rotation:
```bash
aws secretsmanager rotate-secret \
  --secret-id /routeforge/prod/postgres-password \
  --rotation-lambda-arn arn:aws:lambda:us-east-1:123456789012:function:SecretsManagerRotation \
  --rotation-rules AutomaticallyAfterDays=30
```

### Alternative: AWS Systems Manager Parameter Store

For non-sensitive configuration:
```bash
aws ssm put-parameter \
  --name /routeforge/prod/kafka-bootstrap-servers \
  --value "kafka.prod.routeforge.internal:9092" \
  --type String
```

**Comparison:**

| Feature | Secrets Manager | Parameter Store |
|---------|----------------|-----------------|
| Automatic Rotation | ✅ Yes | ❌ No |
| Encryption | ✅ Always | ✅ Optional |
| Cost | ~$0.40/secret/month | Free (standard) |
| Use Case | Passwords, keys | Config values |

---

## Phase 10: Additional Enhancements 📈

### A. Performance Optimizations

1. **Redis Cluster Mode**
   - Current: Single Redis instance
   - Proposed: Redis cluster with sharding
   - Benefit: Handle 10x more cache requests

2. **Database Connection Pooling Tuning**
   ```yaml
   spring:
     datasource:
       hikari:
         maximum-pool-size: 20  # Current: 10
         minimum-idle: 10       # Current: 5
         connection-timeout: 20000
   ```

3. **Kafka Consumer Parallelism**
   ```yaml
   spring:
     kafka:
       listener:
         concurrency: 10  # Current: 3
   ```

### B. Monitoring & Alerting

1. **CloudWatch Integration**
   - Ship logs to CloudWatch Logs
   - Custom metrics for DLQ depth, cache hit rate
   - Alarms for critical thresholds

2. **Distributed Tracing**
   - Add Spring Cloud Sleuth + Zipkin
   - Trace requests across microservices
   - Identify bottlenecks

3. **Custom Grafana Dashboards**
   - DLQ metrics over time
   - Cache hit/miss rates
   - SSE connection health
   - ETA prediction accuracy

### C. API Enhancements

1. **GraphQL API**
   - Single query for vehicle + route + stops + ETA
   - Reduce number of API calls
   - Better for mobile clients

2. **WebSocket Support**
   - Bidirectional communication
   - Subscribe to multiple routes in one connection
   - Lower latency than SSE

3. **Batch APIs**
   ```
   POST /api/vehicles/batch
   {
     "vehicleIds": ["V1", "V2", "V3"]
   }
   ```

### D. Testing & Quality

1. **Contract Testing**
   - Use Spring Cloud Contract
   - Ensure API compatibility across versions

2. **Chaos Engineering**
   - Simulate Kafka failures
   - Test Redis failover
   - Verify circuit breakers

3. **Load Testing**
   ```bash
   # JMeter or Gatling
   - 1000 concurrent SSE connections
   - 10,000 req/s to REST API
   - Kafka throughput: 100,000 msg/s
   ```

### E. DevOps Improvements

1. **Blue-Green Deployments**
   - Zero-downtime deployments
   - Instant rollback capability

2. **Canary Releases**
   - Deploy to 5% of traffic first
   - Monitor error rates
   - Gradual rollout

3. **Auto-Scaling Policies**
   ```hcl
   resource "aws_appautoscaling_policy" "ecs_policy" {
     policy_type = "TargetTrackingScaling"
     
     target_tracking_scaling_policy_configuration {
       predefined_metric_specification {
         predefined_metric_type = "ECSServiceAverageCPUUtilization"
       }
       target_value = 70.0
     }
   }
   ```

---

## Timeline Estimate

| Phase | Description | Effort | Priority |
|-------|-------------|--------|----------|
| 8 | GTFS Static Integration | 2-3 days | High |
| 9 | Secrets Management | 1 day | Critical |
| 10A | Performance Tuning | 2 days | Medium |
| 10B | Enhanced Monitoring | 3 days | Medium |
| 10C | API Enhancements | 5 days | Low |
| 10D | Advanced Testing | 3 days | Medium |
| 10E | DevOps Improvements | 3 days | Medium |

**Total:** ~3 weeks for all enhancements

---

## Deployment Checklist for Production

Before deploying to AWS:

- [ ] Migrate all secrets to AWS Secrets Manager
- [ ] Configure CloudWatch alarms
- [ ] Set up RDS automated backups (retention: 7 days)
- [ ] Enable MSK encryption at rest and in transit
- [ ] Configure VPC with private subnets for databases
- [ ] Set up bastion host for secure access
- [ ] Enable AWS WAF on ALB
- [ ] Configure Route 53 for custom domain
- [ ] Set up SSL/TLS certificates (ACM)
- [ ] Configure log aggregation and retention policies
- [ ] Load test with production-like data
- [ ] Document runbook procedures
- [ ] Train team on incident response

---

## Conclusion

RouteForge is currently a **production-ready MVP** suitable for demonstrating distributed systems expertise. The roadmap above provides a clear path to evolving it into a **production-hardened, enterprise-grade system**.

**Recommended Priority:**
1. **Phase 9 (Secrets Management)** - Required for production security
2. **Phase 8 (GTFS Static)** - Significantly improves accuracy and user experience
3. **Phase 10B (Monitoring)** - Essential for production operations

These enhancements will transform RouteForge from an impressive portfolio project into a system ready for real-world deployment at scale.
