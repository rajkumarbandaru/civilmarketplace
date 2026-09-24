# Civil Engineering Marketplace — Interview Guide

Everything needed to explain this project in an interview: what it is, how it is built, how the
flows work end to end, which concepts it demonstrates and where, real bugs fixed (ready-made STAR
stories), and practice questions answered from the real code.

> Source repo: `~/RAJKUMAR`. Java 21 · Spring Boot 3.2 · Spring Cloud 2023.0.1 · React 18 + Vite ·
> MySQL · Redis · Kafka · Elasticsearch · Docker Compose.
> Tool-wise deep dives: [UI](ui/ui-guide.md) · [Backend](backend/backend-guide.md) ·
> [Docker](docker/docker-guide.md) · [Architecture](overview/architecture.md).
> Tool-wise question banks: [UI](ui/ui-interview.md) · [Backend](backend/backend-interview.md) ·
> [Docker](docker/docker-interview.md) · [System design](interview/system-design.md) ·
> [STAR stories](interview/star-stories.md) · [Rapid fire](interview/rapid-fire.md).

---

## 1. Your 60-second pitch

> "I built a **multi-tenant civil engineering services marketplace** — think Urban Company for
> construction professionals. It is **16 Spring Boot microservices on Java 21** behind a **Spring
> Cloud Gateway**, with a **React 18 + TypeScript** front end, all orchestrated with **Docker
> Compose**.
>
> Customers browse a service catalogue, book an engineer, architect or contractor, pay through
> **Razorpay with an escrow hold**, track the worker live, chat on the booking, and review them
> afterwards. Admins get a console for users, KYC, categories, revenue, content, announcements,
> support tickets and a tamper-evident audit log.
>
> Three things I'd call out architecturally. First, **the gateway owns authentication** — it
> validates the JWT and injects `X-User-Id` / `X-User-Role` headers, so the 15 downstream services
> never parse a token. Second, it is **multi-tenant by schema**: the tenant comes from the request
> subdomain, is cross-checked against a claim in the JWT, and every service routes its JPA
> connection to `<database>_<tenantKey>`; onboarding a tenant publishes a Kafka event and every
> service creates and Flyway-migrates that schema live, with no restart. Third, **money is
> event-driven but idempotent** — payment confirmation is a Kafka event, and the consumer guards
> against Kafka's at-least-once redelivery so a customer is never charged or notified twice.
>
> Along the way I found and fixed some real production-class bugs: a column too narrow for the
> booking code that broke *every* booking, a Spring Security misconfiguration that stopped
> auth-service from starting at all, and a `Map.of` NPE that turned every failed payment into a 500."

---

## 2. What the project is, in numbers

| Item | Value |
|---|---|
| Backend | **16 deployable Spring Boot services** + 3 shared libraries (`audit-common`, `tenant-common`, `web-common`) |
| Platform services | Config Server (8888), Eureka (8761), API Gateway (8080) |
| Business services | auth, user, booking, payment, notification, admin, project, review, search, messaging, support, audit, tenant |
| Frontend | 1 React 18 + TypeScript SPA (Vite, MUI, Tailwind, Redux Toolkit, React Query), installable as a PWA |
| Databases | **One MySQL database per service**, and **one schema per tenant** inside each |
| Messaging | Kafka topics: `user.registered`, `otp.sent`, `payment.created/completed/refunded`, `booking.paid/arriving/completed`, `message.sent`, `escrow.held/released/disputed`, `audit.events`, `tenant.events` |
| Other infra | Redis (OTP, sessions, rate limit), Elasticsearch (search), RabbitMQ, Zipkin, Prometheus, Grafana |
| Orchestration | Docker Compose, 25 containers, one bridge network, health-gated startup |
| Roles | SUPER_ADMIN, ADMIN, SUB_ADMIN, REGIONAL_ADMIN, customer, worker, contractor, supplier, surveyor, architect, labour |

---

## 3. The architecture in one breath

```
Browser → frontend nginx (:3000, serves the bundle, proxies /api)
        → API Gateway (:8080)
            1. resolve tenant from Host subdomain → X-Tenant-Id (client's own header is stripped)
            2. block internal-only paths
            3. validate JWT, check its tenant claim, inject X-User-Id / Email / Role / Name
            4. route by path to lb://<service>, resolved through Eureka
        → microservice (config from Config Server, registered in Eureka)
            → its own MySQL database, schema <db>_<tenant>
            → Kafka for anything asynchronous, Feign for anything synchronous
```

Say this sentence if you say nothing else: **"Authentication and tenancy are solved once, at the
edge; every service downstream is simple because it only reads headers."**

---

## 4. Concept → where it lives in this project

Interviewers ask "have you used X?". Here is X, with a file to point at.

