# Rate Limiter as a Service (RLaaS) — Run & Test Guide

## What This Service Does

RLaaS is a **centralized, distributed rate limiting service**. Instead of each microservice implementing its own rate limiting logic, they call this service's API to ask:

> _"Should I allow this request for user X / IP Y / endpoint Z?"_

RLaaS evaluates the request against configured **policies** and returns an allow/deny decision.

---

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Java | 17+ | Runtime (Spring Boot 3.x requires Java 17 minimum) |
| Maven | 3.8+ | Build tool |
| Redis | Any | Required for Redis-backed limiters (optional for in-memory mode) |
| Docker | Any | Required only if running Redis via Docker |

Check your versions:
```bash
java -version
mvn -version
redis-cli --version
```

---

## Running the Service

### Option A — In-Memory Mode (No Redis needed)

The default `application.yml` uses `default-backend: IN_MEMORY`. Just start the app:

```bash
cd rate-limitter-java
mvn spring-boot:run
```

The service starts at **http://localhost:8080**.

> **Note:** In-memory mode loses all rate limit state on restart. Use for local development only.

---

### Option B — Redis-Backed Mode

**1. Start Redis:**

```bash
# Using Docker (recommended)
docker run -d -p 6379:6379 --name rlaas-redis redis:alpine

# Or if Redis is installed locally
redis-server
```

**2. Set backend to Redis in `application.yml`:**

```yaml
rlaas:
  default-backend: REDIS   # Change from IN_MEMORY to REDIS
```

**3. Start the service:**

```bash
mvn spring-boot:run
```

**Verify Redis connection:**

```bash
redis-cli ping    # Should return: PONG
```

---

## Seeded Data (Auto-loaded on Startup)

Three default policies are loaded automatically when the database is empty:

| Policy Name | Key Type | Pattern | Algorithm | Limit |
|---|---|---|---|---|
| `global-ip-limit` | IP | `*` (any IP) | Token Bucket | 10 req / burst, 1 req/sec refill |
| `premium-users` | USER | `premium_*` | Fixed Window | 1000 req / 60 sec |
| `expensive-api-throttle` | API | `/api/v1/expensive-report` | Leaky Bucket | 5 req / 60 sec |

---

## API Reference

### 1. Policy Management (`/api/v1/admin/policies`)

#### List all policies
```bash
curl http://localhost:8080/api/v1/admin/policies
```

#### Get a single policy
```bash
curl http://localhost:8080/api/v1/admin/policies/1
```

#### Create a policy
```bash
curl -X POST http://localhost:8080/api/v1/admin/policies \
  -H "Content-Type: application/json" \
  -d '{
    "name": "free-tier-users",
    "keyType": "USER",
    "keyPattern": "free_*",
    "algorithmType": "FIXED_WINDOW",
    "maxRequests": 100,
    "windowSizeMs": 60000,
    "useRedis": false,
    "enabled": true,
    "description": "Free tier users get 100 requests per minute"
  }'
```

#### Update a policy
```bash
curl -X PUT http://localhost:8080/api/v1/admin/policies/1 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "global-ip-limit",
    "keyType": "IP",
    "keyPattern": "*",
    "algorithmType": "TOKEN_BUCKET",
    "maxRequests": 20,
    "windowSizeMs": 10000,
    "refillRate": 2.0,
    "bucketSize": 20,
    "useRedis": false,
    "enabled": true,
    "description": "Updated global IP limit"
  }'
```

#### Delete a policy
```bash
curl -X DELETE http://localhost:8080/api/v1/admin/policies/1
```

---

### 2. Rate Limit Checks (`/api/v1/ratelimit`)

This is the **core business API**. Other services call these endpoints to check/enforce rate limits.

#### Check if a request is allowed (and consume a slot)
```bash
# Check for a USER key
curl -X POST http://localhost:8080/api/v1/ratelimit/check \
  -H "Content-Type: application/json" \
  -d '{ "keyType": "USER", "key": "premium_123" }'

# Check for an IP key
curl -X POST http://localhost:8080/api/v1/ratelimit/check \
  -H "Content-Type: application/json" \
  -d '{ "keyType": "IP", "key": "192.168.1.1" }'

# Check for an API path key
curl -X POST http://localhost:8080/api/v1/ratelimit/check \
  -H "Content-Type: application/json" \
  -d '{ "keyType": "API", "key": "/api/v1/expensive-report" }'
```

**Allowed response (200):**
```json
{
  "success": true,
  "message": "Request allowed",
  "data": {
    "allowed": true,
    "limit": 1000,
    "remaining": 999,
    "resetAtMs": 1711234560000,
    "algorithmName": "Fixed Window"
  }
}
```

**Denied response (429):**
```json
{
  "success": false,
  "message": "Rate limit exceeded"
}
```

