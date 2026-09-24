# Backend Interview Questions — Java, Spring Boot, Microservices

Answers taken from this codebase. Reference: [backend-guide.md](backend-guide.md).
Format: the question, a short spoken answer, and the file to point at.

---

## A. Architecture and the gateway

**1. Walk me through what happens to one authenticated request.**
The browser calls `/api/v1/bookings` with a Bearer token. The frontend's nginx proxies it to the
gateway keeping the original `Host`. The gateway resolves the tenant from that host, strips any
client-supplied `X-Tenant-Id` and sets the real one, blocks internal-only paths, validates the JWT
signature and expiry, checks the token's tenant claim matches the resolved tenant, injects
`X-User-Id`, `X-User-Email`, `X-User-Role`, `X-User-Name`, applies a Redis rate limit, then routes to
`lb://booking-service` resolved through Eureka. booking-service reads the headers, switches its JPA
connection to that tenant's schema, and does its work.
→ `GatewayConfig`, `JwtAuthGatewayFilterFactory`, `TenantResolutionGlobalFilter`.

**2. Why validate the JWT at the gateway instead of in every service?**
One implementation, one place to rotate the secret, and services stay free of security plumbing. The
trade-off is that services now trust headers, so those paths must never be reachable directly. Here
they are only exposed on the Docker network, and internal-only prefixes are blocked at the edge by
`InternalOnlyPathFilter`. In a stronger setup I would add mTLS or a signed internal token between
gateway and services.

**3. Route order matters in your gateway. Why?**
Spring Cloud Gateway matches routes in registration order, so a broad path defined early swallows
specific ones. `/api/v1/admin/**` belongs to admin-service, but `/api/v1/admin/reviews/**`,
`/admin/announcements/**` and `/admin/support/**` belong to other services — they are registered
first, and the admin catch-all last. Same for `/api/v1/bookings/*/reviews` before `/bookings/**`.

**4. What does `lb://` mean?**
It is a load-balanced URI: resolve this service name through the discovery client (Eureka) and pick
an instance. Scaling a service to three containers needs no gateway change.

**5. How does rate limiting work here?**
`RedisRateLimiter(100, 200, 1)` — token bucket in Redis: 100 tokens per second replenish, burst 200,
one token per request. Redis is shared, so the limit holds across gateway instances.

**6. Circuit breaker — where and why?**
Resilience4J with a `FallbackController` on the gateway, so a dead downstream returns a fast
fallback instead of piling up threads. The gateway is reactive (WebFlux), so slow calls hold no
thread, but the circuit breaker still stops a failing dependency from becoming everyone's problem.

---

## B. Data and persistence

**7. Database per service — why, and what does it cost?**
Each service owns its schema so it can evolve without coordinating migrations, and nobody can reach
into another's tables. The cost is that joins become API calls or events: the admin dashboard cannot
`JOIN` users and bookings, so admin-service aggregates them through Feign, and cross-service
consistency becomes eventual.

**8. How is multi-tenancy implemented?**
Schema per tenant. `tenant-common` reads `X-Tenant-Id` and routes the connection to
`<service_db>_<tenantKey>`. At boot, and whenever a `tenant.events` message arrives, each service
creates and Flyway-migrates the schema for every tenant, so onboarding needs no restart.

**9. Why schema-per-tenant rather than a `tenant_id` column?**
Isolation you cannot forget. With a discriminator column, one missing `WHERE tenant_id = ?` leaks
another tenant's rows; with separate schemas the rows are not in the connection at all. The cost is
migration fan-out — N tenants × M services schemas to keep in step — and connection pool pressure.
At thousands of tenants I would move to row-level with a mandatory Hibernate filter, or shard.

**10. How do you version the database?**
Flyway per service, `src/main/resources/db/migration/V<n>__*.sql`, run at startup. Applied
migrations are immutable — a change means a new version file. That is how the `booking_code` fix
shipped: `V2` widened the column rather than editing `V1`.

**11. Tell me a Hibernate gotcha you hit.**
Enum fields. With Hibernate 6 on MySQL, `@Enumerated(EnumType.STRING)` alone makes the schema
validator expect a native `ENUM(...)` column, which clashes with the `VARCHAR` Flyway created and
the app fails at startup. Pairing it with `@JdbcTypeCode(SqlTypes.VARCHAR)` fixes it. It is a
convention in the codebase now.

**12. Transactions — where are your boundaries?**
`@Transactional` on service-layer methods, controllers stay thin. Within a service a state change
and its ledger line commit together — escrow release credits the wallet and writes the transaction
row in one transaction. Across services there is no distributed transaction: the flow is an event
plus a compensating action.

**13. How do you paginate?**
Spring Data `Pageable`, e.g. `/bookings/customer?page=0&size=20`. It keeps the response bounded and
pushes the limit into SQL rather than trimming in Java.

---

## C. Messaging and consistency

**14. Which calls are synchronous and which are events?**
Synchronous (Feign) when the caller cannot proceed without the answer: review-service must know a
booking is COMPLETED before accepting a review. Events (Kafka) when the caller should not wait:
receipts, notifications, audit rows, search indexing, tenant provisioning.

**15. Kafka delivers at least once. How do you cope?**
Consumers are idempotent. `PaymentEventConsumer` returns early if the booking is already PAID, so a
redelivered `payment.completed` does not send a second receipt. Escrow guards on state: a RELEASED
hold cannot be released again. The general rule: make the consumer's effect a function of state, not
of the number of deliveries.