| Concept | Where in this project |
|---|---|
| API Gateway routing | `api-gateway/.../GatewayConfig.java` — ~20 routes, ordered so specific paths beat catch-alls |
| Custom gateway filter | `JwtAuthGatewayFilterFactory` (per-route), `TenantResolutionGlobalFilter` + `InternalOnlyPathFilter` (global) |
| Service discovery | Eureka; every route is `lb://service-name`, Feign clients resolve the same way |
| Centralised config | Config Server in native mode, serving `config-repo/<service>.yml` from its classpath |
| Circuit breaker / fallback | Resilience4J + `FallbackController` on the gateway |
| Rate limiting | `RedisRateLimiter(100, 200, 1)` — replenish 100/s, burst 200 |
| Stateless auth | JWT (JJWT 0.12) signed with a shared `JWT_SECRET`; refresh tokens; BCrypt; account lockout |
| RBAC | Role in the JWT → `X-User-Role` header → checked in each service |
| Multi-tenancy | `tenant-common`: schema-per-tenant routing + Flyway per schema; tenant from subdomain |
| Event-driven | Kafka producers/consumers listed above; `@KafkaListener` groups per service |
| Idempotency | `PaymentEventConsumer` ignores a second `payment.completed` for an already-PAID booking |
| Sync service-to-service | OpenFeign: review→booking, project→booking+payment, admin→auth/user/booking/payment |
| Database migrations | Flyway per service, `V1__*.sql`, `V2__*.sql`; never edit an applied migration |
| Caching | Redis for OTP and sessions |
| Full-text search | Elasticsearch indices `profiles` and `services`, fuzzy + filters + sorts |
| Payments | Razorpay order → signature verification → escrow hold → commission → wallet ledger |
| Audit / compliance | `audit-common` → Kafka → `audit-service`, append-only, SHA hash-chained, integrity endpoint |
| Observability | Actuator + Prometheus + Grafana + Zipkin tracing |
| Frontend state | Redux Toolkit for session/client state, React Query for server state |
| Runtime theming | `admin-service` UI-config → `GET /ui-config/me` → MUI theme rebuilt at runtime |
| Containerisation | Per-service Dockerfile (JRE 21 + jar), multi-stage build for the React app, Compose overlay for memory caps |

---

## 5. The 3-minute walkthrough (rehearse out loud)

1. **"A customer opens `acme.localhost:3000`."** nginx serves the React bundle; the page calls
   `/api/v1/tenant-resolution/...`, `/content/site` and `/catalogue` — all public routes — so the
   landing page shows that tenant's branding, editable copy and services before any login.
2. **"They register and log in."** `POST /api/v1/auth/login` → the gateway resolves the tenant and
   forwards; auth-service checks BCrypt + lockout in `civil_engineer_auth_acme`, signs a JWT
   carrying user id, role and tenant. The UI stores it in **sessionStorage** (per tab, so two
   accounts can be open side by side) and immediately calls `/ui-config/me` to theme itself.
3. **"They book a service."** `POST /api/v1/bookings` with the Bearer token. The gateway validates
   it, injects `X-User-Id`, and routes to booking-service, which writes to its own schema.
4. **"They pay."** payment-service creates a Razorpay order, the browser opens Checkout, and the
   callback is verified **server-side by HMAC signature** — never trusting the client. The payment
   row goes COMPLETED and publishes `payment.completed`.
5. **"Two services react to that one event."** booking-service marks the booking PAID (ignoring
   duplicate deliveries), then publishes `booking.paid`; notification-service sends the receipt.
   Nobody blocked on anybody.
6. **"The worker travels."** Location updates compute an ETA; crossing the threshold publishes
   `booking.arriving` and the customer gets a notification.
7. **"Afterwards they review."** review-service makes a Feign call to booking-service to prove the
   booking is COMPLETED and the reviewer was a party to it, then recomputes the rolling rating.
8. **"Everything sensitive is audited."** Admin and money actions publish `audit.events`;
   audit-service appends a hash-chained row, and an integrity endpoint can prove nothing was edited.

---

## 6. Four questions you will definitely get

**"Why microservices and not a monolith?"**
Be honest: for this traffic a modular monolith would be cheaper to run. The split was chosen for
independent deploys, per-domain data ownership and to model a real marketplace org. The cost is
real and I can name it: cross-service consistency needs events and compensations, local dev needs
~12 containers, and a single user-facing screen can fan out to three services.

**"How do you keep one tenant from seeing another's data?"**
Four layers. The subdomain resolves the tenant at the gateway; any client-supplied `X-Tenant-Id` is
stripped; the JWT carries a tenant claim that must match the resolved tenant, so a token stolen from
one tenant is rejected at another; and the data itself is in a separate MySQL schema per tenant, so
a missing `WHERE tenant_id = ?` cannot leak rows — the wrong rows are not in the connection's schema
at all.

**"What happens if Kafka redelivers a payment event?"**
Kafka is at-least-once, so consumers must be idempotent. `PaymentEventConsumer` checks whether the
booking is already PAID and returns early, so no second receipt goes out. Escrow does the same with
state transitions: a hold already RELEASED cannot be released again.