#### Get current state of a key (read-only, does NOT consume a slot)
```bash
curl "http://localhost:8080/api/v1/ratelimit/status?keyType=USER&key=premium_123"
curl "http://localhost:8080/api/v1/ratelimit/status?keyType=IP&key=192.168.1.1"
```

#### Reset rate limit state for a key
```bash
curl -X DELETE "http://localhost:8080/api/v1/ratelimit/reset?keyType=USER&key=premium_123"
```

---

## Testing Scenarios

### Scenario 1: Hit the IP rate limit

The default `global-ip-limit` policy allows a burst of 10 requests then refills at 1/sec.
Run this in your terminal to hit the limit:

```bash
for i in {1..15}; do
  echo "Request $i:"
  curl -s -X POST http://localhost:8080/api/v1/ratelimit/check \
    -H "Content-Type: application/json" \
    -d '{ "keyType": "IP", "key": "192.168.1.100" }' | python3 -m json.tool
  echo "---"
done
```

You should see `"allowed": true` for the first 10, then `429` / `"allowed": false` for the rest.

---

### Scenario 2: Premium users get higher limits

```bash
# premium_123 gets 1000 req/min — should always be allowed
curl -X POST http://localhost:8080/api/v1/ratelimit/check \
  -H "Content-Type: application/json" \
  -d '{ "keyType": "USER", "key": "premium_123" }'

# no_policy_user has no matching policy — allowed by default
curl -X POST http://localhost:8080/api/v1/ratelimit/check \
  -H "Content-Type: application/json" \
  -d '{ "keyType": "USER", "key": "no_policy_user" }'
```

---

### Scenario 3: Expensive API endpoint throttle

```bash
for i in {1..8}; do
  echo "Call $i to expensive endpoint:"
  curl -s -X POST http://localhost:8080/api/v1/ratelimit/check \
    -H "Content-Type: application/json" \
    -d '{ "keyType": "API", "key": "/api/v1/expensive-report" }'
  echo ""
done
```

First 5 allowed, then denied.

---

### Scenario 4: Reset and retry

```bash
# Hit the limit
for i in {1..12}; do
  curl -s -X POST http://localhost:8080/api/v1/ratelimit/check \
    -H "Content-Type: application/json" \
    -d '{ "keyType": "IP", "key": "10.0.0.1" }' > /dev/null
done

# Check current state (read-only)
curl "http://localhost:8080/api/v1/ratelimit/status?keyType=IP&key=10.0.0.1"

# Reset the limit
curl -X DELETE "http://localhost:8080/api/v1/ratelimit/reset?keyType=IP&key=10.0.0.1"

# Now allowed again
curl -X POST http://localhost:8080/api/v1/ratelimit/check \
  -H "Content-Type: application/json" \
  -d '{ "keyType": "IP", "key": "10.0.0.1" }'
```

---

## Health & Observability

```bash
# Is the service alive?
curl http://localhost:8080/actuator/health

# App metrics (JVM, HTTP requests, etc.)
curl http://localhost:8080/actuator/metrics

# See all active configuration
curl http://localhost:8080/actuator/env
```

---

## Database Console (Dev Only)

Browse the H2 in-memory database while the app is running:

1. Open http://localhost:8080/h2-console
2. JDBC URL: `jdbc:h2:mem:rlaasdb`
3. Username: `sa`
4. Password: _(leave blank)_

Useful SQL:
```sql
-- See all policies
SELECT * FROM rate_limit_policy;

-- See enabled policies only
SELECT * FROM rate_limit_policy WHERE enabled = true;
```

---

## Available Algorithm Types

Use these values in the `algorithmType` field when creating policies:

| Value | Description | Required Fields |
|---|---|---|
| `FIXED_WINDOW` | Fixed time window counter | `maxRequests`, `windowSizeMs` |
| `SLIDING_WINDOW_LOG` | Precise per-request timestamp log | `maxRequests`, `windowSizeMs` |
| `SLIDING_WINDOW_COUNTER` | Approximate sliding window | `maxRequests`, `windowSizeMs` |
| `TOKEN_BUCKET` | Burst-tolerant, smooth refill | `bucketSize`, `refillRate`, `windowSizeMs` |
| `LEAKY_BUCKET` | Enforces strict output rate | `bucketSize`, `refillRate`, `maxRequests`, `windowSizeMs` |

## Key Types

| Value | Meaning | Example key |
|---|---|---|
| `IP` | Client IP address | `192.168.1.1` |
| `USER` | User identifier | `premium_123`, `free_456` |
| `API` | API endpoint path | `/api/v1/expensive-report` |

> **Pattern matching**: `*` in `keyPattern` is a wildcard. `premium_*` matches `premium_123`, `premium_abc`, etc.

---

## Build a Production JAR

```bash
mvn clean package -DskipTests
java -jar target/rlaas-1.0.0.jar
```
