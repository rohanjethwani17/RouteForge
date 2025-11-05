# Migration Guide

This document describes breaking changes and migration steps for RouteForge updates.

## Port Configuration Changes (Phase 0)

### What Changed

Service ports have been standardized to eliminate conflicts and align with Prometheus expectations:

| Service | Old Port | New Port | Actuator Path |
|---------|----------|----------|---------------|
| API Gateway | 8081 + 8082 (mgmt) | 8082 | `/actuator/*` |
| Ingestion Service | (not set) | 8083 | `/actuator/*` |
| Processing Service | (not set) | 8084 | `/actuator/*` |

### Why the Change

1. **Port Conflicts**: Running all services simultaneously caused conflicts
2. **Prometheus Integration**: Aligned with docker-compose scraping configuration
3. **Simplified Management**: Removed separate management ports - all endpoints on main port
4. **Consistency**: Standardized port allocation pattern

### Migration Steps

#### 1. Update Environment Variables

If you have an existing `.env` file, update these variables:

```bash
# Old
API_PORT=8081
MANAGEMENT_PORT=8082

# New
API_PORT=8082
INGESTION_PORT=8083
PROCESSING_PORT=8084
```

Or simply:
```bash
cp .env.example .env
```

#### 2. Update Client Configurations

If you have scripts or applications calling the API, update URLs:

```bash
# Old
http://localhost:8081/api/vehicles

# New
http://localhost:8082/api/vehicles
```

#### 3. Update Bookmarks/Documentation

- Swagger UI: http://localhost:8082/swagger-ui.html (was 8081)
- API Health: http://localhost:8082/api/health (was 8081)
- Actuator: http://localhost:8082/actuator (was separate port 8082)

#### 4. Restart Services

```bash
# Stop running services (Ctrl+C)

# Restart with new configuration
./gradlew :api-gateway-service:bootRun
./gradlew :ingestion-service:bootRun
./gradlew :processing-service:bootRun
```

#### 5. Verify Prometheus Scraping

```bash
# Check Prometheus targets
open http://localhost:9090/targets

# All three services should show "UP" status
```

### No Database Migration Required

This change only affects service ports - no database schema changes or data migration needed.

### Rollback

If you need to rollback to old ports, update `application.yml`:

```yaml
# api-gateway-service/src/main/resources/application.yml
server:
  port: 8081  # Change back from 8082

management:
  server:
    port: 8082  # Restore separate management port
```

Then rebuild and restart services.

## Configuration Precedence

RouteForge now clearly documents configuration precedence:

```
Environment Variables > application.yml defaults
```

Example:
```bash
# This overrides application.yml
export API_PORT=9082
./gradlew :api-gateway-service:bootRun
```

## Troubleshooting

### "Connection refused" after update

**Cause**: Client still using old port (8081)  
**Solution**: Update client to use port 8082

### Services won't start - "Port already in use"

**Cause**: Multiple instances or old process still running  
**Solution**:
```bash
# Find and kill process
lsof -ti:8082 | xargs kill -9
lsof -ti:8083 | xargs kill -9
lsof -ti:8084 | xargs kill -9
```

### Prometheus shows all targets down

**Cause**: Services not running or firewall blocking  
**Solution**:
```bash
# Verify services are up
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health

# Restart Prometheus
docker compose restart prometheus
```

## Future Changes

We'll document all breaking changes here. Subscribe to releases for notifications:
- GitHub: Watch > Custom > Releases

## Questions?

- Check [RUNBOOK.md](RUNBOOK.md) for operational guidance
- Open an issue on GitHub
- Review [CHANGELOG.md](../CHANGELOG.md) for all changes
