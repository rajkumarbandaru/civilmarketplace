# Engineer's Guide — Owning `billing_micro_services` at ~5 Years' Experience

> Who this is for: a backend engineer with roughly five years of Java/Spring experience who has to work in this
> telecom BSS: pick up tickets, ship changes safely, debug production, and explain the system to others.
> It assumes you have read the flow walkthroughs (`01`-`05`) and the architecture map.
>
> The short version: **this codebase will not stop you from breaking production.** There is almost no test
> suite, no schema migration tool, no rollback pipeline, and the dominant authorization check is reported as a
> no-op. Your judgement and your investigation are the safety net.

---

## 1. What makes this system different from a "normal" Spring project

At five years in, you already know Spring Boot, JPA and REST. What you will not have seen before, and what
actually decides whether your change is safe here:

| Expectation from a typical project | Reality here | What it means for you |
|---|---|---|
| Services call each other over Feign/REST with discovery | No Feign, no Eureka, no gateway. Hand-rolled Apache HttpClient wrappers and configured host names | Trace calls by grepping for the host property (e.g. `mno.mgmt.host`), not by looking at an interface |
| A message broker (Kafka/Rabbit) carries events | A **MySQL table (`rmq_events`) polled by each service**. MQTT and RabbitMQ exist but are dormant | "Async" means a row was written and someone will poll it. A failed handler is marked processed after one attempt: no retry, no DLQ |
| Flyway/Liquibase migrations, reviewed | **No migration tool. `ddl-auto=update` everywhere** | An `@Entity` change edits the production schema on deploy. Columns are added, never dropped |
| Revert the deploy if it goes wrong | No image versioning, no rollback stage | Every deploy is one-way. Fixing forward is the only option |
| Tests catch regressions | 4 trivial test classes across ~117 modules; no CI test stage | "The build is green" means it compiled. Nothing else |
| `@PreAuthorize` / Spring Security | Custom `SESSIONID` + `@ApiScope` interceptor, wired into a minority of services; the main permission check is reported as a no-op | Never assume an endpoint is protected. Check the interceptor is actually wired in that module |
| Errors return 4xx/5xx | Most failures return **HTTP 200** with a `status` field in the body | Adding a proper status code is a breaking change for existing clients |

Internalize those seven rows and you will avoid most of the ways people damage this system.

---

## 2. Your first week: learn the spine, not the whole repo

There are ~117 Maven modules and ~49 runnable services. Do not try to read them. Learn one vertical slice
end to end, and the rest becomes pattern-matching.

**Day 1-2 — follow the money.** Read the purchase and recharge flows in this folder, then open the code in
this order:

1. `self-service-payment/…/rest/CustomerSelfRechargeController.java` (the entry point, ~9,000 lines — skim)
2. `self-service-payment/…/service/PaymentFacade.java` (the newer, clean design: validator chain → routing →
   per-product dispatch → webhook confirm)
3. `self-service-payment/…/service/OrderFulfillmentServiceImpl.java` (what happens after payment)

That one path shows you the codebase's two generations of style side by side, which is the single most useful
thing to understand here.

**Day 3 — learn the event bus.** Read `event-mgmt-utils`'s `EventService` and any `*EventHooks` class (e.g.
`mno_mgmt/…/event/MnoGwEventHooks.java`). Understand: a producer writes a row; every subscriber polls and
filters. Then read `RMQEventRepository` to see the actual polling query.

**Day 4 — learn one carrier path.** `mno_mgmt` (Verizon) is the most complete. Follow
`MnoGwEventHooksHelper.activateSubscriber` → `MnoSubscriberUtils.allocateMnoSubscriptionReference` →
`mno-gateway-api`'s `SubscriptionServiceImpl`. Now you know how every carrier integration is shaped.

**Day 5 — learn where truth lives.** `.claude/INDEX.md` in the repo routes you to discovery documents per
topic. Treat them as a map, not as gospel: they are dated snapshots, and this exercise alone found several
stale claims (see §8).

**A useful habit:** whenever you learn a new fact, confirm it with `grep` before you rely on it. Example: find
every caller of a method before changing it, and every reader/writer of a table before changing a column.

---

## 3. The change protocol (what "senior" looks like here)

The repo has its own rules under `.claude/rules/`. Compressed to what you will actually do:

### Before you write code

1. **Find every caller.** `grep -rn "methodName" --include=*.java .` — not just the file in the ticket.
2. **Decide if it's shared code.** If the file lives in `billing-util`, `billing-common-repository`,
   `billing-repostiory`, `event-mgmt-utils` or similar, your one-line change is a platform-wide change. List
   the consuming services explicitly.
