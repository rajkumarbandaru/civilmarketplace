# Rapid Fire — One-Line Answers

Spot-check questions. Answer in one or two sentences, then stop talking.
Longer versions: [backend](../backend/backend-interview.md) · [UI](../ui/ui-interview.md) ·
[Docker](../docker/docker-interview.md) · [system design](system-design.md).

---

## The project

| # | Question | Answer |
|---|---|---|
| 1 | What is it? | A multi-tenant marketplace for civil engineering services: 16 Spring Boot microservices, a React SPA, Docker Compose. |
| 2 | How many services? | 16 deployable, plus 3 shared libraries. |
| 3 | Java and Spring versions? | Java 21, Spring Boot 3.2, Spring Cloud 2023.0.1. |
| 4 | Who are the users? | Customers, providers (engineer, architect, surveyor, contractor, supplier, labour) and admins at four levels. |
| 5 | What is the hardest part? | Multi-tenancy end to end: subdomain → JWT claim → schema, without a single place where a client can choose its own tenant. |

## Gateway and auth

| # | Question | Answer |
|---|---|---|
| 6 | Where is the JWT validated? | Only at the gateway; services read the `X-User-*` headers it injects. |
| 7 | What is in the token? | Subject (user id), email, role, name and tenant. |
| 8 | How is a role enforced? | The role arrives as `X-User-Role` and the service checks it; role headers are optional and null-checked so a missing one is a 4xx, not a 500. |
| 9 | How do you revoke a token? | You cannot today — short expiry plus refresh tokens; a Redis deny-list at the gateway would be the fix. |
| 10 | Rate limiting? | `RedisRateLimiter`: 100/s replenish, burst 200, shared across gateway instances. |
| 11 | Why order routes carefully? | First match wins, so `/api/v1/admin/**` would swallow `/admin/reviews/**` if it were registered first. |
| 12 | What is `lb://`? | Resolve this service name through Eureka and load-balance across its instances. |
| 13 | Which routes are public? | auth, geo, catalogue, site content, tenant resolution, payment webhooks, notification delivery callbacks. |
| 14 | How is a webhook authenticated if it has no JWT? | A shared secret in the query string (Brevo) or an HMAC signature (Razorpay). |

## Data

| # | Question | Answer |
|---|---|---|
| 15 | Database per service or shared? | Per service; one MySQL instance locally, one logical database each. |
| 16 | Multi-tenancy model? | Schema per tenant: `<service_db>_<tenantKey>`. |
| 17 | Who decides the tenant? | The gateway, from the Host subdomain, cross-checked against the JWT's tenant claim. |
| 18 | New tenant onboarding? | A `tenant.events` Kafka message; every service creates and Flyway-migrates the schema live, no restart. |
| 19 | Migrations? | Flyway per service, `V1__`, `V2__`…; applied files are never edited. |
| 20 | An ORM gotcha you hit? | Hibernate 6 wants a native `ENUM` column unless `@Enumerated(STRING)` is paired with `@JdbcTypeCode(SqlTypes.VARCHAR)`. |
| 21 | Where is Redis used? | OTP storage, sessions, gateway rate limiting. |
| 22 | Where is Elasticsearch used? | Two indices — `profiles` (denormalised from auth, user and review) and `services`. |
| 23 | Pagination? | Spring Data `Pageable`, page and size query parameters. |

## Events

| # | Question | Answer |
|---|---|---|
| 24 | Kafka or Feign — how do you choose? | Feign when the caller needs the answer to proceed; Kafka when it does not. |
| 25 | Name your topics. | `user.registered`, `otp.sent`, `payment.created/completed/refunded`, `booking.paid/arriving/completed`, `message.sent`, `escrow.held/released/disputed`, `audit.events`, `tenant.events`. |
| 26 | Delivery guarantee? | At least once, so consumers are idempotent. |
| 27 | Give an idempotency example. | A redelivered `payment.completed` finds the booking already PAID and returns without sending a second receipt. |
| 28 | What happens when a consumer throws? | It is caught and logged so the group keeps moving; the honest gap is a dropped event, and the fix is a dead-letter topic. |
| 29 | Your known consistency risk? | The dual write — DB commit then Kafka publish; the fix is the transactional outbox. |
| 30 | A dead listener you found? | `booking.created` has a consumer in notification-service but no producer anywhere. |