**"Where is the weakest part of this design?"**
I'd name the **dual write**: a service commits to MySQL and then publishes to Kafka in the same
method. If the process dies between those two, the database moved and the event never went out. The
fix is the **transactional outbox** pattern — write the event to an outbox table in the same
transaction and have a relay publish it. That is the first thing I'd change.

---

## 7. STAR stories in one line each

Full versions with situation/task/action/result: [interview/star-stories.md](interview/star-stories.md).

1. **Every booking failed with a 500** — `booking_code` was `VARCHAR(20)`, the generator emitted 22
   characters. Widened the column via a new Flyway migration instead of changing the code format,
   because a lookup API depended on it.
2. **auth-service would not start** — `.oauth2Login()` was called unconditionally, but Spring only
   creates a `ClientRegistrationRepository` when a provider is configured. Made it conditional with
   `ObjectProvider`, so social login switches itself on when credentials appear.
3. **Every failed payment returned a 500** — the failure path built a `payment.created` event with
   `Map.of(...)`, which throws NPE on a null value, and the Razorpay order id is null exactly when
   the call failed. Switched to a `HashMap` that tolerates the null and carries the status.
4. **A latent time bomb in a repository** — `findByBookingId` returned `Optional`, but escrow made
   one booking carry several payments, so it would throw `IncorrectResultSizeDataAccessException`.
   Replaced with an ordered `findFirst...` query plus a status-scoped variant.
5. **A bare 403 on the login screen** — CORS used `setAllowedOrigins` with a wildcard, which Spring
   rejects when credentials are allowed, so every tenant subdomain failed at login with an empty
   body. Switched to `setAllowedOriginPatterns`.

---

## 8. Rapid-fire answers to have ready

- **JWT vs session?** Stateless, so any service instance can serve any request; the trade-off is
  revocation, which we soften with short-lived access tokens plus refresh tokens.
- **Where do you validate the token?** Only at the gateway. Services read `X-User-*` headers, and
  those paths are unreachable from outside the Docker network.
- **Why Feign *and* Kafka?** Feign when the caller needs an answer now (is this booking completed?);
  Kafka when it does not (send a receipt, index a profile, write an audit row).
- **How does a new service get discovered?** It registers with Eureka by
  `spring.application.name`, and the gateway routes `lb://that-name` without redeploying.
- **Why is the gateway's route order important?** `/api/v1/admin/**` would swallow
  `/api/v1/admin/reviews/**`; specific routes are registered first.
- **How are schema changes shipped?** Flyway, one file per version per service, run at boot for
  every tenant schema.
- **How do you run it?** `mvn clean package` then `docker compose up -d --build`; the lean overlay
  caps JVM heap so 16 services fit on a laptop.

---

## 9. How to demo it in 5 minutes

1. `docker compose ps` — show the container list; mention health-gated startup order.
2. http://localhost:8761 — Eureka, every service registered.
3. Log in as `superadmin@civileng.test` — show the admin console.
4. Change the platform theme, reload as a member — runtime theming from the API.
5. `docker compose logs -f notification-service` while triggering an OTP — show the Kafka consumer.
6. Open [`overview/action-flows.html`](overview/action-flows.html) — the end-to-end trace of the
   action you just performed.

---

## 10. Seven-day prep plan

| Day | Focus |
|---|---|
| 1 | This guide + [architecture](overview/architecture.md); draw the layer diagram from memory |
| 2 | [Backend guide](backend/backend-guide.md) + [backend questions](backend/backend-interview.md) |
| 3 | [Request flows](overview/request-flows.md) + [action flows](overview/action-flows.html); narrate login and payment out loud |
| 4 | [UI guide](ui/ui-guide.md) + [UI questions](ui/ui-interview.md) |
| 5 | [Docker guide](docker/docker-guide.md) + [how to run](docker/how-to-run.md); run the stack from scratch |
| 6 | [System design](interview/system-design.md) — practise the "how would you scale it" whiteboard |
| 7 | [STAR stories](interview/star-stories.md) + [rapid fire](interview/rapid-fire.md); mock interview |

---

## 11. Questions to ask them

- How are services split across teams — who owns a service end to end?
- What is the deploy path: Compose, Kubernetes, something managed? How long from merge to production?
- How do you handle cross-service data consistency today — outbox, sagas, or database transactions?
- What does on-call look like, and what is the most common class of incident?
- Where is the biggest piece of technical debt you would want the next hire to attack?

---

## 12. Things not to claim

Be straight about scope — it reads as senior, and the follow-up questions get easier:

- **Payouts do not exist.** Money enters wallets; there is no withdrawal, because that needs PSP
  payout APIs plus a KYC-approved gate.
- **Razorpay was never exercised with live keys** on this machine; the funding leg was simulated by
  marking the payment COMPLETED in the database, so everything downstream of the PSP is real code
  but the PSP hop itself is unverified.
- **Several backends have no UI yet**: KYC, escrow, projects, messaging and search are API-only.
- **No Kubernetes, no CI/CD to a real environment, no load testing.** It is a Compose stack.
- **`booking.created` has a consumer but no producer** — a dead listener I found while documenting.
