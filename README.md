# RouteForge

**Real-time Public Transit Tracking Platform**

RouteForge is a production-grade, cloud-native distributed system that ingests GTFS-Realtime vehicle position feeds, processes them asynchronously through Apache Kafka, maintains hot cache state in Redis, stores historical data in PostgreSQL, and exposes secure REST + WebSocket APIs for real-time vehicle tracking.

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green.svg)](https://spring.io/projects/spring-boot)

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

- Java 17+
- Docker & Docker Compose
- 8GB RAM minimum

### 1. Clone and Configure

```bash
cp .env.example .env
# Edit .env if needed (defaults work for local development)
```

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

- Ingestion: http://localhost:8083/actuator
- Processing: http://localhost:8084/actuator
- API Gateway: http://localhost:8082/actuator

### Key Metrics

- `routeforge_ingestion_events_published` - Events to Kafka
- `routeforge_processing_events_processed` - Events processed
- `routeforge_processing_cache_updates` - Redis updates
- `routeforge_processing_db_inserts` - DB inserts

## 📖 API Documentation

Interactive docs: http://localhost:8081/swagger-ui.html

**Core Endpoints:**
- `GET /api/routes/{routeId}/vehicles` - Get vehicles on route
- `GET /api/vehicles/{vehicleId}` - Get vehicle position
- `GET /api/health` - Health check

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

---

**Built with ❤️ for the transit community**
