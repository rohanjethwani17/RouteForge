# RouteForge Operations Runbook

This guide covers common operational tasks and troubleshooting.

## Starting the System

### Full Stack Startup

```bash
# 1. Start infrastructure
docker compose up -d

# 2. Wait for health checks
docker compose ps  # All services should show "healthy"

# 3. Build services
./gradlew build

# 4. Run services (in separate terminals)
./gradlew :ingestion-service:bootRun
./gradlew :processing-service:bootRun
./gradlew :api-gateway-service:bootRun
```

### Startup Verification

```bash
# Check all services are up
curl http://localhost:8082/actuator/health  # API Gateway
curl http://localhost:8083/actuator/health  # Ingestion
curl http://localhost:8084/actuator/health  # Processing

# Check infrastructure
docker compose ps

# Check Kafka topics
docker exec routeforge-kafka kafka-topics --bootstrap-server localhost:9092 --list
```

---

## Monitoring

### Check Service Health

```bash
# Actuator health endpoints
curl http://localhost:8083/actuator/health | jq
curl http://localhost:8084/actuator/health | jq
curl http://localhost:8082/actuator/health | jq
```

Healthy response:
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "diskSpace": {"status": "UP"},
    "ping": {"status": "UP"}
  }
}
```

### Check Metrics

```bash
# View all metrics
curl http://localhost:8082/actuator/metrics

# View specific metric
curl http://localhost:8082/actuator/metrics/routeforge.processing.events.processed
```

### Grafana Dashboards

1. Open http://localhost:3000
2. Login: admin/admin123
3. Import dashboard from `infra/grafana/dashboards/routeforge-dashboard.json`

---

## Common Alerts

### Alert: High Consumer Lag

**Symptom:** `kafka_consumer_records_lag` > 1000

**Impact:** Processing delays, stale cache data

**Investigation:**
```bash
# Check consumer lag
docker exec routeforge-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe \
  --group processing-service-group

# Check processing service logs
docker compose logs processing-service --tail 100
```

**Resolution:**
1. Check if processing service is running
2. Verify database connectivity
3. Check for exceptions in logs
4. Consider scaling processing service

---

### Alert: High API Latency

**Symptom:** `http_server_requests_seconds{quantile="0.95"}` > 0.5

**Impact:** Slow API responses

**Investigation:**
```bash
# Check Redis connectivity
docker exec routeforge-redis redis-cli ping

# Check cache hit rate
curl http://localhost:8082/actuator/metrics/cache.gets | jq

# Check database connections
docker exec routeforge-postgres pg_stat_activity
```

**Resolution:**
1. Verify Redis is healthy
2. Check if cache is being populated
3. Increase Redis memory if evictions are high
4. Review slow queries in PostgreSQL

---

### Alert: Feed Ingestion Failures

**Symptom:** `routeforge_ingestion_events_published` flat or decreasing

**Impact:** No new data

**Investigation:**
```bash
# Check ingestion service logs
docker compose logs ingestion-service --tail 50

# Test feed URL manually
curl -I "$GTFS_RT_FEED_URL"
```

**Resolution:**
1. Verify feed URL is accessible
2. Check circuit breaker state
3. Verify API keys if required
4. Restart ingestion service

---

### Alert: DLQ Messages Accumulating

**Symptom:** Messages in `vehicle_positions.dlq` topic

**Impact:** Data loss for failed events

**Investigation:**
```bash
# Check DLQ messages
docker exec routeforge-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic vehicle_positions.dlq \
  --from-beginning \
  --max-messages 10

# Check processing service logs for errors
docker compose logs processing-service | grep ERROR
```

**Resolution:**
1. Identify error pattern from logs
2. Fix root cause (DB connection, Redis, validation)
3. Replay DLQ messages if needed

---

## Kafka Operations

### List Topics

```bash
docker exec routeforge-kafka kafka-topics \
  --bootstrap-server localhost:9092 \
  --list
```

### Describe Topic

```bash
docker exec routeforge-kafka kafka-topics \
  --bootstrap-server localhost:9092 \
  --describe \
  --topic vehicle_positions
```

### View Messages

```bash
# From beginning
docker exec routeforge-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic vehicle_positions \
  --from-beginning \
  --max-messages 10

# Latest messages
docker exec routeforge-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic vehicle_positions \
  --max-messages 10
