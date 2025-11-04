# RouteForge API Documentation

## Base URL

```
http://localhost:8082/api
```

## Authentication

### Development Mode (Default)
No authentication required.

### Production Mode
Requires JWT Bearer token for `/api/admin/**` endpoints.

```
Authorization: Bearer <jwt-token>
```

## Rate Limiting

Public endpoints are rate-limited:
- **Limit:** 100 requests per minute per IP
- **Headers:**
  - `X-RateLimit-Limit`: Total capacity
  - `X-RateLimit-Remaining`: Remaining requests
  - `X-RateLimit-Reset`: Reset timestamp

**Response on limit exceeded:**
```json
HTTP 429 Too Many Requests
{
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Try again later."
}
```

## Endpoints

### Health Check

**GET** `/api/health`

Check API health status.

**Response:**
```json
{
  "status": "UP",
  "timestamp": "2024-01-01T12:00:00Z",
  "service": "api-gateway"
}
```

---

### Get Vehicles by Route

**GET** `/api/routes/{routeId}/vehicles`

Retrieve all active vehicles currently serving a route.

**Parameters:**
- `routeId` (path, required): Route identifier

**Example Request:**
```bash
curl http://localhost:8082/api/routes/1/vehicles
```

**Response:** `200 OK`
```json
[
  {
    "vehicleId": "VEHICLE_123",
    "routeId": "1",
    "lat": 40.7128,
    "lon": -74.0060,
    "speedKph": 25.5,
    "headingDeg": 90.0,
    "timestamp": "2024-01-01T12:00:00Z",
    "stopId": "STOP_456",
    "delaySec": 120
  },
  {
    "vehicleId": "VEHICLE_124",
    "routeId": "1",
    "lat": 40.7589,
    "lon": -73.9851,
    "speedKph": 30.0,
    "headingDeg": 180.0,
    "timestamp": "2024-01-01T12:00:05Z",
    "stopId": null,
    "delaySec": null
  }
]
```

**Response:** `404 Not Found`
```json
{
  "timestamp": "2024-01-01T12:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "No vehicles found for route: 1",
  "path": "/api/routes/1/vehicles"
}
```

---

### Get Vehicle by ID

**GET** `/api/vehicles/{vehicleId}`

Retrieve current position of a specific vehicle.

**Parameters:**
- `vehicleId` (path, required): Vehicle identifier

**Example Request:**
```bash
curl http://localhost:8081/api/vehicles/VEHICLE_123
```

**Response:** `200 OK`
```json
{
  "vehicleId": "VEHICLE_123",
  "routeId": "1",
  "lat": 40.7128,
  "lon": -74.0060,
  "speedKph": 25.5,
  "headingDeg": 90.0,
  "timestamp": "2024-01-01T12:00:00Z",
  "stopId": "STOP_456",
  "delaySec": 120
}
```

**Response:** `404 Not Found`
```json
{
  "timestamp": "2024-01-01T12:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Vehicle not found: VEHICLE_123",
  "path": "/api/vehicles/VEHICLE_123"
}
```

---

## Error Responses

### Standard Error Format

All errors follow this structure:

```json
{
  "timestamp": "2024-01-01T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Detailed error message",
  "path": "/api/endpoint",
  "traceId": "abc123..."
}
```

### Common Status Codes

- **200 OK** - Request successful
- **400 Bad Request** - Invalid parameters
- **401 Unauthorized** - Missing or invalid JWT token
- **404 Not Found** - Resource not found
- **429 Too Many Requests** - Rate limit exceeded
- **500 Internal Server Error** - Server error (includes traceId for debugging)

---

## OpenAPI/Swagger

Interactive API documentation:

- **Swagger UI:** http://localhost:8081/swagger-ui.html
- **OpenAPI JSON:** http://localhost:8081/v3/api-docs

---

## Actuator Endpoints

Operational endpoints (exposed on port 8082):

- **Health:** http://localhost:8082/actuator/health
- **Metrics:** http://localhost:8082/actuator/metrics
- **Prometheus:** http://localhost:8082/actuator/prometheus

---

## Future Endpoints (Roadmap)

### Get ETA

**GET** `/api/routes/{routeId}/eta?stopId={stopId}`

Estimate arrival time at a stop.

### Get Route Details

**GET** `/api/routes/{routeId}`

Get route metadata (name, stops, schedule).

### Search Nearby Vehicles

**GET** `/api/vehicles/nearby?lat={lat}&lon={lon}&radius={meters}`

Find vehicles within a geographic radius.

### Admin: Clear Cache

**POST** `/api/admin/cache/clear`

Clear Redis cache (requires admin role).
