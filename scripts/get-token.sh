#!/bin/bash
# Script to get JWT token from Keycloak for testing admin endpoints

set -e

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8080}"
REALM="${KEYCLOAK_REALM:-routeforge}"
CLIENT_ID="${KEYCLOAK_CLIENT_ID:-routeforge-api}"
CLIENT_SECRET="${KEYCLOAK_CLIENT_SECRET:-routeforge-secret}"

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}RouteForge JWT Token Generator${NC}"
echo "=================================="
echo ""

# Check if username is provided
if [ -z "$1" ]; then
    echo -e "${YELLOW}Usage: $0 <username> [password]${NC}"
    echo ""
    echo "Available users:"
    echo "  admin    - Full admin access (password: admin123)"
    echo "  viewer   - Read-only access (password: viewer123)"
    echo ""
    echo "Example: $0 admin admin123"
    exit 1
fi

USERNAME="$1"
PASSWORD="${2}"

# Prompt for password if not provided
if [ -z "$PASSWORD" ]; then
    echo -n "Password for $USERNAME: "
    read -s PASSWORD
    echo ""
fi

echo ""
echo "Requesting token from Keycloak..."
echo "URL: $KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token"
echo ""

# Get token
RESPONSE=$(curl -s -X POST \
  "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=$CLIENT_ID" \
  -d "client_secret=$CLIENT_SECRET" \
  -d "username=$USERNAME" \
  -d "password=$PASSWORD" \
  -d "grant_type=password")

# Check if jq is available
if ! command -v jq &> /dev/null; then
    echo -e "${YELLOW}Warning: jq not installed, showing raw response${NC}"
    echo "$RESPONSE"
    exit 0
fi

# Parse token
ACCESS_TOKEN=$(echo "$RESPONSE" | jq -r '.access_token')

if [ "$ACCESS_TOKEN" == "null" ] || [ -z "$ACCESS_TOKEN" ]; then
    echo -e "${YELLOW}Error: Failed to get token${NC}"
    echo "$RESPONSE" | jq .
    exit 1
fi

EXPIRES_IN=$(echo "$RESPONSE" | jq -r '.expires_in')

echo -e "${GREEN}✓ Token obtained successfully!${NC}"
echo ""
echo "Token expires in: ${EXPIRES_IN}s ($(($EXPIRES_IN / 60)) minutes)"
echo ""
echo "=========== ACCESS TOKEN ==========="
echo "$ACCESS_TOKEN"
echo "===================================="
echo ""
echo -e "${BLUE}Usage Examples:${NC}"
echo ""
echo "1. Set token as environment variable:"
echo "   export TOKEN=\"$ACCESS_TOKEN\""
echo ""
echo "2. Call admin endpoint:"
echo "   curl -H \"Authorization: Bearer \$TOKEN\" http://localhost:8082/api/admin/stats"
echo ""
echo "3. Clear cache:"
echo "   curl -X DELETE -H \"Authorization: Bearer \$TOKEN\" http://localhost:8082/api/admin/cache/all"
echo ""

# Optionally save to file
TOKEN_FILE=".token"
echo "$ACCESS_TOKEN" > "$TOKEN_FILE"
echo -e "${GREEN}Token saved to: $TOKEN_FILE${NC}"
echo "You can use: export TOKEN=\$(cat $TOKEN_FILE)"
