# 05 — Request Flows (step by step)

## Flow 1 — Opening the app

```
Browser ── GET http://localhost:3000/ ──▶ civil_frontend (nginx :3000)
                                            └─ returns index.html + /assets/*.js (React bundle)
React boots (main.tsx → App.tsx → providers → router)
  ├─ GET /api/v1/tenant-resolution/...  → gateway → tenant-service   (workspace name/branding)
  ├─ GET /api/v1/content/...            → gateway → admin-service    (landing page copy, public)
  └─ GET /api/v1/catalogue/...          → gateway → booking-service  (service list, public)
```

## Flow 2 — Login

```
1. User submits the Login form (pages/auth/LoginPage.tsx)
2. Axios  POST /api/v1/auth/login  {email, password}
3. Browser → frontend nginx :3000  (/api/ → proxy_pass http://api-gateway:8080, Host kept)
4. API gateway:
     a. TenantResolutionGlobalFilter: Host "acme.localhost:3000" → tenant "acme"
        (plain "localhost" → the fallback tenant); strips any client X-Tenant-Id; sets X-Tenant-Id
     b. Route "auth-service" matches /api/v1/auth/**  (public, no JWT filter)
     c. lb://auth-service → Eureka lookup → auth-service container :8081
5. auth-service:
     a. tenant-common switches the DB connection to schema civil_engineer_auth_<tenant>
     b. loads the user, checks the BCrypt password, lockout counters
     c. creates an access token (JWT: sub=userId, email, role, name, tenant) + refresh token
6. Response → gateway → nginx → browser
7. The frontend saves the tokens (Redux store and storage), then calls
   GET /api/v1/ui-config/me to apply the tenant/user theme, and redirects to the dashboard
```

OTP login is similar: `POST /api/v1/auth/otp/send` → auth-service stores the OTP in Redis →
event to notification-service → email/SMS (or the log, when provider=`log`) →
`POST /api/v1/auth/otp/verify` → JWT.

## Flow 3 — Any authenticated call (example: create a booking)

```
Browser  POST /api/v1/bookings   Authorization: Bearer eyJ...
   │
   ▼  frontend nginx (:3000)  →  api-gateway (:8080)
   │
   ├─ 1. TenantResolutionGlobalFilter → X-Tenant-Id: acme
   ├─ 2. InternalOnlyPathFilter        → not an internal path, continue
   ├─ 3. Route "booking-service" (/api/v1/bookings/**) with JwtAuth filter:
   │       - verify signature with JWT_SECRET, check expiry
   │       - check JWT "tenant" claim == resolved tenant (else 403, which blocks
   │         cross-tenant token replay)
   │       - add headers: X-User-Id, X-User-Email, X-User-Role, X-User-Name
   ├─ 4. Redis rate limiter
   └─ 5. lb://booking-service  (Eureka)
   │
   ▼  booking-service :8083
      - controller reads @RequestHeader("X-User-Id") ...  (does not re-verify the JWT)
      - tenant-common → schema civil_engineer_bookings_acme
      - service layer (@Transactional) → JPA → MySQL
      - may publish a Kafka event (booking created / status changed)
   │
   ▼  JSON response back through the gateway → nginx → React Query cache → UI re-renders
```

Errors: an invalid or expired token gets **401** from the gateway, and the frontend interceptor refreshes the
token or logs the user out. A missing role gets **403** from the service. A validation error (`IllegalArgumentException`)
gets **400** from the service's `GlobalExceptionHandler`.

## Flow 4 — Service-to-service (synchronous, Feign)

Example: submitting a review.

```
POST /api/v1/bookings/42/reviews → gateway route "booking-reviews" → review-service
review-service ──Feign (lb://booking-service)──▶ booking-service: is booking 42 COMPLETED,
                                                  and is the caller a party to it?
   yes → save Review, recompute RatingSummary average
   no  → 400 / 403
```

Other Feign calls: project-service → booking-service and payment-service (budget and escrow rollup);
search-service → auth, user and review services (building the profile index).

## Flow 5 — Event-driven (asynchronous, Kafka)

```
messaging-service: customer sends a chat message
   └─ publish "message.sent" ──Kafka──▶ notification-service listener
                                          └─ creates an in-app Notification for the recipient
                                             (+ email/SMS/WhatsApp depending on provider settings)

Any audited action (KYC approve, escrow release, ticket resolve, theme change, announcement…)
   └─ audit-common publishes an audit event ──Kafka──▶ audit-service
                                                        └─ append-only, hash-chained row
                                                           (GET /api/v1/admin/audit/events)

tenant-service: a new tenant is onboarded
   └─ publish "tenant.events" ──Kafka──▶ every tenanted service
                                          └─ tenant-common creates <db>_<tenantKey> + runs Flyway
                                             (no restart needed)
```

## Flow 6 — Payment and escrow

```
1. Customer: POST /api/v1/escrow  (booking/milestone, amount)         → PENDING_FUNDING
2. payment-service creates a Razorpay order → frontend opens Razorpay Checkout
   (VITE_RAZORPAY_KEY_ID, public key)
3. Razorpay → POST /webhooks/** (public route) or client verify → payment COMPLETED
4. Reconciliation marks the hold HELD
5. Customer: POST /api/v1/escrow/{id}/release        (or the auto-release job after 7 days)
   → 5% commission frozen on the row → payee Wallet credited + WalletTransaction ledger line
   → audit event
   Dispute: POST /{id}/dispute → DISPUTED (release blocked) → admin resolves with RELEASE/REFUND/HOLD
```

## Flow 7 — Service boot (what happens inside one container)

```
java -jar app.jar
  1. bootstrap: contact config-server (http://config-server:8888), load <service>.yml
  2. connect to MySQL; tenant-common reads the tenant registry and runs Flyway
     migrations for every tenant schema
  3. connect to Kafka / Redis / RabbitMQ / Elasticsearch as needed
  4. register with Eureka (service-registry:8761) under spring.application.name
  5. /actuator/health = UP → the gateway can now route to it
```

## Multi-tenancy summary

| Step | Where |
|---|---|
| Tenant chosen from the URL subdomain (`acme.localhost`) | gateway `TenantResolutionGlobalFilter` |
| Client-supplied `X-Tenant-Id` removed, real one set | gateway |
| JWT `tenant` claim must match | gateway `JwtAuthGatewayFilterFactory` |
| DB schema `<service_db>_<tenantKey>` selected per request | `tenant-common` in each service |
| New tenant's schemas created live | `tenant.events` Kafka topic |
| Browser Host kept all the way through | frontend nginx `proxy_set_header Host $http_host` |
