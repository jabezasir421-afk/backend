# Rate Limiting Architectural Strategy & Multi-Instance Scaling

## 1. Executive Summary

BlueCollar protects its public and authenticated REST endpoints against brute force, denial of service (DoS), and abusive consumption through API rate limiting. This document details the architectural rationale for selecting local in-memory (Caffeine) rate limiting versus a distributed shared store (Redis), how multi-instance deployments are handled, and the migration blueprint for high-scale horizontal cluster deployments.

---

## 2. In-Memory (Caffeine) vs Distributed (Redis) Analysis

| Evaluation Metric | In-Memory (Caffeine Cache) | Distributed Shared Store (Redis) |
| :--- | :--- | :--- |
| **Execution Latency** | **Sub-microsecond (< 1 µs)** directly in JVM heap | **1 – 3 ms** (network roundtrip + Redis command serialization) |
| **Operational Complexity** | **Zero external dependencies**; no Redis cluster to deploy, monitor, or patch | Requires highly available Redis Sentinel / Cluster setup with failover |
| **Failure Modes** | Self-contained, cannot suffer network partition or disconnection | If Redis is degraded or unreachable, must decide between fail-open (security risk) or fail-closed (system outage) |
| **Memory Management** | Strictly bounded via `maximumSize` LRU eviction and TTL cleanup | Bounded by Redis `maxmemory` and volatile-lru policy |
| **Multi-Instance Quota** | Quota is enforced per application replica ($N \times \text{limit}$) | Quota is enforced globally across all replicas |
| **IP Spoofing Protection** | Supported via trusted-proxy validation | Supported via trusted-proxy validation |

---

## 3. Decision Rationale

### Current Architecture: In-Memory Bounded Caffeine with Trusted Proxy Validation
For single-instance and initial multi-instance deployments behind standard reverse proxies (e.g. AWS ALB, Nginx, Cloudflare), **in-memory Caffeine rate limiting is the recommended strategy**:
1. **Low Latency & High Throughput**: Rate limiting executes on every single HTTP request. An in-memory atomic check introduces negligible overhead (< 1µs) compared to remote Redis queries.
2. **Resilience**: Rate limiting never fails due to database or cache connectivity drops.
3. **Memory Bounding**: Configured with a hard upper bound (`max-capacity: 100,000` keys) and time-to-live expiration (`expireAfterWrite: 2 * windowMs`). Even under distributed port scanning or IP randomizing DDoS attacks, memory cannot exceed the specified ceiling.
4. **Edge Layer Synergy**: In production, volume-based DDoS protection (e.g., 10,000 req/min/IP) is best terminated at the Cloudflare / AWS WAF edge, while application-layer rate limiting (e.g., 20 req/min for auth or search) is handled within the application.

---

## 4. Multi-Instance Horizontal Deployment Blueprint

When horizontally autoscaling to multiple application instances ($N > 1$) where a **strict global quota** is required across all nodes without relying on edge-level rate limiting, the system transitions to Redis.

### Architectural Blueprint for Redis Rate Limiting

```
[ Incoming Requests ]
         │
         ▼
[ Ingress / Reverse Proxy (ALB / Nginx) ]
         │ (Appends X-Forwarded-For)
         ├───────────────────────┬───────────────────────┐
         ▼                       ▼                       ▼
  [ Node 1 (Spring) ]     [ Node 2 (Spring) ]     [ Node 3 (Spring) ]
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 ▼
                     [ Redis Cluster / Sentinel ]
                     (Atomic Sliding Window Lua)
```

### Redis Sliding Window Token-Bucket Script (Lua)

```lua
-- KEYS[1]: Rate limit key (e.g., ratelimit:{client_ip})
-- ARGV[1]: Current timestamp (millis)
-- ARGV[2]: Window size (millis)
-- ARGV[3]: Max allowed requests
-- ARGV[4]: Cost (typically 1)

local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
local cost = tonumber(ARGV[4])
local clear_before = now - window

-- Remove timestamps outside the sliding window
redis.call('ZREMRANGEBYSCORE', key, 0, clear_before)

-- Count current requests in window
local current_requests = redis.call('ZCARD', key)

if current_requests + cost <= limit then
    -- Record this request with member = timestamp + random UUID
    redis.call('ZADD', key, now, now .. '-' .. math.random(100000, 999999))
    redis.call('PEXPIRE', key, window)
    return 1 -- Allowed
else
    return 0 -- Denied
end
```

### Redis Configuration Integration

To activate Redis-backed distributed rate limiting when deploying multi-instance:
1. Set `bluecollar.security.rate-limit.type=redis` in `application-prod.yml`.
2. Provide Spring Data Redis with `spring.data.redis.host` and `spring.data.redis.port`.
3. Provide fallback: If Redis is temporarily unavailable, fall back to in-memory Caffeine rate limiting rather than rejecting legitimate client traffic.

---

## 5. Security & Trusted Proxy Configuration

To prevent IP spoofing where an attacker sends a malicious `X-Forwarded-For: 1.1.1.1` header:
- `RateLimitingFilter` checks `request.getRemoteAddr()`.
- Forwarded headers (`X-Forwarded-For`, `X-Real-IP`) are **only parsed if `remoteAddr` is in the trusted proxies set** (`127.0.0.1`, RFC 1918 subnets, or VPC CIDR).
- If the request originates from an untrusted remote IP, forwarded headers are ignored and the raw IP is used for rate limiting.
