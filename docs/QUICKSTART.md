# RouteForge Quick Start Guide

Get RouteForge running locally in 5 minutes!

## Prerequisites

Ensure you have installed:

- **Java 17+** - [Download OpenJDK](https://adoptium.net/)
- **Docker Desktop** - [Download Docker](https://www.docker.com/products/docker-desktop/)
- **8GB RAM** minimum

Verify installations:
```bash
java -version    # Should show 17 or higher
docker --version
docker compose version
```

## Step 1: Start Infrastructure (2 minutes)

```bash
# Copy environment config
cp .env.example .env

# Start Kafka, Redis, PostgreSQL, etc.
docker compose up -d

# Wait for services to be healthy (~30 seconds)
docker compose ps
```

All services should show "healthy" status. If not, wait a bit longer or check logs:
```bash
docker compose logs
```

## Step 2: Build Services (1 minute)

```bash
# Build all microservices
./gradlew build -x test  # Skip tests for faster build

# Or with tests (takes ~3 minutes)
./gradlew build
```

## Step 3: Run Services (1 minute)

Open 3 terminal windows and run:

**Terminal 1: Ingestion Service**
```bash
./gradlew :ingestion-service:bootRun
```

**Terminal 2: Processing Service**
```bash
./gradlew :processing-service:bootRun
```

**Terminal 3: API Gateway**
```bash
./gradlew :api-gateway-service:bootRun
```

Wait for each service to show:
```
Started [ServiceName]Application in X.XXX seconds
```

## Step 4: Verify & Test (1 minute)

### Check Health
```bash
curl http://localhost:8082/api/health
```

Expected response:
```json
{
  "status": "UP",
  "timestamp": "2024-01-01T12:00:00Z",
  "service": "api-gateway"
}
```

### Get Vehicle Positions

```bash
# Wait ~10 seconds for data ingestion, then:
curl http://localhost:8082/api/routes/1/vehicles | jq
```

You should see an array of vehicle positions!

### View in Browser

- **API Docs:** http://localhost:8082/swagger-ui.html
- **Grafana:** http://localhost:3000 (admin/admin123)
- **Prometheus:** http://localhost:9090

## What's Running?

| Service | Port | Purpose |
|---------|------|---------|
| API Gateway | 8081 | REST API |
| Ingestion | 8083 | GTFS-RT polling |
| Processing | 8084 | Event processing |
| Kafka | 9092 | Event streaming |
| Redis | 6379 | Hot cache |
| PostgreSQL | 5432 | Historical data |
| Keycloak | 8080 | OAuth2/JWT |
| Prometheus | 9090 | Metrics |
| Grafana | 3000 | Dashboards |

## Common Issues

### "Port already in use"
```bash
# Find and kill process
lsof -ti:8081 | xargs kill -9  # Replace 8081 with your port
```

### "Cannot connect to Docker daemon"
```bash
# Start Docker Desktop
# On Linux: sudo systemctl start docker
```

### "Kafka not ready"
```bash
# Check Kafka logs
docker compose logs kafka

# Restart Kafka
docker compose restart kafka
```

### "No vehicles found"
```bash
# Wait ~30 seconds for initial data ingestion
# Check ingestion logs
docker compose logs ingestion-service

# Verify Kafka messages
docker exec routeforge-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic vehicle_positions \
  --from-beginning \
  --max-messages 5
```

## Next Steps

1. **Explore the API:** Visit http://localhost:8081/swagger-ui.html
2. **View Metrics:** Open Grafana at http://localhost:3000
3. **Read the Docs:** Check out [DESIGN.md](DESIGN.md) and [API.md](API.md)
4. **Run Tests:** `./gradlew test`
5. **Enable OAuth2:** See [KEYCLOAK.md](KEYCLOAK.md)

## Stopping

```bash
# Stop services (Ctrl+C in each terminal)

# Stop infrastructure
docker compose down

# Clean everything (removes data)
docker compose down -v
```

## Need Help?

- Check [RUNBOOK.md](RUNBOOK.md) for troubleshooting
- Open an issue on GitHub
- Review logs: `docker compose logs`

Happy tracking! 🚇