3. **Check for a carrier branch.** Grep the file for `mnoSimProfile`, `MobileNetworkOperators`, `Verizon`,
   `PWG`, `TMobile`, `gupi`. A "generic" module often carries carrier-specific behaviour. Never assume the
   Verizon pattern holds for GUPI: it usually does not.
4. **Check the config axis.** Two independent axes exist: carrier (`mnoSimProfile`, a DB column) and brand
   (`billing.mode` / `AvailableModes`, set at deploy time). A change safe for one can break the other.
5. **Look for a compiled constant that overrides the flag you're touching.** This codebase has a confirmed
   pattern where a Java constant silently neuters a DB flag or a request parameter.
6. **Write down the blast radius** before editing: services, tables, APIs, events, carriers, brands.

### While you write

- Smallest safe change. No drive-by refactors, renames or reformatting: in a repo with no tests, an incidental
  change is indistinguishable from the intended one in review.
- Preserve the existing response shape, including the "HTTP 200 with a status field" convention.
- Don't add a `static` mutable field. Several money-handling bugs here come from JVM-local state under
  multiple replicas.
- If you must retry an external call, confirm the receiving end is idempotent first. Around a carrier order,
  a retry can double-provision.

### Before you ship

- Re-read the diff against your blast-radius list. Anything new in the diff means re-doing the analysis.
- State plainly what you tested and what you could not. "No automated test covers this; here is what I checked
  manually" is the correct, expected answer here.
- For a schema change, say out loud: this applies to production on deploy and cannot be undone.

---

## 4. Task recipes

### Add a field to an API response
Find the DTO, add the field, and check every consumer (mobile app, web, another service, marketplace
integration). There is no API versioning, so the change hits 100% of traffic at deploy. Additive is safe;
renaming or removing is not.

### Add a column to an entity
Additive only. On deploy, Hibernate adds the column to production. Removing a field later leaves an orphaned
column forever. Check whether another service maps the same table: several tables are shared, and both
services' entity definitions must agree.

### Add a new event consumer
Implement the relevant `on*Event` in your service's `EventHooks`, register the topic in the service's
`publishTopics`/subscription list, and **filter by `mnoSimProfile` if the event is carrier-specific** (this is
exactly where the GUPI handler goes wrong). Assume your handler can be called twice, and that if it throws,
the event is gone.

### Add a new payment product type
Follow the existing shape: add to `SelfPaymentTypes`, add a case in `PaymentFacade`'s dispatch switch, and
implement the fulfilment method in `OrderFulfillmentService`. Reuse the validator chain rather than adding
inline checks.

### Add a scheduled job
There is no distributed lock (no ShedLock, no Quartz cluster). If the service runs more than one replica, your
cron runs on all of them. Either make the work idempotent or guard it with a DB-level claim (a status update
with a `WHERE` on the previous status).

### Change a carrier's behaviour
Put it in that carrier's module (`mno_mgmt`, `pwg-mno-mgmt`, `gupi_mgmt`), not in shared code. A carrier fix
applied to a shared module is the exact pattern that has caused cross-carrier regressions here.

---

## 5. Debugging playbooks

### "The customer paid but got nothing"
1. Find the transaction: `gateway_transaction` by `transaction_token` / `gateway_reference_id`. Check
   `transaction_status`.
2. `transactionStarted` → the webhook never arrived or failed. Check `webhook_events` for the gateway event.
3. `transactionComplete` but no order → the fulfilment step after confirmation failed. For a card SIM purchase
   the order is created by `user-management` from the `SUBMIT_ORDER` event, so check `payment_order_info`
   (`is_ordersubmitted`, `failure_cause`) and whether the event row exists in `rmq_events`.
4. No event row, or a row nobody consumed → nothing will retry it. This is where manual intervention or a
   replay endpoint is needed.

### "Activation is stuck"
1. `subscription.activation_state` and `sim_info.lifecycle_state` tell you how far it got: PreActive means the
   platform did its part and the carrier has not confirmed.
2. Check `mno_subscription` for `order_ref_no` (the order reached Verizon) and `failure_cause`; check
   `failed_mno_subscription`.
3. Check whether the event was even routed: the handler filters on carrier, so a wrong `mno_sim_profile` on the
   stock row means no carrier service acted.
4. Remember the duplicate guard is per-pod: two activations for one ICCID can both proceed.

### "The event didn't get processed"
Check `rmq_events` for the row and its timestamp. Consumers poll by a time window, so a slow or restarted
consumer can miss the window. There is no DLQ: if a handler threw, the event is marked processed and gone. Fix
forward by re-publishing or by an admin endpoint.

### "Usage/CDR data is missing"
Collection is a chain of scheduled jobs with a pod-local folder in the middle. Check: did the collector cron
run, is the file in the folder, did the parse cron move it, did the insert cron write to MongoDB, and is there
a record-index row. A pod restart mid-chain can strand files.

