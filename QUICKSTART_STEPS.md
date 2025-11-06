# RouteForge Quick Start Guide

## Prerequisites

Ensure you have the following installed on your machine:
- **Docker** and **Docker Compose** (for infrastructure)
- **Java 21** (for running the services)
- **Git** (for cloning the repository)

Verify your setup:
```bash
docker --version
docker compose version
java -version  # Should show Java 21
```

## Getting Started

### 1. Clone and Setup

```bash
git clone https://github.com/your-username/RouteForge.git
cd RouteForge
```

### 2. Environment Configuration

Copy the example environment file:
```bash
cp .env.example .env
```

The defaults in `.env` are suitable for local development. You can adjust ports, hostnames, and other settings if needed.

### 3. Start Infrastructure

Start the required infrastructure services (Kafka, Redis, PostgreSQL, Keycloak, Prometheus, Grafana):
```bash
docker compose up -d
```

Wait for all containers to be healthy:
```bash
docker compose ps
```

All services should show "healthy" status. This may take 1-2 minutes.

### 4. Build the Application

Build all services and run tests:
```bash
./gradlew build
```

This will:
- Download dependencies
- Compile all services
- Run unit and integration tests
- Create executable JAR files

### 5. Start the Services

Start each microservice in separate terminal windows or as background processes:

**Terminal 1 - API Gateway:**
```bash
java -jar api-gateway-service/build/libs/api-gateway-service-0.1.0-SNAPSHOT.jar
```

**Terminal 2 - Processing Service:**
```bash
java -jar processing-service/build/libs/processing-service-0.1.0-SNAPSHOT.jar
```

**Terminal 3 - Ingestion Service:**
```bash
java -jar ingestion-service/build/libs/ingestion-service-0.1.0-SNAPSHOT.jar
```

Wait for all services to start (look for "Started [ServiceName]Application" in the logs).

## Verification

### 1. Check Service Health

Verify all services are running:
```bash
# API Gateway
curl http://localhost:8082/api/health

# Processing Service  
curl http://localhost:8084/actuator/health

# Ingestion Service
curl http://localhost:8083/actuator/health
```

All should return `{"status":"UP"}` or similar.

### 2. Access the API

- **Swagger UI**: http://localhost:8082/swagger-ui/index.html
- **API Endpoints**: http://localhost:8082/api/
- **Example**: `curl http://localhost:8082/api/routes/1/vehicles`

### 3. Monitoring Dashboards

- **Grafana**: http://localhost:3000 (admin/admin123)
- **Prometheus**: http://localhost:9090

## Testing

### Run Unit Tests
```bash
./gradlew test
```

### Run Integration Tests
```bash
./gradlew :api-gateway-service:test :processing-service:test --tests "*IntegrationTest"
```

## Stopping the System

### Stop Services
Press `Ctrl+C` in each terminal running the services.

### Stop Infrastructure
```bash
docker compose down
```

To also remove volumes (clears all data):
```bash
docker compose down -v
```

## Troubleshooting

### Port Conflicts
If you encounter port conflicts, check what's running:
```bash
lsof -i :5432  # PostgreSQL
lsof -i :6379  # Redis
lsof -i :9092  # Kafka
```

### Service Won't Start
1. Check if infrastructure is running: `docker compose ps`
2. Verify Java version: `java -version` (should be 21)
3. Check logs for specific error messages
4. Ensure all previous builds completed: `./gradlew clean build`

### Gradle Issues
If you encounter Gradle daemon issues:
```bash
./gradlew --stop
./gradlew clean build --no-daemon
```

## Development Mode

For active development, you can run services with auto-reload:
```bash
# Alternative: Use Gradle bootRun (if working)
./gradlew :api-gateway-service:bootRun --no-daemon
```

## Service Ports

| Service | Port | Purpose |
|---------|------|---------|
| API Gateway | 8082 | Main API and Swagger UI |
| Processing Service | 8084 | Internal processing |
| Ingestion Service | 8083 | GTFS data ingestion |
| PostgreSQL | 5433 | Database |
| Redis | 6379 | Cache and pub/sub |
| Kafka | 9092 | Message streaming |
| Keycloak | 8080 | Authentication |
| Prometheus | 9090 | Metrics collection |
| Grafana | 3000 | Monitoring dashboards |

## Next Steps

1. **Explore the API**: Visit the Swagger UI to test endpoints
2. **Monitor the System**: Check Grafana dashboards for metrics
3. **View Real-time Data**: Use the SSE streaming endpoints
4. **Customize Configuration**: Modify `.env` file for your needs

For more detailed information, see the full documentation in the `docs/` directory.