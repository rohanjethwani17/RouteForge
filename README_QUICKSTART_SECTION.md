## Quick Start

### Prerequisites
- Docker & Docker Compose
- Java 21
- Git

### Setup & Run

1. **Clone and setup environment:**
   ```bash
   git clone https://github.com/your-username/RouteForge.git
   cd RouteForge
   cp .env.example .env
   ```

2. **Start infrastructure:**
   ```bash
   docker compose up -d
   ```

3. **Build the application:**
   ```bash
   ./gradlew build
   ```

4. **Start services** (in separate terminals):
   ```bash
   # Terminal 1 - API Gateway
   java -jar api-gateway-service/build/libs/api-gateway-service-0.1.0-SNAPSHOT.jar
   
   # Terminal 2 - Processing Service  
   java -jar processing-service/build/libs/processing-service-0.1.0-SNAPSHOT.jar
   
   # Terminal 3 - Ingestion Service
   java -jar ingestion-service/build/libs/ingestion-service-0.1.0-SNAPSHOT.jar
   ```

### Verify Setup

- **API Health**: `curl http://localhost:8082/api/health`
- **Swagger UI**: http://localhost:8082/swagger-ui/index.html
- **Grafana**: http://localhost:3000 (admin/admin123)
- **Prometheus**: http://localhost:9090

### Stop System
```bash
# Stop services: Ctrl+C in each terminal
# Stop infrastructure:
docker compose down
```

### Troubleshooting
- **Port conflicts**: The setup uses PostgreSQL on port 5433 (not 5432) to avoid conflicts
- **Gradle issues**: Run `./gradlew --stop` then retry
- **Service logs**: Check terminal output for specific error messages