# 02 — Backend Microservices

Stack: Java 21, Spring Boot 3.2, Spring Cloud 2023.0.1 (Gateway, Config, Eureka, OpenFeign),
Spring Security + JJWT 0.12, Spring Data JPA / Hibernate 6, Flyway, MapStruct, Lombok,
Resilience4J, springdoc (Swagger).

Parent build: `backend/pom.xml` (all modules below). Each service is its own Spring Boot jar.

## Port table

"Container port" is what the service listens on inside Docker. "Host port" is what you use
from your machine (set in `docker/.env`; defaults in brackets).

| Service | Container port | Host port | Database |
|---|---|---|---|
| config-server | 8888 | **8889** (`HOST_PORT_CONFIG`) | — |
| service-registry (Eureka) | 8761 | 8761 | — |
| api-gateway | 8080 | **8080** (`HOST_PORT_GATEWAY`) | — (Redis for rate limit) |
| auth-service | 8081 | **8093** (`HOST_PORT_AUTH`) | `civil_engineer_auth` |
| user-service | 8082 | **8094** (`HOST_PORT_USER`) | `civil_engineer_users` |
| booking-service | 8083 | 8083 | `civil_engineer_bookings` |
| payment-service | 8084 | 8084 | `civil_engineer_payments` |
| notification-service | 8085 | 8085 | `civil_engineer_notifications` |
| admin-service | 8086 | 8086 | `admin_db` |
| review-service | 8089 | 8089 | `civil_engineer_reviews` |
| search-service | 8092 | 8092 | — (Elasticsearch) |
| audit-service | 8095 | 8095 | `civil_engineer_audit` |
| project-service | 8096 | 8096 | `civil_engineer_projects` |
| messaging-service | 8096 | **8097** (`HOST_PORT_MESSAGING`) | `civil_engineer_messaging` |
| support-service | 8098 | 8098 | `civil_engineer_support` |
| tenant-service | 8099 | 8099 | tenant registry |

Always call the API through the **gateway (8080)**. The direct host ports are for debugging only.
Direct calls skip the gateway, so no `X-User-*` or `X-Tenant-Id` headers get added and most
endpoints reject them.

## Platform services

### config-server (`backend/config-server`)
- Spring Cloud Config in **native** mode. It serves YAML from `classpath:/config-repo/`, so the
  configs live in `backend/config-server/src/main/resources/config-repo/<service>.yml`.
- Every other service fetches its config from here at boot. **It starts first.** Its healthcheck
  gates everything else.
- Check a service's resolved config: `curl http://localhost:8889/auth-service/default`

### service-registry (`backend/service-registry`)
- Netflix Eureka server. Each service registers under its `spring.application.name`.
- Dashboard: http://localhost:8761
- The gateway's `lb://<name>` routes and Feign clients look up instances here.

### api-gateway (`backend/api-gateway`)
Spring Cloud Gateway (reactive/WebFlux). Main classes:

| Class | Job |
|---|---|
| `GatewayConfig` | Path → service routes, CORS origin patterns, Redis rate limiter (100 req/s, burst 200) |
| `TenantResolutionGlobalFilter` | Works out the tenant from the Host subdomain, strips any client `X-Tenant-Id`, sets the real one |
| `TenantDirectory` / `TenantDescriptor` | Looks up and caches tenants from tenant-service (via Eureka) |
| `JwtAuthGatewayFilterFactory` | Validates the Bearer JWT, checks the `tenant` claim matches, adds `X-User-Id`, `X-User-Email`, `X-User-Role`, `X-User-Name` |
| `InternalOnlyPathFilter` | Blocks internal-only paths from outside (e.g. `/api/v1/bookings/admin`, `/api/v1/auth/admin`) |
| `FallbackController` | Circuit-breaker fallback responses |

**Route table** (order matters: specific paths come before broad ones):

| Path | Service | JWT? |
|---|---|---|
| `/api/v1/auth/**`, `/oauth2/**` | auth-service | no (public) |
| `/api/v1/geo/**` | user-service | no |
| `/api/v1/users/**` | user-service | yes |
| `/api/v1/bookings/*/reviews` | review-service | yes |
| `/api/v1/bookings/*/messages`, `/api/v1/threads/**` | messaging-service | yes |
| `/api/v1/catalogue/**` | booking-service | no (public catalogue) |
| `/api/v1/content/**` | admin-service | no (landing page content) |
| `/api/v1/bookings/**` | booking-service | yes |
| `/api/v1/payments/**`, `/wallets/**`, `/razorpay/**`, `/escrow/**`, `/admin/escrow/**` | payment-service | yes |
| `/webhooks/**` | payment-service | no (Razorpay webhook) |
| `/api/v1/admin/audit/**`, `/api/v1/privacy/**` | audit-service | yes |
| `/api/v1/search/**`, `/api/v1/admin/search/**` | search-service | yes |
| `/api/v1/projects/**`, `/api/v1/admin/projects/**` | project-service | yes |
| `/api/v1/reviews/**`, `/api/v1/profiles/**`, `/api/v1/admin/reviews/**` | review-service | yes |
| `/api/v1/notifications/webhooks/**` | notification-service | no (Brevo callback, shared secret) |
| `/api/v1/notifications/**`, `/api/v1/admin/announcements/**`, `/api/v1/admin/notifications/**` | notification-service | yes |
| `/api/v1/support/**`, `/api/v1/admin/support/**` | support-service | yes |
| `/api/v1/admin/**`, `/api/v1/ui-config/**` | admin-service | yes (catch-all for admin) |
| `/api/v1/tenant-resolution/**` | tenant-service | no |
| `/api/v1/tenants/**` | tenant-service | yes (SUPER_ADMIN of the operator tenant) |

