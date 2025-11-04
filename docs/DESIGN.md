# RouteForge System Design

## Overview

RouteForge is a distributed system for real-time public transit tracking that demonstrates production-ready patterns for:

- Event-driven architecture with Apache Kafka
- Dual-write pattern (cache + database)
- Low-latency APIs with Redis caching
- OAuth2/JWT security
- Full observability (metrics, logs, traces)

## Architecture

### Data Flow

```
┌─────────────┐     ┌─────────────┐     ┌────────┐
│  GTFS-RT    │────>│  Ingestion  │────>│ Kafka  │
│    Feed     │     │   Service   │     │        │
└─────────────┘     └─────────────┘     └────────┘
                                             │
                                             v
                    ┌─────────────┐     ┌────────┐
                    │ Processing  │<────│ Kafka  │
                    │   Service   │     │        │
                    └─────────────┘     └────────┘
                           │
                    ┌──────┴──────┐
                    v             v
               ┌────────┐   ┌──────────┐
               │ Redis  │   │Postgres  │
               │ (hot)  │   │(history) │
               └────────┘   └──────────┘
                    │             │
                    └──────┬──────┘
                           v
                    ┌─────────────┐
                    │ API Gateway │
                    │  Service    │
                    └─────────────┘
                           │
                    ┌──────┴──────┐
                    v             v
               ┌────────┐   ┌──────────┐
               │  REST  │   │WebSocket │
               │  API   │   │   /SSE   │
               └────────┘   └──────────┘
```

### Data Flow with Real-Time Streaming

```
GTFS-RT → Ingestion → Kafka → Processing → Redis + DB
                                              ↓
                                        Redis Pub/Sub
                                              ↓
                                         API Gateway → SSE → Clients
```

### Service Responsibilities

#### Ingestion Service
- Polls GTFS-Realtime Vehicle Positions feed every 5 seconds
- Parses protobuf format to JSON events
- Publishes to Kafka topic `vehicle_positions`
- Circuit breaker and retry for feed failures
- Metrics: events published, fetch failures

#### Processing Service
- Kafka consumer with manual offset management
- Batch processing (50 events per batch)
- Dual-write: Redis for hot state, PostgreSQL for history
- Redis Pub/Sub publisher for real-time notifications
- Out-of-order detection using timestamps
- Dead-letter queue for failed events
- Metrics: events processed, cache updates, DB inserts, DLQ count, Pub/Sub notifications

#### API Gateway Service
- REST endpoints for vehicle queries
- Server-Sent Events (SSE) for real-time streaming
- Redis Pub/Sub subscriber for update notifications
- Redis-first read pattern (fallback to DB)
- OAuth2 Resource Server (JWT)
- Rate limiting (Bucket4j)
- OpenAPI documentation
- Metrics: request latency, throughput, cache hit/miss, active SSE connections

## Data Models

### Event Schema (Kafka)

```json
{
  "eventId": "1234567890:VEHICLE_123",
  "vehicleId": "VEHICLE_123",
  "routeId": "1",
  "lat": 40.7128,
  "lon": -74.0060,
  "speedKph": 25.5,
  "headingDeg": 90.0,
  "tsEpochMs": 1704067200000,
  "stopId": "STOP_456",
  "delaySec": 120
}
```

### Redis Keys

```
veh:{vehicleId} -> Hash
  fields: vehicleId, routeId, lat, lon, speedKph, headingDeg, tsEpochMs, stopId, delaySec
  TTL: 300 seconds

route:{routeId}:vehicles -> Sorted Set
  score: tsEpochMs
  member: vehicleId
  TTL: 300 seconds
```

### PostgreSQL Schema

```sql
CREATE TABLE vehicle_positions_history (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL UNIQUE,
    vehicle_id VARCHAR(50) NOT NULL,
    route_id VARCHAR(50) NOT NULL,
    lat DOUBLE PRECISION NOT NULL,
    lon DOUBLE PRECISION NOT NULL,
    speed_kph DOUBLE PRECISION,
    heading_deg DOUBLE PRECISION,
    ts_epoch_ms BIGINT NOT NULL,
    stop_id VARCHAR(50),
    delay_sec INTEGER,
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_vehicle_ts ON vehicle_positions_history(vehicle_id, ts_epoch_ms DESC);
CREATE INDEX idx_route_ts ON vehicle_positions_history(route_id, ts_epoch_ms DESC);
```

## Consistency Model

### Eventual Consistency

- Redis and PostgreSQL are updated independently
- Redis may be slightly ahead or behind PostgreSQL
- Accepted tradeoff for low-latency reads

### Conflict Resolution

- Timestamps used for ordering
- Out-of-order events (older than last seen) are skipped
- Idempotency via unique `eventId`

## Partitioning Strategy

### Kafka Partitioning