```

### Reset Consumer Group (DANGER)

```bash
# This will reprocess all messages
docker exec routeforge-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group processing-service-group \
  --reset-offsets \
  --to-earliest \
  --execute \
  --all-topics
```

---

## Redis Operations

### Connect to Redis

```bash
docker exec -it routeforge-redis redis-cli
```

### Check Keys

```bash
# All keys
KEYS *

# Vehicle keys
KEYS veh:*

# Route keys
KEYS route:*
```

### View Vehicle Data

```bash
# Get vehicle hash
HGETALL veh:VEHICLE_123

# Get vehicles on route
ZRANGE route:1:vehicles 0 -1 WITHSCORES
```

### Clear Cache

```bash
# Clear all keys (DANGER)
FLUSHALL

# Delete specific pattern
redis-cli --scan --pattern 'veh:*' | xargs redis-cli DEL
```

---

## Database Operations

### Connect to PostgreSQL

```bash
docker exec -it routeforge-postgres psql -U routeforge -d routeforge
```

### Common Queries

```sql
-- Check recent vehicle positions
SELECT vehicle_id, route_id, lat, lon, recorded_at
FROM vehicle_positions_history
ORDER BY recorded_at DESC
LIMIT 10;

-- Count positions by route
SELECT route_id, COUNT(*)
FROM vehicle_positions_history
WHERE recorded_at > NOW() - INTERVAL '1 hour'
GROUP BY route_id
ORDER BY COUNT(*) DESC;

-- Check for duplicates
SELECT event_id, COUNT(*)
FROM vehicle_positions_history
GROUP BY event_id
HAVING COUNT(*) > 1;
```

### Database Maintenance

```sql
-- Vacuum to reclaim space
VACUUM ANALYZE vehicle_positions_history;

-- Check table size
SELECT pg_size_pretty(pg_total_relation_size('vehicle_positions_history'));

-- Create missing indexes (if needed)
CREATE INDEX CONCURRENTLY idx_missing ON vehicle_positions_history(column_name);
```

---

## Backup and Restore

### Database Backup

```bash
# Full backup
docker exec routeforge-postgres pg_dump -U routeforge routeforge > backup.sql

# Schema only
docker exec routeforge-postgres pg_dump -U routeforge --schema-only routeforge > schema.sql
```

### Database Restore

```bash
# Restore from backup
cat backup.sql | docker exec -i routeforge-postgres psql -U routeforge routeforge
```

---

## Scaling

### Scale Processing Service

```bash
# Run multiple instances
./gradlew :processing-service:bootRun &  # Instance 1
./gradlew :processing-service:bootRun &  # Instance 2
./gradlew :processing-service:bootRun &  # Instance 3
```

Kafka will automatically distribute partitions across instances.

### Scale API Gateway

```bash
# Run on different ports
API_PORT=8091 ./gradlew :api-gateway-service:bootRun &
API_PORT=8092 ./gradlew :api-gateway-service:bootRun &
```

Add load balancer (nginx, ALB) in front.

---

## Shutdown

### Graceful Shutdown

```bash
# Stop services (Ctrl+C in each terminal)

# Stop infrastructure
docker compose down
```

### Force Stop

```bash
# Kill all Java processes
pkill -9 java

# Stop and remove containers
docker compose down -v  # -v removes volumes
```

---

## Log Analysis

### View Logs

```bash
# All infrastructure logs
docker compose logs -f

# Specific service
docker compose logs -f kafka
docker compose logs -f redis
docker compose logs -f postgres

# Service logs (if running via bootRun)
tail -f logs/ingestion-service.log
```

### Search Logs

```bash
# Search for errors
docker compose logs | grep ERROR

# Search by traceId
docker compose logs | grep "traceId=abc123"
```

---

## Performance Tuning

### JVM Tuning

Add to service startup:
```bash
export JAVA_OPTS="-Xmx2g -Xms2g -XX:+UseG1GC"
./gradlew :processing-service:bootRun
```

### Kafka Tuning

Edit `docker-compose.yml`:
```yaml
kafka:
  environment:
    KAFKA_NUM_PARTITIONS: 6  # Increase partitions
    KAFKA_LOG_RETENTION_HOURS: 24  # Reduce retention
```

### PostgreSQL Tuning

Edit `docker-compose.yml`:
```yaml
postgres:
  command:
    - postgres
    - -c
    - max_connections=100
    - -c
    - shared_buffers=256MB
```
