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
curl http://localhost:8082/api/vehicles/VEHICLE_123
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

- **Swagger UI:** http://localhost:8082/swagger-ui.html
- **OpenAPI JSON:** http://localhost:8082/v3/api-docs

---

## Actuator Endpoints

Operational endpoints (exposed on port 8082):

- **Health:** http://localhost:8082/actuator/health
- **Metrics:** http://localhost:8082/actuator/metrics
- **Prometheus:** http://localhost:8082/actuator/prometheus

---

### Stream Live Vehicle Updates (SSE)

**GET** `/api/stream/routes/{routeId}`

Opens a Server-Sent Events (SSE) connection for real-time vehicle position updates.

**Parameters:**
- `routeId` (path, required): Route identifier

**Headers:**
- `Accept: text/event-stream`

**Example Request:**
```bash
curl -N -H "Accept: text/event-stream" \
  http://localhost:8082/api/stream/routes/1
```

**Response:** Continuous event stream

```
event: connected
data: {"message":"Connected to route 1","timestamp":1704067200000}

event: vehicle-update
data: {"vehicleId":"VEHICLE_123","routeId":"1","lat":40.7128,"lon":-74.0060,"speedKph":25.5,"headingDeg":90.0,"timestamp":"2024-01-01T12:00:00Z"}

event: heartbeat
: keep-alive

event: vehicle-update
data: {"vehicleId":"VEHICLE_124","routeId":"1","lat":40.7589,"lon":-73.9851,"speedKph":30.0,"headingDeg":180.0,"timestamp":"2024-01-01T12:00:05Z"}
```

**Event Types:**
- `connected` - Initial connection confirmation
- `vehicle-update` - Real-time vehicle position update (JSON)
- `heartbeat` - Keep-alive ping (every 15 seconds)

**Connection Details:**
- Timeout: 30 minutes of inactivity
- Reconnection: Client should reconnect on disconnect
- Backpressure: Server drops oldest events if client can't keep up

**JavaScript Client Example:**
```javascript
const eventSource = new EventSource('http://localhost:8082/api/stream/routes/1');

eventSource.addEventListener('connected', (event) => {
  console.log('Connected:', JSON.parse(event.data));
});

eventSource.addEventListener('vehicle-update', (event) => {
  const vehicle = JSON.parse(event.data);
  console.log('Vehicle update:', vehicle);
  // Update map marker at vehicle.lat, vehicle.lon
});

eventSource.onerror = (error) => {
  console.error('SSE error:', error);
  eventSource.close();
  // Implement reconnection logic
};
```

---

### Get Streaming Statistics

**GET** `/api/stream/stats`

Returns current SSE streaming statistics.

**Example Request:**
```bash
curl http://localhost:8082/api/stream/stats
```

**Response:** `200 OK`
```json
{
  "activeConnections": 15,
  "activeRoutes": 3
}
```

---

### Get ETA Predictions

**GET** `/api/routes/{routeId}/eta?stopId={stopId}`

Calculate estimated time of arrival for all vehicles on a route to a specific stop.

**Parameters:**
- `routeId` (path, required): Route identifier
- `stopId` (query, required): Stop identifier for ETA calculation

**Example Request:**
```bash
curl "http://localhost:8082/api/routes/1/eta?stopId=STOP_456"
```

**Response:** `200 OK`
```json
[
  {
    "vehicleId": "VEHICLE_123",
    "routeId": "1",
    "stopId": "STOP_456",
    "currentLat": 40.7128,
    "currentLon": -74.0060,
    "stopLat": 40.7589,
    "stopLon": -73.9851,
    "distanceKm": 6.42,
    "scheduledArrival": null,
    "predictedArrival": "2024-01-01T12:15:30Z",
    "delaySeconds": 120,
    "confidence": 0.85,
    "calculatedAt": "2024-01-01T12:00:00Z",
    "note": null
  },
  {
    "vehicleId": "VEHICLE_124",
    "routeId": "1",
    "stopId": "STOP_456",
    "currentLat": 40.7300,
    "currentLon": -73.9900,
    "stopLat": 40.7589,
    "stopLon": -73.9851,
    "distanceKm": 3.21,
    "scheduledArrival": null,
    "predictedArrival": "2024-01-01T12:07:45Z",
    "delaySeconds": null,
    "confidence": 0.45,
    "calculatedAt": "2024-01-01T12:00:00Z",
    "note": "Low confidence - insufficient historical data"
  }
]
```

**Fields:**
- `distanceKm` - Haversine distance from current position to stop
- `predictedArrival` - Estimated arrival time based on historical speed + current speed
- `confidence` - Prediction confidence (0.0-1.0) based on sample size
- `note` - Additional information (e.g., low confidence warning)