### General
There is **no correlation ID and no distributed tracing**. To follow one request across services you use the
business keys: transaction token, recharge token, ICCID, MSISDN, order number. Logs are mostly unstructured
log4j and `System.out`, and JavaMelody (`/monitoring`) is the per-service APM.

---

## 6. The risk radar (things that should make you slow down)

- **Shared/common modules** — `billing-util`, `billing-common-repository`, `billing-repostiory`,
  `event-mgmt-utils`. High fan-in; treat any change as platform-wide.
- **`SubscriptionEvent.UpdateFlag`** — an ordinal-positional bitmask. Inserting or reordering a value anywhere
  except the end silently corrupts every consumer, with no compile error.
- **Money paths** — wallet debit and the local insert are not atomic; the ledger and wallet can disagree.
- **SIM allocation** — no locking; concurrent orders can take the same stock.
- **Anything with `static` mutable state** — correct on one pod, wrong on two.
- **Security-adjacent changes** — "fixing" the no-op permission check would change behaviour at ~1,000 call
  sites at once. That is a project, not a bug fix.
- **Duplicated classes** — two `CryptoHelper`s, two carrier enums, two `UserAddressCommand`s. When you find a
  duplicate, assume it has drifted for a reason and check both.

---

## 7. Operating in production

- **Deploys** are a manually parameterized Jenkins job per service: Build (`mvn -pl <module> -am clean
  install`) → Deploy (`build_run.sh` on a Docker host, then `kubectl` from a jump host).
- **Config** comes from config-server at boot. If it is down, services do not start. Two carrier modules
  bypass it with their own properties.
- **Rollback** does not exist as a pipeline step; plan for fix-forward, and keep schema changes additive.
- **Recovery** for a stuck cron, a failed CDR batch or a lost event is largely manual, sometimes by editing the
  database. If that's the honest answer for your change, say so in the ticket rather than leaving it implied.
- **Monitoring** is JavaMelody plus a set of Python verifier scripts that detect problems after the fact
  (duplicate charges, stuck CDRs, failed activations). Actuator/Prometheus endpoints are exposed but there is
  no confirmed scraper, and there are no business metrics.

---

## 8. Don't trust documentation, including this file

Everything here was verified against the source on the date it was written, and doing that verification found
several wrong claims in the repo's own discovery notes, for example:

- The repo notes said card orders were created by `createNewOrderForTransaction`. That method has no live
  caller; `user-management` creates them from the `SUBMIT_ORDER` event.
- The notes said the invoice due date is a flat 15-day offset. In the source it comes from
  `DunningConfig.duePeriod` (or an account-level cutoff).
- The notes said 130 active Maven modules; the root pom has 117 active plus 6 commented out.
- Line numbers in the notes for the Verizon activation-completion writes had drifted by hundreds of lines.

The habit that matters: **cite file:line, and re-check before you rely on it.** When you correct something,
correct it everywhere it is repeated, or the stale copy will mislead the next person.

---

## 9. Explaining this system (interviews, design reviews)

If you are asked to describe this work, these are the points that land, because they show judgement rather
than feature lists:

- **Scale and shape.** "A telecom BSS: ~117 Maven modules, ~49 Spring Boot services, MySQL per domain, MongoDB
  for call records, on Kubernetes. I owned the subscriber journey end to end: purchase, activation, recharge."
- **A non-obvious design decision.** "Async between services is a polled MySQL table rather than a broker.
  That makes events durable and easy to inspect, but there is no retry or dead-letter, so consumers must be
  idempotent and failures need a replay path."
- **A concrete correctness problem you can reason about.** "Payment confirmation is idempotent through a state
  check plus a durable webhook dedup key, but the in-process lock only protects a single pod, so the database
  state check is what actually makes it safe across replicas."
- **Carrier integration.** "One activation event fans out to three carrier services, each filtering on the
  subscriber's carrier. The carrier is chosen earlier, by which SIM stock was allocated."
- **Operating under constraint.** "No migration tool and `ddl-auto=update`, so schema changes are additive and
  irreversible; no rollback stage, so we plan fix-forward; near-zero automated tests, so changes are scoped
  tightly and verified manually against named consumers."
- **What you'd fix first, with reasons.** Idempotency and a retry/DLQ path on the event bus; a distributed
  lock for crons and SIM allocation; enforcing the API-key check that is currently computed and ignored;
  correlation IDs. Each is a specific, defensible improvement with a blast radius you can describe.

The strongest signal at this level is not "I know Spring Boot". It is: *"I can tell you exactly what happens
if this call fails, who else depends on it, and how I would verify it in a codebase with no tests."*
