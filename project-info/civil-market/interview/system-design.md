# System Design — Whiteboarding This Project

For the round where they say *"design a services marketplace"* or *"how would you scale what you
built?"*. Everything here refers to the real system; the "what I'd change" parts are marked as such.

---

## 1. Draw this, in this order

Do not start with the database. Start with the request.

```
[Browser / PWA]
      │  HTTPS, tenant subdomain
[CDN + nginx]  ── static bundle, /api proxied (Host preserved)
      │
[API Gateway]  ── tenant resolution · JWT validation · header injection · rate limit · routing
      │
[Services]  auth  user  booking  payment  notification  admin  review  search  messaging
            support  project  audit  tenant
      │                    │                       │
[MySQL per service]   [Kafka topics]        [Redis]  [Elasticsearch]
      │
[Config Server]  [Eureka]  [Prometheus/Grafana/Zipkin]
```

Then say the sentence that frames everything: **"Cross-cutting concerns live at the edge; each
service owns its data; anything that does not need an answer immediately is an event."**

---

## 2. The three decisions worth defending

| Decision | Why | What it costs | When I'd choose differently |
|---|---|---|---|
| Auth at the gateway, headers downstream | One implementation, one rotation point, simple services | Services trust headers, so they must be unreachable directly | Zero-trust environment → mTLS or a signed internal token per hop |
| Database per service | Independent schema evolution, no cross-team table coupling | No joins across domains; eventual consistency; more connections | Small team, one product → modular monolith, one database, same boundaries |
| Schema per tenant | Isolation that cannot be forgotten in a `WHERE` clause | Migration fan-out (N tenants × M services), pool pressure | Thousands of small tenants → shared schema + mandatory filter, or sharding by tenant |

---

## 3. Capacity: the numbers to reason with

Do not invent precision. Reason out loud:

- Say 100k bookings/month ≈ 40 writes/minute at peak — trivial for MySQL. **Reads dominate**:
  catalogue, profiles, search, tracking polls.
- Tracking is the noisy one: one poll every 5 s per active booking. 1,000 concurrent bookings =
  200 req/s of pure reads. That alone justifies moving tracking to server-sent events or WebSocket
  push, and caching the last position in Redis rather than hitting MySQL.
- Search must not touch MySQL at all — that is why profiles are denormalised into Elasticsearch.
- Notifications fan out: one announcement to 50k users is 50k rows plus 50k sends. That belongs in a
  batched, rate-limited worker, not a request thread. Today it is a synchronous fan-out inside the
  request — a genuine limitation worth naming.

---

## 4. Failure modes, and what happens today

| Failure | Today | What I'd add |
|---|---|---|
| A service is down | Gateway returns a circuit-breaker fallback; other flows unaffected | Per-route timeouts and bulkheads so one slow dependency cannot exhaust the caller |
| Kafka is down | Sync flows keep working; receipts, audit rows and index updates stop | Transactional outbox so nothing is lost, and a replay tool |
| Consumer throws | Caught and logged so the group keeps moving — the event is dropped | Dead-letter topic + retry with backoff |
| Crash between DB commit and Kafka publish | Silent divergence: DB moved, event never sent | **Outbox pattern.** The single most valuable change |
| Duplicate event delivery | Handled: consumers are idempotent on state (`already PAID` → return) | Idempotency keys on the write API too, so client retries are safe |
| MySQL down | Everything stops | Read replicas, connection budgets, graceful degradation for read-only pages |
| Razorpay down or slow | Payment creation fails; the payment row is persisted FAILED | Retries with backoff, reconciliation job against the PSP, webhook as the source of truth |
| Elasticsearch stale | Search shows old data; reindex is a full sweep | Incremental indexing driven by domain events |

---

## 5. Consistency: how to talk about it

Payments are the sharp end, so use them as the example.

- **Inside a service:** ACID. Escrow release debits the hold, freezes the commission and writes the
  wallet ledger line in one `@Transactional` method. Never two transactions for one financial fact.
- **Across services:** no 2PC. `payment.completed` is a fact published after the money is real; the
  booking service reacts by marking the booking PAID. This is eventual consistency, and the user
  sees it as "payment confirmed, booking updates a moment later".
- **Idempotency is the price of at-least-once.** Consumers key off state rather than delivery count.
- **The invariant that protects the money:** a hold becomes HELD only because its linked payment
  completed at the PSP. There is no endpoint where a payer declares their own money received, so
  the platform cannot be talked into releasing funds that never arrived. Say this one verbatim —
  it shows you think in invariants, not endpoints.

---

## 6. If traffic went 100×

In priority order, with the reason attached:

1. **Cache the hot reads.** Catalogue, site content and `/ui-config/me` are read on every page load
   and change rarely → Redis with event-based invalidation. Biggest win per hour spent.
2. **Split read and write paths for tracking.** Push instead of poll; last known position in Redis.
3. **Outbox + dead-letter topics.** Correctness before capacity — scaling a lossy pipeline multiplies
   the loss.
4. **Read replicas and per-service connection budgets.** Per-tenant schemas multiply pools; that is
   the first thing to bite.
5. **Scale stateless services horizontally.** They already register in Eureka, so the gateway
   load-balances new instances with no config change. Kafka consumer groups give per-topic
   parallelism up to the partition count — so partition counts become a capacity decision.
6. **Move notification fan-out to a worker** with batching and provider rate limits.
7. **Incremental search indexing** off the event stream instead of full reindex sweeps.
8. **Then** consider sharding tenants across database instances, routed by the same tenant key that
   already flows through every request.

---

## 7. If they ask you to design it fresh on a whiteboard

A defensible 45-minute answer that is not just "copy what I built":

1. **Start as a modular monolith** with hard internal boundaries (auth, catalogue, booking,
   payment, messaging) and one database with schema-per-module. Ship features fast.
2. **Extract first the services with a different shape**: payment (regulatory, different deploy
   cadence, different on-call), search (different datastore), notification (bursty, external
   providers).
3. **Put tenancy in from day one.** Retrofitting tenant isolation is far more expensive than
   carrying a tenant key from the start — every table, every cache key, every index.
4. **Make the event backbone real from the first event**: outbox, dead-letter, replay. Half an event
   system is worse than none.
5. **Decide the money invariants before the endpoints** and write them down: what makes a hold
   HELD, who may release, what freezes on dispute.

---

## 8. Questions they use to test depth

**"Why not put the tenant id in the JWT only?"**
Because then a request's tenant depends entirely on a token the client holds. The subdomain gives an
independent signal, and the gateway requires the two to agree. Defence in depth: stealing a token
from one tenant does not let you use it against another.

**"Why strip the client's `X-Tenant-Id` instead of validating it?"**
Because anything a client can set, a client can forge, and a validation rule can be weakened later
by someone who does not know why it exists. Stripping and re-deriving leaves no path where client
input decides isolation.

**"Your admin dashboard calls four services. Isn't that slow and fragile?"**
Yes, and it is the classic API composition trade-off. Today it is parallel Feign calls with
fallbacks, so one slow service degrades one card rather than the page. At scale the fix is a
read model: consume the domain events into a dashboard projection and serve it from one query — CQRS
where it earns its keep.

**"How do you roll out a breaking API change across 16 services?"**
Expand and contract. Add the new field alongside the old, deploy consumers first, migrate producers,
then remove the old field in a later release. Never rename in place, and never edit an applied
migration — the same discipline in the database and in the API.
