# Keycloak Setup for OAuth2/JWT Authentication

This guide explains how to configure Keycloak for RouteForge authentication.

## Quick Start (Development)

Keycloak is included in `docker-compose.yml` and runs on http://localhost:8080.

**✨ Automated Setup:** The `routeforge` realm is automatically imported from `infra/keycloak/routeforge-realm.json` on first startup. You can skip manual configuration!

### 1. Access Keycloak Admin Console

```bash
open http://localhost:8080
```

Login with:
- Username: `admin`
- Password: `admin123`

### 2. Pre-configured Users

The realm comes with these test users:

**Admin User:**
- Username: `admin-user`
- Password: `admin123`
- Role: `admin` (can access all endpoints including `/api/admin/**`)

**Regular User:**
- Username: `test-user`
- Password: `test123`
- Role: `viewer` (read-only access)

### 3. Obtaining JWT Tokens

#### Option A: Using the Helper Script (Recommended)

```bash
# Get admin token
./scripts/get-token.sh admin-user admin123

# Get viewer token
./scripts/get-token.sh test-user test123
```

The script outputs the JWT token which you can use in API requests:
```bash
TOKEN=$(./scripts/get-token.sh admin-user admin123)
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8082/api/admin/stats
```

#### Option B: Manual Token Request

```bash
curl -X POST http://localhost:8080/realms/routeforge/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=routeforge-api" \
  -d "client_secret=routeforge-secret" \
  -d "username=admin-user" \
  -d "password=admin123" \
  -d "grant_type=password" | jq -r '.access_token'
```

### 4. Testing Protected Endpoints

```bash
# Get token
TOKEN=$(./scripts/get-token.sh admin-user admin123)

# Test admin endpoint
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8082/api/admin/dlq/metrics

# Clear cache
curl -X DELETE -H "Authorization: Bearer $TOKEN" \
  http://localhost:8082/api/admin/cache/routes/1

# Trigger DLQ replay
curl -X POST -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8082/api/admin/ingestion/replay?minutes=10"
```

### 5. Manual Configuration (Optional)

If you need to create additional users or modify the realm:

#### Create Realm (if not using auto-import)

1. Click dropdown in top-left (currently "master")
2. Click "Create Realm"
3. Name: `routeforge`
4. Click "Create"

#### Create Client

1. Navigate to "Clients" → "Create client"
2. Configuration:
   - Client ID: `routeforge-api`
   - Client authentication: ON
   - Authentication flow: Standard flow, Direct access grants
3. Click "Save"
4. In "Credentials" tab, set Client Secret to: `routeforge-secret`

#### Create Roles

1. Navigate to "Realm roles" → "Create role"
2. Create roles:
   - `viewer` - Can read all endpoints
   - `admin` - Can access admin endpoints

#### Create Users

1. Navigate to "Users" → "Create user"
2. Username: `viewer`
3. Email: `viewer@routeforge.io`
4. Click "Create"
5. In "Credentials" tab:
   - Set password: `viewer123`
   - Temporary: OFF
6. In "Role mapping" tab:
   - Assign role: `viewer`

#### Admin User

1. Navigate to "Users" → "Create user"
2. Username: `admin`
3. Email: `admin@routeforge.io`
4. Click "Create"
5. In "Credentials" tab:
   - Set password: `admin123`
   - Temporary: OFF
6. In "Role mapping" tab:
   - Assign roles: `viewer`, `admin`

## Testing Authentication

### 1. Get Access Token

```bash
curl -X POST http://localhost:8080/realms/routeforge/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=routeforge-api" \
  -d "client_secret=<your-client-secret>" \
  -d "username=admin" \
  -d "password=admin123" \
  -d "grant_type=password"
```

Response:
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIs...",
  "expires_in": 300,
  "refresh_token": "eyJhbGciOiJIUzI1NiIs...",
  "token_type": "Bearer"
}
```

### 2. Call Protected Endpoint

```bash
TOKEN="<access-token-from-above>"

curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8081/api/admin/stats
```

## Production Configuration

### 1. Environment Variables

```env
# Production Keycloak instance
JWT_ISSUER_URI=https://keycloak.yourdomain.com/realms/routeforge
KEYCLOAK_AUTH_SERVER_URL=https://keycloak.yourdomain.com
KEYCLOAK_CLIENT_SECRET=<production-secret>
```

### 2. Enable Production Security

In `api-gateway-service/src/main/resources/application.yml`:

```yaml
spring:
  profiles:
    active: prod  # Remove 'dev' profile
```

### 3. Configure HTTPS

Keycloak requires HTTPS in production:

```bash
# Generate SSL certificate
openssl req -newkey rsa:2048 -nodes \
  -keyout keycloak.key -x509 -days 365 \
  -out keycloak.crt

# Mount in Docker
volumes:
  - ./keycloak.crt:/etc/x509/https/tls.crt
  - ./keycloak.key:/etc/x509/https/tls.key
```

## Realm Export/Import

### Export Realm Configuration

```bash
docker exec routeforge-keycloak \
  /opt/keycloak/bin/kc.sh export \
  --dir /tmp/export \
  --realm routeforge

docker cp routeforge-keycloak:/tmp/export/routeforge-realm.json ./routeforge-realm.json
```

### Import Realm Configuration

```bash
# Add to docker-compose.yml
keycloak:
  volumes:
    - ./routeforge-realm.json:/opt/keycloak/data/import/routeforge-realm.json
  command:
    - start-dev
    - --import-realm
```

## Automated Setup Script

```bash
#!/bin/bash
# scripts/setup-keycloak.sh

KEYCLOAK_URL="http://localhost:8080"
ADMIN_USER="admin"
ADMIN_PASS="admin123"

# Get admin token
TOKEN=$(curl -s -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=$ADMIN_USER" \
  -d "password=$ADMIN_PASS" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" | jq -r '.access_token')

# Create realm
curl -X POST "$KEYCLOAK_URL/admin/realms" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{ "realm": "routeforge", "enabled": true }'

echo "Keycloak realm 'routeforge' created!"
```

## Troubleshooting

### Invalid Token Error

```
HTTP 401 Unauthorized
An error occurred while attempting to decode the Jwt
```

**Solution:** Check `JWT_ISSUER_URI` matches Keycloak realm URL exactly.

### CORS Error

**Solution:** Add your frontend URL to Keycloak client "Web Origins":
```
http://localhost:3000
http://localhost:8081
```

### Token Expiration

Default token expiry is 5 minutes. To change:

1. Navigate to "Realm settings" → "Tokens"
2. Adjust "Access Token Lifespan"
3. Save

## References

- [Keycloak Documentation](https://www.keycloak.org/documentation)
- [Spring Security OAuth2](https://spring.io/guides/tutorials/spring-boot-oauth2/)
- [JWT.io Debugger](https://jwt.io/)