## Payments

| # | Question | Answer |
|---|---|---|
| 31 | Payment flow? | Create Razorpay order → Checkout in the browser → server-side HMAC signature verification → COMPLETED → `payment.completed`. |
| 32 | Can the browser mark a payment paid? | No. The client's word is never enough: the signature is verified server-side and Razorpay also posts a webhook. |
| 33 | What is escrow here? | A hold funded by a real payment, released by the payer or automatically after 7 days, with a 5% commission frozen on the row at release. |
| 34 | Dispute handling? | The hold moves to DISPUTED, release is blocked by the query itself, and an admin resolves RELEASE, REFUND or HOLD. |
| 35 | Are payouts built? | No — money enters wallets and cannot leave; that needs PSP payout APIs plus a KYC gate. |

## Frontend

| # | Question | Answer |
|---|---|---|
| 36 | Redux or React Query? | Both: Redux for session and client state, React Query for server state. |
| 37 | Where is the token stored? | `sessionStorage`, per tab, so two accounts can be open side by side; httpOnly cookies would be the stronger choice. |
| 38 | How does refresh work? | An Axios 401 interceptor refreshes once (guarded by a `_retry` flag) and replays the request; auth endpoints are excluded. |
| 39 | Are `VITE_` variables runtime config? | No — inlined at build time, so they are Docker build args and a rebuild is needed to change them. |
| 40 | Can a secret go in a `VITE_` variable? | Never; it ships to every browser. Only the Razorpay key *id* is public. |
| 41 | Why does the bundle call its own origin? | So the browser's Host reaches the gateway and tenant resolution works per subdomain. |
| 42 | How is the UI themed? | `GET /ui-config/me` returns the effective theme and the MUI theme is rebuilt at runtime; components read `theme.palette`, never a hex literal. |
| 43 | Caching policy? | `/assets/*` one year immutable (content-hashed), `index.html` no-cache because it names those files. |
| 44 | SPA deep links? | `try_files $uri $uri/ /index.html` in nginx. |
| 45 | Bundle strategy? | Manual chunks: vendor, mui, state, query; the PWA precache limit had to be raised because the MUI chunk is ~4.2 MB. |

## Docker and ops

| # | Question | Answer |
|---|---|---|
| 46 | How do you start it? | `mvn clean package -DskipTests`, then `docker compose up -d --build` from `docker/`. |
| 47 | Why Maven first? | The service Dockerfiles copy a prebuilt jar; only the frontend image builds from source. |
| 48 | How is startup ordered? | Health-gated `depends_on`: config-server, then Eureka, then everything else. |
| 49 | Container-to-container addressing? | By service name on the `civil-network` bridge; the host uses published ports instead. |
| 50 | Why two Kafka listeners? | `kafka:9092` for containers, `localhost:29093` for the host, because a broker advertises the address clients must use. |
| 51 | What does `docker compose down -v` do? | Deletes the named volumes — every database is wiped and the init SQL re-runs on next start. |
| 52 | Why is there a lean overlay? | Each JVM defaults to 25% of host RAM for max heap; the overlay caps heap and memory so 16 services fit on a laptop. |
| 53 | How are secrets handled? | A gitignored `docker/.env` — the minimum, not production-grade; that needs a secret manager. |
| 54 | Why no OTP email locally? | `EMAIL_PROVIDER=log` (or placeholder credentials) writes it to the notification-service log instead of sending it. |
| 55 | Production gaps? | No orchestrator, no CI/CD to a real environment, no secret manager, single-node datastores, no log aggregation or alerting. |

## Behavioural

| # | Question | Answer |
|---|---|---|
| 56 | Hardest bug? | `booking_code` was `VARCHAR(20)` while the generator emitted 22 characters, so every booking creation 500'd — fixed with a new migration rather than changing a public code format. |
| 57 | A time you prevented a bug? | `findByBookingId` returning `Optional` would have thrown once escrow gave a booking several payments. |
| 58 | Something you chose not to build? | Payouts — it needs PSP payout APIs and a KYC gate; a half-built version would have been worse than a clean boundary. |
| 59 | What would you refactor first? | The dual write — add a transactional outbox for payment and escrow events. |
| 60 | What did the project teach you? | That the interesting failures are integration failures — column widths, header propagation, CORS patterns, cache headers — not algorithms. |
