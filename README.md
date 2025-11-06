# RouteForge

**Real-time Public Transit Tracking Platform**

RouteForge is a Java + Spring Boot distributed system for real time public transit tracking. It ingests live vehicle position data in the GTFS‑Realtime format, processes that stream asynchronously through Apache Kafka, and keeps two sources of truth: a Redis cache for the most recent positions and a PostgreSQL database for historical records. An API gateway exposes that data through secure REST endpoints and Server‑Sent Events streams, so clients can fetch the current location of a single bus, the list of vehicles on a route, or subscribe to continuous updates without polling.

The system is composed of three Spring‑Boot microservices. The ingestion service polls external GTFS feeds every few seconds, converts the protobuf messages into JSON events, and publishes them to Kafka with circuit breakers and retries to handle feed outages. The processing service consumes those events in batches, updates the Redis cache and writes to PostgreSQL, publishes notifications through Redis and routes unprocessable messages to a dead‑letter queue. The API gateway serves user requests, prioritising the cache and falling back to the database when necessary, enforces rate limits, and can be secured with OAuth2/JWT via Keycloak. A Prometheus–Grafana stack collects metrics such as events processed, cache updates and API latency, and structured logs include trace identifiers for easier debugging.


## 🏗️ Architecture

```
GTFS-RT Feed → Ingestion Service → Kafka → Processing Service → Redis (hot) + PostgreSQL (history) → API Gateway → Clients
```

### Core Services

1. **routeforge-common** - Shared DTOs, utilities, and event schemas
2. **ingestion-service** - GTFS-Realtime feed parser and Kafka producer  
3. **processing-service** - Kafka consumer with dual-write to Redis and PostgreSQL
4. **api-gateway-service** - REST + WebSocket APIs with OAuth2/JWT security

### Infrastructure

- **Apache Kafka** (KRaft mode) - Event streaming
- **Redis** - Hot state cache for low-latency reads
- **PostgreSQL** - Historical data and analytics
- **Prometheus + Grafana** - Metrics and dashboards
- **Keycloak** - OAuth2/JWT authentication

## 🚀 Quick Start

### Prerequisites

- Java 21
- Docker & Docker Compose
- 8GB RAM minimum

### 1. Clone and Configure

```bash
# Copy the environment configuration template
cp .env.example .env

# Edit .env if needed (defaults work for local development)
# The .env.example file includes all required variables with sensible defaults
```

> **📝 Note:** `.env.example` includes all environment variables with defaults for local development. See [Configuration](#-configuration) below for details.

### 2. Start Infrastructure

```bash
docker compose up -d
```

Wait for all services to be healthy (~30 seconds):

```bash
docker compose ps
```

### 3. Build Services

```bash
./gradlew build
```

### 4. Run Services

```bash
# Terminal 1: Ingestion Service
./gradlew :ingestion-service:bootRun

# Terminal 2: Processing Service  
./gradlew :processing-service:bootRun

# Terminal 3: API Gateway
./gradlew :api-gateway-service:bootRun
```

### 5. Verify

```bash
# Check API health
curl http://localhost:8082/api/health

# Get vehicles for a route
curl http://localhost:8082/api/routes/1/vehicles

# View Swagger UI
open http://localhost:8082/swagger-ui.html

# View Grafana
open http://localhost:3000  # admin/admin123
```

## 📊 Monitoring

### Actuator Endpoints

- API Gateway: http://localhost:8082/actuator
- Ingestion: http://localhost:8083/actuator
- Processing: http://localhost:8084/actuator

### Key Metrics

- `routeforge_ingestion_events_published` - Events to Kafka
- `routeforge_processing_events_processed` - Events processed
- `routeforge_processing_cache_updates` - Redis updates
- `routeforge_processing_db_inserts` - DB inserts

## 📖 API Documentation

Interactive docs: **http://localhost:8082/swagger-ui.html**

**Core Endpoints:**
- `GET /api/vehicles/routes/{routeId}` - Get vehicles on route
- `GET /api/vehicles/{vehicleId}` - Get vehicle position
- `GET /api/stream/routes/{routeId}` - SSE stream for real-time updates
- `GET /actuator/health` - Health check

**Admin Endpoints (JWT Required):**
- `DELETE /api/admin/cache/all` - Clear all cache
- `DELETE /api/admin/cache/routes/{routeId}` - Clear route cache
- `GET /api/admin/dlq/metrics` - Get DLQ statistics
- `POST /api/admin/ingestion/replay` - Replay failed messages
- `GET /api/admin/stats` - System statistics

See [API.md](docs/API.md) for complete documentation.

## 🧪 Testing

```bash
./gradlew test                  # Unit tests
./gradlew integrationTest       # Integration tests with Testcontainers
```

## 📦 Project Structure

```
routeforge/
├── routeforge-common/           # Shared DTOs
├── ingestion-service/           # GTFS-RT ingestion
├── processing-service/          # Event processing
├── api-gateway-service/         # REST APIs
├── infra/                       # Infrastructure configs
│   ├── prometheus/
│   ├── grafana/
│   └── terraform/               # AWS deployment
└── docker-compose.yml
```

## 🔒 Security

**Development (default):** All endpoints accessible without auth

**Production:** Enable OAuth2/JWT:
- Set `spring.profiles.active=prod`
- Configure Keycloak (see KEYCLOAK.md)
- Public endpoints have rate limiting (100 req/min)

## 🚢 Deployment

### AWS (Optional)

```bash
cd infra/terraform
terraform apply
```

⚠️ **Cost Warning:** AWS resources incur charges. See terraform/README.md.

## 🔧 Configuration

Key environment variables in `.env`:

```env
GTFS_RT_FEED_URL=https://api-endpoint.mta.info/...
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
REDIS_HOST=localhost
POSTGRES_JDBC_URL=jdbc:postgresql://localhost:5432/routeforge
```

## 🐛 Troubleshooting

**Services won't start:**
```bash
docker compose ps
docker compose logs kafka redis postgres
docker compose restart
```

**Check Kafka topics:**
```bash
docker exec routeforge-kafka kafka-topics --bootstrap-server localhost:9092 --list
```

**Check Redis cache:**
```bash
docker exec routeforge-redis redis-cli KEYS "*"
```

**Query database:**
```bash
docker exec -it routeforge-postgres psql -U routeforge -d routeforge
\dt
SELECT * FROM vehicle_positions_history LIMIT 10;
```

## 📚 Documentation

- DESIGN.md - System architecture
- API.md - API specifications  
- RUNBOOK.md - Operations guide
- CONTRIBUTING.md - Contribution guidelines

## 📄 License

Apache License 2.0 - see [LICENSE](LICENSE)

## 🤝 Contributing

Contributions welcome! See [CONTRIBUTING.md](CONTRIBUTING.md)
