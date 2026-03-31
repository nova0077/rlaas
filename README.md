# Rate Limiting as a Service (RLaaS)

A robust, distributed rate limiter service built with Java and Spring Boot. This service acts as a centralized traffic management layer to protect your applications and APIs from being overwhelmed by too many requests.

Instead of duplicating rate-limiting logic across all your microservices, you can deploy this service independently. Your other services act as clients that simply ask RLaaS: *"Is this request allowed to proceed?"*

## Supported Algorithms

The service supports several enterprise-grade rate-limiting algorithms, giving you the flexibility to choose the right strategy for your traffic patterns:
- **Fixed Window**
- **Token Bucket**
- **Leaky Bucket**
- **Sliding Window Log**
- **Sliding Window Counter**

---

## How to Integrate

Integrating this service into your existing projects is straightforward. We highly recommend a **"Fail-Open"** approach:

1. **Check before processing**: In your application code (e.g., a Controller or Middleware), call the RLaaS `/api/v1/ratelimit/check` endpoint before executing the business logic.
2. **Handle the Decision**: 
   - If `allowed: true`, proceed with your business logic.
   - If `allowed: false`, immediately return a `429 Too Many Requests` HTTP status, ideally passing along the `Retry-After` header provided by RLaaS.
3. **Fail Open**: If RLaaS is unreachable or the network request times out, log the error but **allow the user request to proceed**. Rate limiting is a protection layer and an outage in RLaaS shouldn't break your core application.

---

## How to Create a Policy

You can define rules to rate-limit traffic by `IP`, `USER`, or `API`. Use the Admin API to register policies dynamically.

### Best Practice: API Key Naming
When rate-limiting specific endpoints, use a namespaced format to prevent collisions between different applications. We recommend using the format: `domain:REQ-type:API path`

**Example**: Limiting `POST` requests to `/api/entries` on the `calorie-tracker` domain to 2 requests per minute.

```bash
curl -X POST http://localhost:8080/api/v1/admin/policies \
  -H "Content-Type: application/json" \
  -d '{
    "name": "calorie-tracker-creation-limit",
    "keyType": "API",
    "keyPattern": "calorie-tracker:POST:/api/entries",
    "algorithmType": "FIXED_WINDOW",
    "maxRequests": 2,
    "windowSizeMs": 60000,
    "useRedis": false,
    "enabled": true,
    "description": "Limit food entry creation"
  }'
```

When your client app verifies the rate limit, it just sends the exact same structured string as the `key`.

---

## Future Plans

We have a clear roadmap to mature this service for high-scale production usage:

- **Multi-layer Rate Limiting**: Support evaluating multiple conditions on a single request (e.g., checking both the `API` endpoint limit AND the `End User IP` limit simultaneously).
- **Docker Containerization**: Provide a `Dockerfile` and `docker-compose` setup to make deploying and scaling the service seamless across any environment.
- **Improved Policy Fetching Logic**: Optimize how the service matches an incoming request against hundreds of active policies for better P99 latency.
- **Domain-wise Isolation**: Add multi-tenancy structures so different teams or domains can safely manage their rate limits in isolation without affecting others.
- **Redis Testing Strategy**: Implement comprehensive integration tests specifically targeting the Redis-backed distributed modes to ensure cluster consistency.
