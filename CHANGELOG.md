# RouteForge Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added - Phase 1: Real-Time Streaming with SSE

#### Server-Sent Events (SSE) Implementation
- **New Endpoint**: `GET /api/stream/routes/{routeId}`
  - Opens persistent SSE connection for real-time vehicle updates
  - 30-minute timeout with automatic cleanup
  - Heartbeat every 15 seconds to keep connections alive
  - Event types: `connected`, `vehicle-update`, `heartbeat`

#### Redis Pub/Sub Fan-Out
- **Processing Service**: Publishes update notifications after successful Redis/DB write
  - Channel pattern: `route:{routeId}:updates`
  - Notification payload: `{routeId, vehicleId, updatedAt}`
  - New metric: `routeforge.processing.pubsub.published`

- **API Gateway**: Subscribes to route update channels
  - Pattern subscription: `route:*:updates`
  - Fetches fresh vehicle data from Redis on notification
  - Fans out to all SSE clients subscribed to that route

#### SSE Connection Management
- `SseEmitterManager`: Thread-safe management of active connections
- `RedisSubscriberService`: Background Pub/Sub subscriber
- `SseHeartbeatScheduler`: Scheduled heartbeat to detect dead connections
- `VehicleStreamController`: REST endpoint for SSE streaming

#### New Metrics
- `routeforge.sse.emitters.created` - Total SSE emitters created
- `routeforge.sse.emitters.removed` - Total SSE emitters removed
- `routeforge.sse.messages.sent` - Total messages sent via SSE
- `routeforge.sse.messages.failed` - Failed SSE message attempts
- `routeforge.sse.active.connections` - Current active connections (gauge)

#### Documentation Updates
- Added SSE endpoint to API.md with JavaScript client example
- Updated DESIGN.md with real-time streaming architecture
- Documented fan-out pattern and connection lifecycle

#### Security
- SSE endpoint `/api/stream/**` open in dev profile
- Rate limiting applies to SSE connections
- OAuth2/JWT protection available in prod profile

### Changed - Phase 0: Config Hygiene & Port Alignment

#### Service Port Standardization
- **API Gateway**: Now runs on port `8082` (changed from 8081)
  - Removed separate management port
  - Actuator endpoints available at `http://localhost:8082/actuator/*`
  - Swagger UI at `http://localhost:8082/swagger-ui.html`
  
- **Ingestion Service**: Now runs on port `8083`
  - Added explicit `server.port` configuration
  - Actuator metrics at `http://localhost:8083/actuator/prometheus`
  
- **Processing Service**: Now runs on port `8084`
  - Added explicit `server.port` configuration
  - Actuator metrics at `http://localhost:8084/actuator/prometheus`

#### Configuration Improvements
- Enhanced `.env.example` with comprehensive documentation
  - Added all service ports
  - Documented precedence: env vars > application.yml
  - Organized by service category
  - Added inline comments for all variables
  
- Updated Prometheus scrape configuration
  - Aligned targets with new service ports
  - Added consistent labels (service, application)
  - Set explicit scrape intervals (15s)

#### Documentation Updates
- Updated README.md with correct service URLs
- Updated actuator endpoint references
- Aligned all curl examples with new ports

### Technical Details

**Why the changes:**
- Eliminated port conflicts when running all services simultaneously
- Aligned with Prometheus scraping expectations from docker-compose
- Simplified management endpoints (no separate port needed)
- Standardized port allocation: 8082 (API), 8083 (Ingestion), 8084 (Processing)

**Migration notes:**
- If you have existing `.env` files, update `API_PORT=8082`
- Update any scripts or client configurations to use new ports
- Prometheus will automatically scrape from new ports on restart

## [0.1.0] - 2024-01-XX

### Added - Initial Release

#### Core Services
- routeforge-common: Shared DTOs and utilities
- ingestion-service: GTFS-Realtime feed ingestion with Kafka producer
- processing-service: Kafka consumer with Redis + PostgreSQL dual-write
- api-gateway-service: REST API with OAuth2/JWT support

#### Infrastructure
- Docker Compose setup with Kafka (KRaft), Redis, PostgreSQL
- Prometheus + Grafana for observability
- Keycloak for OAuth2/JWT authentication

#### Features
- Real-time GTFS-RT vehicle position ingestion (NYC MTA)
- Event streaming via Apache Kafka
- Hot cache in Redis (5-minute TTL)
- Historical data storage in PostgreSQL
- REST API for vehicle queries
- Circuit breaker and retry patterns
- Rate limiting (100 req/min per IP)
- OpenAPI/Swagger documentation

#### Observability
- Micrometer metrics with Prometheus export
- Structured JSON logging
- Actuator health endpoints
- Pre-built Grafana dashboard

#### Documentation
- Comprehensive README
- Quick Start guide
- System Design documentation
- API documentation
- Operations Runbook
- Keycloak setup guide
- Contributing guidelines