- Key: `vehicleId`
- Ensures all events for a vehicle go to same partition
- Maintains ordering per vehicle
- Allows parallel processing across vehicles

### Redis Sharding (Future)

- Can shard by route or geographic region
- Use Redis Cluster for horizontal scaling

### PostgreSQL Partitioning

- Partition by day/week on `recorded_at` column
- Automatic retention policy (drop old partitions)
- Optimized for time-range queries

## Failure Modes

### GTFS-RT Feed Unavailable
- Circuit breaker opens after 50% failure rate
- Retries with exponential backoff
- Metrics alert on consecutive failures
- Impact: No new data, stale cache

### Kafka Unavailable
- Ingestion service blocks until connection restored
- Processing service consumer pauses
- No data loss (feed will be re-polled)

### Redis Unavailable
- API falls back to PostgreSQL
- Higher latency but still functional
- Metrics alert on cache miss rate spike

### PostgreSQL Unavailable
- Processing service sends to DLQ
- Redis cache still updated
- Recent data available via API
- Historical queries fail

## Real-Time Streaming Architecture

### Server-Sent Events (SSE)

RouteForge uses SSE for unidirectional real-time updates from server to clients. SSE was chosen over WebSocket for:
- Simpler implementation (HTTP-based, no protocol upgrade)
- Better for read-only updates (vehicle positions)
- Automatic reconnection in browsers
- Works through HTTP proxies and firewalls
- Lower overhead than WebSocket for our use case

### Fan-Out Pattern

```
Processing Service                  API Gateway              Clients
      ↓                                   ↓                    ↓
Redis+DB Write                    Redis Subscriber      SSE Emitters
      ↓                                   ↓                    ↓
Redis Pub/Sub                     Vehicle Fetch         Event Stream
 (notify)                          (from Redis)          (JSON events)

Timeline:
1. Vehicle position updated in Redis/DB
2. Notification published: route:{routeId}:updates
3. API Gateway subscribers receive notification
4. Fresh vehicle data fetched from Redis
5. Data sent to all SSE clients subscribed to that route
```

### Connection Management

- **Timeout**: 30 minutes of inactivity
- **Heartbeat**: Every 15 seconds to detect dead connections
- **Backpressure**: Buffer limited; drops oldest events on overflow
- **Lifecycle**: Auto-cleanup on timeout/error/completion
- **Metrics**: Active connections, messages sent/failed

### Scalability

**Single Instance**: Redis Pub/Sub with in-memory emitter management

**Multi-Instance**: 
- Option 1: Sticky sessions (route clients to same instance)
- Option 2: Shared state via Redis (store emitter metadata)
- Current: Single-instance suitable for MVP; sticky sessions for scale

## Observability

### Metrics (Prometheus)

```
# Business metrics
routeforge_ingestion_events_published{}
routeforge_processing_events_processed{}
routeforge_processing_cache_updates{}
routeforge_processing_db_inserts{}
routeforge_processing_pubsub_published{}

# SSE metrics
routeforge_sse_emitters_created{}
routeforge_sse_emitters_removed{}
routeforge_sse_messages_sent{}
routeforge_sse_messages_failed{}
routeforge_sse_active_connections{}

# Infrastructure metrics
http_server_requests_seconds{quantile="0.95"}
kafka_consumer_records_lag{}
redis_commands_processed_total{}
jvm_memory_used_bytes{}
```

### Logging

- Structured JSON logs (logstash-logback-encoder)
- Correlation IDs (traceId/spanId in MDC)
- Log levels: DEBUG for processing logic, INFO for lifecycle, ERROR for failures

### Tracing (Optional)

- OpenTelemetry integration ready
- Trace end-to-end: Feed → Kafka → Processing → API

## Scaling Considerations

### Horizontal Scaling

**Ingestion Service:**
- Single instance sufficient (one feed poller)
- Can partition feeds by line/route if scaling needed

**Processing Service:**
- Scale to N instances (same consumer group)
- Kafka partitions distribute load automatically
- Optimal: 1 instance per Kafka partition

**API Gateway:**
- Stateless, can scale to N instances
- Load balancer in front (ALB on AWS)
- WebSocket sessions sticky to instance

### Vertical Scaling

- Increase JVM heap for high throughput
- More Kafka partitions for parallelism
- Redis memory for larger cache
- PostgreSQL connection pool tuning

## Future Enhancements

1. **ETA Calculation** - Use historical data + current position
2. **Geofencing** - Alerts when vehicles enter/exit zones
3. **Route Optimization** - Suggest faster routes based on real-time data
4. **Multi-Agency Support** - Ingest from multiple GTFS-RT feeds
5. **Mobile SDK** - Native iOS/Android libraries
6. **GraphQL API** - Alternative to REST for complex queries