## Business services

### auth-service
- Register, login (email + password), OTP send/verify, refresh token, logout, OAuth2 social
  login (Google/Facebook, only when the `social` profile and real credentials are set).
- Issues the **JWT** (signed with `JWT_SECRET`), which carries user id, email, role, name and tenant.
- Uses Redis for OTPs and sessions, and Kafka to publish events (e.g. OTP delivery →
  notification-service).
- Profile `docker` turns on `DevUserSeeder`, which creates test accounts (password `Password123!`):
  `superadmin@`, `admin@`, `customer@`, `worker@`, `engineer@`, `architect@`, `contractor@`,
  `surveyor@`, `supplier@`, `labour@` — all `@civileng.test`.
- Roles: `SUPER_ADMIN`, `ADMIN`, `SUB_ADMIN`, `REGIONAL_ADMIN`, plus customer and provider roles.

### user-service
- User profiles, addresses, worker portfolios, **KYC** (submit, list, admin approve/reject;
  approval sets `UserProfile.isVerified`).
- Public country/state/city reference data under `/api/v1/geo/**`.
- First audit producer (KYC actions).

### booking-service
- Booking lifecycle: create → assign worker → status changes → complete/cancel. Quotations.
- Public **service catalogue** (`/api/v1/catalogue/**`).
- `bookings.project_id` / `milestone_id` link bookings to project-service.

### payment-service
- Razorpay orders, payment verification, refunds, webhooks.
- **Escrow** (`EscrowHold`): the payer funds → HELD → payer releases (or auto-release after 7 days via
  `EscrowAutoReleaseJob`) → commission (5%) taken → payee's **Wallet** credited with a ledger line.
  Disputes freeze the hold, and an admin resolves them.
- A hold becomes HELD only when the linked payment is confirmed by the PSP (Razorpay).

### notification-service
- Delivery channels: email (SMTP or Brevo), SMS (Twilio), WhatsApp (Twilio), in-app notifications.
- Each channel can be set to `log` (`EMAIL_PROVIDER`, `SMS_PROVIDER`, `WHATSAPP_PROVIDER`). Then
  messages are only written to the service log. Useful locally, where OTPs show up in
  `docker compose logs notification-service`.
- Kafka listeners (e.g. `message.sent`), plus RabbitMQ.
- **Announcements**: an admin broadcast fans out one `Notification` row per recipient.

### admin-service
- Admin console backend: dashboards, analytics, revenue, workspace management.
- **UI-config / theme**: platform theme, per-workspace theme, presets
  (`/api/v1/admin/theme`, `/api/v1/admin/workspaces/{ws}/theme`, `/api/v1/ui-config/me`).
  The frontend reads this to style itself at runtime.
- Editable site content (`/api/v1/content/**` public reads, `/api/v1/admin/content/**` writes).

### project-service
- Projects, milestones (warns on budget over-allocation), project documents, status history,
  budget-vs-actual rollup (pulls booking and escrow figures by Feign), admin oversight.

### review-service
- Two-way reviews (customer ↔ worker), allowed only for a COMPLETED booking (checked with
  booking-service by Feign). One review per booking per reviewer. Rolling average in
  `RatingSummary`. One response per review. Admins can hide or restore reviews.

### search-service
- Elasticsearch indices: `profiles` (built from auth, user and review data) and `services`
  (the catalogue). Fuzzy full-text search with filters (role, city, price, rating, verified) and sorting.
  Admins can trigger a reindex. Stores nothing in MySQL; it reads the tenant registry only
  to know which tenants to reindex.

### messaging-service
- One chat thread per booking between customer and worker. It opens once a worker is assigned.
  Unread counters. Publishes `message.sent` to Kafka → in-app notification.

### support-service
- Helpdesk tickets: reporter creates → admin assigns (→ IN_PROGRESS) → RESOLVED/CLOSED.
  Reply threads. Closed tickets refuse new replies.
- **Ask AI** assistant using Google Gemini (`GEMINI_API_KEY`, model plus fallbacks). If the key is
  unset, the assistant reports that it is unavailable and ticketing keeps working.

### audit-service
- Consumes audit events from Kafka. Storage is append-only and **hash-chained**, so tampering can be detected.
- Producers (through the `audit-common` library): KYC, projects, escrow, announcements, support,
  UI-config.

### tenant-service
- Registry of tenants (workspaces). Resolves host → tenant for the gateway and the login screen.
- Onboarding a tenant publishes `tenant.events` on Kafka. Every service then creates and migrates that
  tenant's schema with no restart.

## Shared libraries (not deployed on their own)

| Module | Purpose |
|---|---|
| `tenant-common` | Routes each request's DB connection to `<db>_<tenantKey>` using `X-Tenant-Id`; runs Flyway for every tenant schema at boot and on `tenant.events` |
| `audit-common` | Spring Boot starter; lets a service publish audit events to Kafka with one call |
| `web-common` | Common web/exception classes |

## Coding conventions (from MODULE_STATUS.md)

- Read identity with `@RequestHeader("X-User-Id")` etc. Make role headers `required = false` and
  null-check them, so a missing header returns a 4xx instead of a 500.
- Enum columns: `@Enumerated(EnumType.STRING)` **and** `@JdbcTypeCode(SqlTypes.VARCHAR)`.
- Service layer throws `IllegalArgumentException` for client errors. Each service's
  `GlobalExceptionHandler` maps that to 400.
- Flyway: `src/main/resources/db/migration/V<n>__*.sql`. Never edit an applied migration.
- A **new service** needs: a route in `GatewayConfig`, a Eureka client dependency, a config file in
  config-server's `config-repo`, and a block in `docker-compose.yml` with `depends_on`
  service-registry and config-server (`condition: service_healthy`).