**16. What if a consumer throws?**
Kafka redelivers the same offset and can park the whole consumer group on a poison message. The
booking consumer catches and logs instead of rethrowing, which keeps the group moving; the honest
trade-off is a dropped event, and the right fix is a dead-letter topic with retries.

**17. Your dual-write problem — do you have one?**
Yes, and I would name it before the interviewer does. A service commits to MySQL and then publishes
to Kafka; a crash in between leaves the database changed and no event sent. The fix is the
transactional outbox: write the event to an outbox table inside the same transaction, and let a
relay publish it. Worth doing first for the payment and escrow paths.

**18. How would you make booking + payment a saga?**
Booking created (PENDING) → payment requested → on `payment.completed`, booking becomes PAID; on
failure or timeout, a compensating event cancels the booking and releases the slot. No 2PC; each
step is locally transactional with a compensation. Today the happy path is event-driven, but the
compensations are thin — I would add a timeout sweeper.

---

## D. Security

**19. How are passwords and tokens handled?**
BCrypt hashes, never plaintext. JWTs signed with a shared `JWT_SECRET` (auth-service signs, the
gateway verifies) carrying subject, email, role, name and tenant. Short-lived access token plus a
refresh token; failed-login counters lock an account.

**20. Biggest weakness of JWT here?**
Revocation. A stolen token is valid until it expires. Mitigations: keep access tokens short, rotate
refresh tokens, and — if needed — a Redis deny-list of revoked token ids checked by the gateway,
which trades a little statelessness for real logout.

**21. How is RBAC enforced?**
The role rides in the JWT and arrives as `X-User-Role`. Services check it explicitly, e.g. admin
endpoints require `SUPER_ADMIN`, `ADMIN`, `SUB_ADMIN` or `REGIONAL_ADMIN`. A convention worth
mentioning: role headers are declared `required = false` and null-checked, because a missing
*required* header throws before the handler runs and surfaces as a generic 500 instead of a 403.

**22. Payment security?**
The browser never decides that a payment succeeded. Razorpay returns an id and signature, and
payment-service verifies the HMAC with the key secret server-side; Razorpay also calls a webhook.
Only the *key id* is exposed to the browser — the secret lives in the container environment. The
escrow invariant is the same idea: a hold becomes HELD only because its linked payment completed at
the PSP, so nobody can talk the platform into releasing money that never arrived.

**23. What is the audit log for and how is it trustworthy?**
Compliance and dispute resolution. Producers publish `audit.events` via `audit-common`;
audit-service appends rows whose hash chains to the previous row, so editing history breaks the
chain. An integrity endpoint re-walks it and reports the break. Append-only: no update, no delete.

---

## E. Spring specifics they like to probe

**24. Config Server — what problem does it solve?**
One place for every service's configuration, environment by environment, so a change does not mean
editing 16 jars. Here it runs in native mode serving `config-repo/*.yml` from its classpath; in
production it would be a Git backend, giving versioned, reviewable config.

**25. How does a service find the Config Server before it has any config?**
Bootstrap configuration — the config client's address is baked in as an environment variable, and it
fetches everything else. That is why Config Server has a health check that gates every other
container.

**26. Feign vs `RestTemplate` vs `WebClient`?**
Feign for declarative, interface-based calls with Eureka resolution and Resilience4J — used for the
service-to-service calls here. `WebClient` (reactive) on the gateway, because WebFlux is
non-blocking. `RestTemplate` is in maintenance mode.

**27. How do you handle errors consistently?**
Service methods throw `IllegalArgumentException` for client errors; each service's
`@RestControllerAdvice` `GlobalExceptionHandler` maps that to 400 with a standard body and unexpected
exceptions to 500. The frontend has a matching `apiError` helper.

**28. MapStruct, Lombok — why?**
MapStruct generates entity↔DTO mappers at compile time (no reflection, compile errors when a field
is missed). Lombok removes boilerplate. The important part is *why DTOs at all*: never serialise
entities — it leaks columns and couples the API to the schema.

**29. How do you test and what is missing?**
Unit tests on service classes, and the flows in `MODULE_STATUS.md` were verified live against the
real stack with real JWTs — including negative paths like non-party 403s and duplicate rejections.
Missing: broad integration tests with Testcontainers and contract tests between services. I would
add Testcontainers first, because the bugs found here were integration bugs, not unit bugs.

**30. How do you debug a request across 16 services?**
Zipkin tracing with a propagated trace id, plus per-service logs (`docker compose logs -f`), Eureka
to confirm registration and actuator health endpoints. For a slow endpoint: trace first to find
which hop is slow, then look at that service's SQL.

---

## F. Curveballs

**31. "This could be a monolith."**
Agreed, for this traffic. The split buys independent deploys and clear data ownership, and costs
operational complexity and eventual consistency. If I were starting commercially, I would build a
modular monolith with the same boundaries and split out the parts that actually need it — payments
and search first.

**32. "What would fall over first under load?"**
MySQL, because every service shares one instance here, and per-tenant schemas multiply the
connection pools. Then Elasticsearch reindexing, which is a full sweep rather than incremental.
Fixes: read replicas, connection pool budgets per service, incremental indexing off the event
stream, and caching the hot read paths (catalogue and UI-config) in Redis.

**33. "Show me something you deliberately did *not* build."**
Withdrawals. Money reaches wallets but cannot leave, because a payout needs PSP payout APIs plus a
KYC-approved gate and a reconciliation story. Half-building that would have been worse than leaving
a clean boundary: `held_balance` and the hold/release methods exist for the day it lands.