**Algorithm:**
1. Get current vehicle positions from Redis
2. Query historical average speed from PostgreSQL (last hour)
3. Use vehicle's current speed if available, else historical average
4. Calculate travel time: `distance / speed`
5. Confidence based on number of historical samples

**Response:** `404 Not Found` - No vehicles on route or stop not found

---

## Admin Endpoints (JWT Protected)

All admin endpoints require authentication with `SCOPE_admin` or `ROLE_ADMIN`.

### Clear All Cache

**DELETE** `/api/admin/cache/all`

Clears all vehicle and route cache keys from Redis.

**Headers:**
```
Authorization: Bearer <jwt-token>
```

**Example Request:**
```bash
TOKEN="<your-jwt-token>"
curl -X DELETE \
  -H "Authorization: Bearer $TOKEN" \
  http://localhost:8082/api/admin/cache/all
```

**Response:** `200 OK`
```json
{
  "status": "success",
  "message": "Cache cleared successfully",
  "keysDeleted": 1523,
  "timestamp": 1704067200000
}
```

---

### Clear Route Cache

**DELETE** `/api/admin/cache/routes/{routeId}`

Clears cache for a specific route.

**Parameters:**
- `routeId` (path, required): Route identifier

**Example Request:**
```bash
curl -X DELETE \
  -H "Authorization: Bearer $TOKEN" \
  http://localhost:8082/api/admin/cache/routes/1
```

**Response:** `200 OK`
```json
{
  "status": "success",
  "message": "Route cache cleared",
  "routeId": "1",
  "keysDeleted": 15,
  "timestamp": 1704067200000
}
```

---

### Get DLQ Metrics

**GET** `/api/admin/dlq/metrics`

Returns real-time statistics about dead-letter queue messages using Kafka Admin API.

**Example Request:**
```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8082/api/admin/dlq/metrics
```

**Response:** `200 OK`
```json
{
  "dlqTopic": "vehicle_positions.dlq",
  "partitionCount": 3,
  "totalMessages": 42,
  "partitions": {
    "0": {
      "earliestOffset": 0,
      "latestOffset": 15,
      "messageCount": 15
    },
    "1": {
      "earliestOffset": 0,
      "latestOffset": 20,
      "messageCount": 20
    },
    "2": {
      "earliestOffset": 0,
      "latestOffset": 7,
      "messageCount": 7
    }
  },
  "status": "success",
  "instructions": {
    "consume": "kafka-console-consumer --bootstrap-server localhost:9092 --topic vehicle_positions.dlq --from-beginning",
    "count": "Total messages currently in DLQ: 42"
  },
  "timestamp": 1704067200000
}
```

**Fields:**
- `totalMessages` - Total number of messages across all partitions
- `partitionCount` - Number of topic partitions
- `partitions` - Per-partition offset details
- `status` - "success" or "error"
- `instructions` - CLI commands for DLQ inspection

**Response:** `200 OK` (with error status on Kafka connection failure)
```json
{
  "dlqTopic": "vehicle_positions.dlq",
  "status": "error",
  "error": "Connection to Kafka failed",
  "note": "Failed to connect to Kafka. Ensure Kafka is running and accessible.",
  "timestamp": 1704067200000
}
```

---

### Trigger Ingestion Replay

**POST** `/api/admin/ingestion/replay?minutes=10`

Triggers ingestion service to replay recent feed data.

**Parameters:**
- `minutes` (query, optional): Minutes of history to replay (default: 10)

**Example Request:**
```bash
curl -X POST \
  -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8082/api/admin/ingestion/replay?minutes=15"
```

**Response:** `202 Accepted`
```json
{
  "status": "accepted",
  "message": "Replay triggered successfully",
  "minutesToReplay": 15,
  "timestamp": 1704067200000
}
```

**Response:** `503 Service Unavailable` - Ingestion service unavailable

---

### Get Admin Statistics

**GET** `/api/admin/stats`

Returns overall system statistics for monitoring.

**Example Request:**
```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8082/api/admin/stats
```

**Response:** `200 OK`
```json
{
  "redis": {
    "vehicleKeys": 1250,
    "routeKeys": 45,
    "totalKeys": 1295
  },
  "sse": {
    "activeConnections": 23,
    "activeRoutes": 8
  },
  "timestamp": 1704067200000
}
```

---

## Future Endpoints (Roadmap)

### Get Route Details

**GET** `/api/routes/{routeId}`

Get route metadata (name, stops, schedule).

### Search Nearby Vehicles

**GET** `/api/vehicles/nearby?lat={lat}&lon={lon}&radius={meters}`

Find vehicles within a geographic radius.

### Admin: Clear Cache

**POST** `/api/admin/cache/clear`

Clear Redis cache (requires admin role).
