# 01 — Architecture Overview

## What the product is

A multi-tenant marketplace that connects customers with civil engineering professionals
(engineers, architects, surveyors, contractors, labour, material suppliers). Customers book
services, pay into escrow, track projects and milestones, message the worker, leave reviews and
raise support tickets. Admins manage users, KYC, content, theme/UI, announcements and audits.

## The layers

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ LAYER 1 — CLIENT                                                             │
│   Browser runs the React SPA (React 18, TypeScript, Vite, MUI, Tailwind,     │
│   Redux Toolkit, React Query, Axios). Installable as a PWA.                  │
└──────────────────────────────────┬───────────────────────────────────────────┘
                                   │ HTTP  /api/v1/...
┌──────────────────────────────────▼───────────────────────────────────────────┐
│ LAYER 2 — EDGE                                                               │
│   Frontend nginx (container civil_frontend, port 3000)                       │
│     - serves the static React bundle                                         │
│     - proxies /api/** to api-gateway:8080, keeping the browser's Host header │
│   (Optional) civil_nginx on 8010/8443 — reverse proxy placeholder,           │
│     docker/nginx/sites is currently empty, so it is not used locally.        │
└──────────────────────────────────┬───────────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼───────────────────────────────────────────┐
│ LAYER 3 — API GATEWAY  (Spring Cloud Gateway, port 8080)                     │
│   1. TenantResolutionGlobalFilter: Host → tenant, sets X-Tenant-Id           │
│   2. InternalOnlyPathFilter: blocks service-to-service-only paths            │
│   3. JwtAuthGatewayFilter: validates JWT, adds X-User-Id/Email/Role/Name     │
│   4. Route by path → lb://<service-name> (looked up in Eureka)               │
│   Also: CORS, Redis rate limiter, fallback controller                        │
└──────────────────────────────────┬───────────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼───────────────────────────────────────────┐
│ LAYER 4 — BUSINESS MICROSERVICES (Spring Boot 3.2, Java 21)                  │
│   auth · user · booking · payment · notification · admin · project ·        │
│   review · search · messaging · support · audit · tenant                     │
│   Each owns its own MySQL database (and a schema per tenant).                │
│   Sync calls between services: OpenFeign via Eureka.                         │
│   Async events: Kafka topics.                                                │
└──────────────────────────────────┬───────────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼───────────────────────────────────────────┐
│ LAYER 5 — PLATFORM SERVICES                                                  │
│   config-server :8888   every service pulls its YAML config from here        │
│   service-registry :8761 (Eureka) every service registers; gateway looks up │
└──────────────────────────────────┬───────────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼───────────────────────────────────────────┐
│ LAYER 6 — DATA & MESSAGING INFRASTRUCTURE                                    │
│   MySQL 8 · Redis 7 · Kafka 7.5 + Zookeeper · RabbitMQ 3.12 ·                │
│   Elasticsearch 8.11                                                         │
├──────────────────────────────────────────────────────────────────────────────┤
│ LAYER 7 — OBSERVABILITY                                                      │
│   Zipkin (tracing) · Prometheus (metrics) · Grafana (dashboards)             │
└──────────────────────────────────────────────────────────────────────────────┘
```

## How the three parts connect

### UI ↔ Backend

- The UI never calls a microservice directly. **Every** API call goes to `/api/v1/...`
  and reaches the **API gateway**.
- In Docker, the bundle calls its own origin (`VITE_API_BASE_URL=same-origin`), and the
  frontend's nginx forwards `/api/` to `http://api-gateway:8080`. Keeping the same origin
  matters for multi-tenancy (see [request-flows.md](request-flows.md)).
- In dev (`npm run dev`), Vite's proxy forwards `/api` and `/ws` to
  `http://localhost:${HOST_PORT_GATEWAY:-8080}`.
- Login returns a JWT. Axios attaches it as `Authorization: Bearer <token>` on later calls.

### Gateway ↔ Services

- Routes are defined in code in
  `backend/api-gateway/src/main/java/com/civileng/marketplace/gateway/config/GatewayConfig.java`.
- `lb://auth-service` means "ask Eureka where auth-service is and load-balance across the
  instances". So services can move or scale without changing the gateway.
- Services **do not validate JWTs themselves**. They trust the `X-User-*` headers the gateway adds.
  Anything the client sends as `X-Tenant-Id` is stripped and replaced by the gateway.

### Services ↔ Services

- **Synchronous**: OpenFeign clients by service name through Eureka. Examples:
  review-service asks booking-service whether a booking is COMPLETED; project-service asks
  booking-service and payment-service for rollup figures.
- **Asynchronous**: Kafka events. Examples: `message.sent` → notification-service; audit events
  from `audit-common` → audit-service; `tenant.events` → every service migrates the new tenant's
  schema.

### Backend ↔ Docker

- Each service has a small `Dockerfile` (`eclipse-temurin:21-jre-alpine`) that **copies a jar
  already built by Maven** from `backend/<service>/target/*.jar`. Docker does not compile Java.
  Build the jars first, then build the images.
- The frontend's Dockerfile is multi-stage: it runs `npm ci` and `npm run build` inside Docker,
  then serves `dist/` with nginx.
- All containers share the `civil-network` bridge network and reach each other by service name.
  Inside Docker, a service uses `mysql:3306` and `kafka:9092`, not `localhost`.

## Key design decisions

| Decision | Why |
|---|---|
| Database per service | Services deploy and evolve independently; no shared tables |
| Schema per tenant (`<db>_<tenantKey>`) | Tenant data is isolated; onboarding a tenant needs no restart |
| Gateway does auth | One place to validate JWTs; services stay simple |
| Config Server (native, `classpath:/config-repo/`) | One place for every service's config |
| Eureka discovery | No hard-coded host names between services |
| Kafka for events | Loose coupling for notifications, audit, search indexing, tenant onboarding |
| Flyway per service | Versioned schema (`V1__*.sql`, `V2__*.sql`) under each service's `db/migration` |
| Hash-chained audit log | Tamper-evident trail of admin and money actions |

## Repository layout

```
RAJKUMAR/
├── backend/                  Maven multi-module (parent pom.xml, Java 21, Spring Cloud 2023.0.1)
│   ├── config-server/        :8888  central config
│   ├── service-registry/     :8761  Eureka
│   ├── api-gateway/          :8080  routing + auth + tenancy
│   ├── auth-service/         register/login/OTP/OAuth2/JWT
│   ├── user-service/         profiles, addresses, KYC, geo data
│   ├── booking-service/      bookings, service catalogue
│   ├── payment-service/      Razorpay, escrow, wallets
│   ├── notification-service/ email/SMS/WhatsApp/in-app, announcements
│   ├── admin-service/        admin console APIs, UI-config/theme, site content
│   ├── project-service/      projects, milestones, documents
│   ├── review-service/       reviews and ratings
│   ├── search-service/       Elasticsearch search
│   ├── messaging-service/    booking chat threads
│   ├── support-service/      helpdesk tickets + "Ask AI" (Gemini)
│   ├── audit-service/        hash-chained audit log
│   ├── tenant-service/       tenant registry
│   ├── audit-common/         shared library: publish audit events
│   ├── tenant-common/        shared library: tenant schema routing + Flyway per tenant
│   └── web-common/           shared library: common web classes
├── frontend/                 React SPA (Vite) + Dockerfile + nginx.conf
├── docker/
│   ├── docker-compose.yml       the full stack
│   ├── docker-compose.lean.yml  memory caps for local dev (overlay)
│   ├── .env / .env.example      ports, credentials, providers
│   ├── database/init/           SQL run on first MySQL start (creates DBs + user)
│   ├── monitoring/              prometheus.yml, grafana provisioning
│   └── nginx/                   optional edge reverse proxy config
├── .github/workflows/        CI
├── MODULE_STATUS.md          detailed build log of every module
└── project-info/             ← these docs
```
