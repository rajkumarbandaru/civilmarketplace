# Project Handbook — `billing_micro_services`

**Audience:** an engineer with ~5 years of Java/Spring backend experience joining this platform.
**Purpose:** one file that tells you what this system is, how it is really built, which tools exist, how to
build/run/deploy it, and which traps will bite you in week one.

> Verified against the repository on 2026-09-21 (branch `main`, `2e92f42eaf`). Anything not directly verified
> is marked `UNVERIFIED`. **All paths below are relative to the repo root, `~/billing_micro_services/`.**
> Companion documents in this folder: [`06-engineer-guide.md`](06-engineer-guide.md) (how to work here day to
> day — change protocol, task recipes, debugging playbooks) and the numbered flow walkthroughs `01`–`05`.
> Deeper, evidence-cited material lives inside the repo under `.claude/` — start at `.claude/INDEX.md`.
> Per `.claude/rules/source-of-truth.md`, **source code beats this document**; if they disagree, the code wins
> and this file should be corrected.

---

## 1. What this system is

A production **Business Support System (BSS)** for a telecom **MVNO** (an operator that sells mobile service
without owning the radio network, buying wholesale capacity from real carriers instead).

It handles:

- Subscriber lifecycle — activation, suspension, plan change, port-in, churn, SIM replacement
- Prepaid wallet + postpaid billing, recharges, advance payments, invoices, dunning
- CDR collection and rating (voice/data/SMS, domestic + international)
- Product catalogue (plans, bundles, add-ons), inventory (SIM/device stock), order fulfillment and shipping
- Reseller/distributor/retailer channel — stock, commission, e-vouchers
- Tax computation and general-ledger accounting
- Gateway integration with carriers and vendors (Verizon, Tata "GUPI", NetworkIP, "PWG", Roamability) and
  with Stripe/PayPal/Twilio/Amazon/Temu

Real customers, real money, real external carrier contracts. Treat every change accordingly.

---

## 2. Read this before you touch anything

This repo carries a mandatory change-discipline (`.claude/CLAUDE.md`, `.claude/rules/*.md`). The short version
a 5-YOE engineer needs to internalize:

1. **No change is local until proven local.** Diff size does not predict blast radius here.
2. **Search for usages before editing any symbol** — even a one-liner. Shared modules have 20-45 consumers.
3. **A module's name does not tell you its behavior.** `*-utils` modules contain carrier- and brand-specific
   business rules.
4. **There is essentially no automated test coverage.** A green build proves compilation, nothing more.
5. **Schema changes auto-apply to production** (`hibernate.ddl-auto=update`), and there is no rollback path.
6. **Do not "clean up" while you are in there.** Duplicated-looking code has often drifted for a real reason.

---

## 3. Architecture reality check

Built on Spring Cloud 2020.0.0 and split into 117 active Maven modules — but it is **not** a textbook
microservices system. The things that surprise newcomers:

| Expectation | Reality here | Where to confirm |
|---|---|---|
| DB per service | **Shared MySQL.** One service opens JDBC pools into ~10 other domains' schemas in-process | `.claude/context/08-database-architecture.md` §8.3; any `*-production.properties` |
| Service discovery / gateway | None. No Eureka, no Spring Cloud Gateway, no Feign | `.claude/context/09-api-landscape.md` |
| Inter-service calls | Hand-rolled Apache HttpClient wrappers + hardcoded `host:port` from properties | `billing-util` HTTP helpers |
| Message bus | MQTT + RabbitMQ code exists but is **dormant**; the live bus is **DB polling** of an `rmq_events` table | `.claude/context/10-event-messaging.md` |
| Flyway/Liquibase migrations | None. Hibernate `ddl-auto=update` is the schema mechanism | `.claude/context/08-database-architecture.md` §8.1 |
| Distributed locks for cron | None (no ShedLock). Scheduled jobs fire on **every** replica | `.claude/context/10-event-messaging.md` §10.7 |
| Spring Security | Custom DB-backed opaque session tokens + `@ApiScope` interceptor; wired into only a minority of services | `.claude/context/36-authentication.md`, `39-service-security.md` |
| Tracing / correlation IDs | None anywhere | `.claude/context/51-logging.md` |
| API versioning | None. A breaking change hits 100% of clients at deploy | `.claude/context/50-deployment-risks.md` §50.2 |
| Rollback | None. No image versioning, no rollback stage — forward-fix only | `.claude/context/50-deployment-risks.md` §50.5 |

Mental model: **a distributed monolith deployed as many units.** The deployment boundary is real; the data
boundary is not.

---

## 4. Repository layout

160 top-level directories; **117 active modules** in the root reactor `pom.xml`, plus 13 commented-out
`<module>` lines (a few of them duplicates). Verified this session:
`grep -o "<module>.*</module>" pom.xml | grep -v '<!--' | wc -l` → 117; 7,623 non-target `.java` files.
Modules fall into three kinds, identifiable by suffix (mostly — see the traps in §15):

```text
<name>-utils        shared library      business helpers, clients, taxonomies      (~40 modules)
<name>-repo(sitory) persistence library JPA entities, repositories, DataSource cfg (~35 modules)
<name>-mgmt / etc.  deployable service  @SpringBootApplication + controllers       (49 with a main class,
                                                                                    23 actually deployed)
```

Non-module top-level directories worth knowing:

```text
.claude/                    engineering knowledge base + mandatory change rules (read INDEX.md first)
.github/workflows/          Claude PR/branch review GitHub Actions
build_scripts/              local build helpers, USSD systemd deploy, diff tooling
config_properties/          per-service test/production property files (33 files)
config-server/              Spring Cloud Config Server, native filesystem backend
prod_config_properties/     production property files served by config-server
flow_diagrams/              20+ end-to-end payment/topup/order flow write-ups (INDEX.md inside)
validation_scripts/         ~118 Python data-quality/alerting scripts (APScheduler-driven)
verifiers/ upgrade_verifier/ pre/post-deploy DB+API verification scripts (untracked in git — verify presence)
billing_tools/              Flask app: cron-job/topic operational console
fraud_service_usage/        Python fraud-monitor scripts
recovery_scripts/           manual recovery shell scripts (invoice cancel/issue, OTA pending, top-up)
reconciliation_scripts/     Verizon + PWG reconciliation scripts
billing_reconciliation_scripts/  second reconciliation tree (Verizon + PWG)
churn_process/ churn.sh     churn + SIM recycling automation (incl. Selenium flows)
renewal_process/ downgrade/ ad-hoc renewal-cleanup and recharge-deletion scripts
copy_jar_files/             rsync-style jar distribution scripts (stage/prod/live)
kub_global_config/          a small amount of k8s-adjacent config (most manifests live OUTSIDE this repo)
api_explorer/               React/Vite front-end for exploring APIs
BackendTestUtiltyScript/    backend_utility.sh helper
code_merge_requests/        dated merge-request notes
scripts/                    packed ops bundles (adv payments, recharge expiry)
Jenkinsfile                 the CI/CD pipeline (build + deploy)
```

---

## 5. Deployable services

These 24 Jenkins keys → 23 distinct modules are the confirmed deploy set (`Jenkinsfile` `svcModuleMap()`,
`Jenkinsfile:11-38`), with the local dev port from `config_properties/*-test.properties` where one exists:

| Jenkins key | Module | Domain | Test port |
|---|---|---|---|
| `user_mgmt` | `user-management` | Subscriber lifecycle | 8081 |
| `wallet_mgmt` | `wallet-management` | Prepaid wallet/balance | 8087 |
| `self_mgmt` | `self-service-payment` | Customer self-care + payments | 8086 |
| `ctlg_mgmt` | `catalogue-management` | Plans, bundles, add-ons | 8084 |
| `invt_mgmt` | `inventory-management` | SIM/device stock | 8083 |
| `service_mgmt` | `service_management` | Service/plan management | 8085 |
| `shipment_mgmt` | `shipment-management` | SIM fulfillment/logistics | 8099 |
| `tax_mgmt` | `tax-management` | Tax rules | 8097 |
| `user_kyc` | `user-kyc` | Identity/KYC/session | 8089 |
| `billing_integ` / `billing_integ_secondary` | `billing-integ` | Notification/integration hub (two instances) | 8090 |
| `billing_reports` | `billing-reports` | Reporting | — |
| `revenue_mgmt` | `revenue-issuance-mgmt` | Stripe/PayPal reconciliation | — |
| `validation` | `validation-mgmt` | Data-quality/fraud alerting | — |
| `seller_mgmt` | `seller-api-gw` | Amazon/Temu marketplace sync | — |
| `mno_mgmt` | `mno_mgmt` | Verizon provisioning (MNO) | 8807 |
| `cdr_mgmt` | `prr-cdr` | Verizon PRR CDRs (MNO) | 8989 |
| `prr_collector_mgmt` | `prr-collector` | Verizon PRR collection (MNO) | 9000 |
| `netip_gw` | `netip-gw` | NetworkIP ILD/VoIP gateway (MNO) | — |
| `netip_mgmt` | `ild-service-mgmt` | NetworkIP provisioning (MNO) | — |
| `ildcdr_mgmt` | `international-cdrs` | International CDR rating (MNO) | — |
| `pwg_api` | `pwg-api` | "PWG" wholesale gateway (MNO) | — |
| `pwg_mno_mgmt` | `pwg-mno-mgmt` | "PWG" subscriber mgmt (MNO) | — |
| `pwg_cdrs` | `pwg-cdr` | "PWG" CDR ingestion (MNO) | — |

Note: the `SERVICE_NAME` dropdown also offers `ussd_mgmt`, but `svcModuleMap()` has no entry for it, so a
`single` deploy of that key fails with "No Maven module mapping found" (`Jenkinsfile:101-108` vs `:11-38`).

**Compiled but with no Jenkins deploy path** (do *not* assume dead — several have recent commits):
`general-ledger-mgmt`, `postpaid-service`, `billing-loyalty`, `gupi_mgmt`, `gupi_api`, `ussd-api` (deploys via
its own `build_scripts/ussdSystemd.sh`), `DMI`, `billing-spal`/`spal-api`, `config-server`.

**Commented out of the root pom** (`pom.xml:104-174`): `stock_mgmt_repo`, `stock_management`,
`billing-ofcs-3g`, `billing-ofcs-lte`, `billing-workflow`, `billing-scheduler`, `billing-reseller-module`,
`controller`, `EDI-converter`, `Roamability` — several tagged `NOT COMPILING`. Note `billing-reports` and
`billing-spal` each appear both commented **and** active, so they are net-active. These are
excluded-but-not-abandoned — check `git log` before calling anything dead.

---

## 6. Data layer

- **MySQL** (primary), one host per business domain (`usermgmtrepo:3306`, `walletmgmtrepo:3306`, `cataloguemgmtrepo:3306`,
  `inventorymgmtrepo:3306`, `billingrepo:3306`, `sspaymentrepo:3306`, `userkycrepo:3306`,
  `servicemgmtrepo:3306`, `generalledgerrepo:3306`, …), schema commonly named `billing`.
- **Each service embeds many other domains' `-repo` modules** and creates a `DataSource` bean per embedded
  repo, gated by `<domain>.use_extra_threads=yes` in properties. A "microservice" here reads and writes other
  teams' tables directly.
- **Pooling is hand-rolled HikariCP** via a non-standard `hikari.poolsize` property, not Spring Boot
  autoconfiguration. If the gating flag is off, some modules fall back to a **non-pooled**
  `DriverManagerDataSource`.
- **Schema evolution = `hibernate.ddl-auto=update`.** Adding a field ships DDL to production on next deploy;
  removing one leaves an orphaned column forever. No migration tool exists.
- ORM is JPA/Hibernate with heavy native SQL.
- **MongoDB is also in play for CDRs** — `spring.data.mongodb.uri=mongodb://…@cdrmongodbrepo:27017/cdrs`,
  used by the CDR repo modules (`prr-cdr-repo`, `pwg-cdr-repo`, `billing-common-repository`,
  `revenue-issuance-repo`, `billing-ofcs-lte-repo`, `gupi-cdr-repo`). Rated/aggregated results land in MySQL;
  raw usage volume lives in Mongo.

Practical consequence: **before changing an entity, find every service that embeds that repo module**, not
just the one you are working in.

---

## 7. Configuration

Two config trees plus a config server:

```text
config-server/src/main/resources/config/   what the Spring Cloud Config Server serves (native FS backend)
config_properties/                          per-service *-test / *-production files (33)
prod_config_properties/                     production property files
```

- ~41 services import config at boot via `spring.config.import=configserver:...` — **config-server is a
  platform-wide startup single point of failure**, and it has no Jenkins deploy entry of its own.
- Two independent behavioral axes, never conflate them:
  - **Carrier** — `mnoSimProfile` / `MobileNetworkOperators` (a persisted DB column): Verizon, PWG, GUPI/Tata…
  - **Brand/tenant** — `billing.mode` / `AvailableModes` (selected at **deployment** time, not per request).
- Known pattern to watch for: a **compiled Java constant silently overriding a DB flag or request parameter**
  that looks configurable. Trace the field, don't trust the name (`.claude/context/43-feature-flags.md` §43.3).
- Secrets: Spring Cloud Config `{cipher}` values plus a narrow AWS Secrets Manager usage. **Never copy a secret
  value into code, logs, a PR, or any `.claude/` file** — cite `file:line` instead.

---

## 8. Events, scheduling and failure behavior

- **Live event bus:** DB polling (`PollEventHandler` / `EventService`) over a shared `rmq_events` table.
  MQTT/RabbitMQ are implemented but dormant everywhere.
- A failed event handler is marked processed after **one** attempt — no retry, no DLQ. The manual replay
  endpoint only reaches never-dispatched events.
- `SubscriptionEvent.UpdateFlag` uses an **ordinal-positional bitmask**. Inserting or reordering a value
  anywhere except the end silently corrupts every downstream bit — no compile error, no runtime error.
- Cron jobs have no distributed lock; with >1 replica they run concurrently.
- Dominant error-handling pattern is **silent swallowing** (`printStackTrace()`, bare `catch (Exception e)`).
  Authorization and many payment failures return **HTTP 200** with the error encoded in a JSON `status` field —
  changing that to a real status code is a breaking change for existing clients.

---

## 9. Security model (as actually enforced)

- Custom DB-backed **opaque session tokens**, validated by a `ContextInterceptor` driven by `@ApiScope`.
- That interceptor is wired into a **minority** of deployed services; elsewhere, source-level annotations
  enforce nothing.
- `SessionController.checkSessionPermissions` is currently a **structural no-op** across ~1,000 call sites —
  "fixing" it is a platform-wide behavior change, not a small bug fix.
- **No tenant isolation is enforced at any layer.** `TenantContext`/`TenantInterceptor` is dead code. Reseller/
  account scoping must be done explicitly in application code; there is no framework backstop.
- Carrier segregation is a *business/deployment* axis, **not** an authorization boundary.
- Known, cataloged defect: plaintext credentials/tokens appear in logs in places. That is a defect to avoid
  repeating, never a precedent to extend.

Details and blast-radius lists: `.claude/context/36-*.md` … `41-*.md`,
`.claude/dependencies/security-blast-radius.md`.

---

## 10. Build and run locally

Toolchain confirmed on this machine: **JDK 1.8.0_482**, **Maven 3.8.7**. You also need MySQL access (or
tunnels) and Python 3 for the ops tooling.

```bash
# Full reactor build (long). Skipping tests is harmless — there are essentially none.
mvn -T1C clean install -DskipTests

# Build one service plus everything it depends on (the usual loop)
mvn -pl user-management -am clean install -DskipTests

# Library-only build in dependency order, as the ops scripts do it
cd build_scripts && ./build.sh          # iterates build.mvn.modules
./clean.sh                              # mvn clean across the same list

# Run a service against the test profile
java -jar user-management/target/*.jar --spring.profiles.active=test
```

Gotchas:

- The root pom declares Spring Cloud `2020.0.0` as **parent** and pins `log4j 1.2.16` globally. Java 8 is
  mandatory — a newer JDK will fail the build.
- Some modules are commented out of the reactor; building them individually may fail (that is expected, see §5).
- A service will not boot without reachable config-server/DB unless you supply local overrides — copy the
  matching `config_properties/<svc>-test.properties` and point it at your own MySQL.
- `localrepositories` and `copy_jar_files/` exist for installing/distributing jars outside Maven Central —
  read them before assuming a dependency resolves from a public repo.

---

## 11. CI/CD and deployment

`Jenkinsfile` (~36 KB) is the pipeline. Parameters:

- `ENV`: `stage_env1`, `test_env1`, `live`, `pre-live`, `live-xmobee`
- `SERVICE_NAME`: one of the keys in §5
- `DEPLOY_MODE`: `single` or `all`

Flow: graceful-shutdown API call (skipped for `billing_reports`, `cdr_mgmt`, `netip_gw`) → Maven build of the
mapped module → jar copy to a Docker host → image rebuild → `kubectl apply` of **static YAML that lives outside
this repository** over SSH.

Know before you ship:

- **No rollback stage, no image versioning, no in-repo record of what is live.** Recovery = forward-fix deploy.
- Schema changes apply themselves on boot (§6). A bad entity change is not revertible.
- `ussd-api` deploys through `build_scripts/ussdSystemd.sh` (VM/systemd, hardcoded host) — not Jenkins/K8s.
- Deploy is manually triggered — there is no webhook/git-diff automation.

---

## 12. Tools inventory

### In-repo tooling

| Tool | Location | What it does |
|---|---|---|
| Build helpers | `build_scripts/build.sh`, `clean.sh`, `build.mvn.modules`, `diffScript.sh`, `tbed.sh` | Ordered library builds, cleanup, branch diffing |
| USSD deploy | `build_scripts/ussdSystemd.sh`, `ussdSystemd_test.sh` | curl-driven systemd deploy for `ussd-api` |
| Jar distribution | `copy_jar_files/copy_all_{jars,live_jars,prod_jars,stage_jars}.sh` | rsync jars to hosts (legacy path) |
| Validation suite | `validation_scripts/` (~118 Python files + `yaml_config`) | Scheduled data-quality/fraud/report checks: duplicate recharges, stuck CDRs, failed activations, wallet/voucher/tax validations, daily activation & recharge reports. Alert-only — takes no corrective action |
| Verifiers | `verifiers/` (`pre_upgrade.py`, `post_payment_verify.py`, `post_fulfill_verify.py`, `config.yml`, `HOW_TO_RUN.md`), `upgrade_verifier/` | Pre/post-deploy DB+API verification for self-service payment and order fulfillment — the closest thing to a regression suite. **Untracked in git**; confirm the files exist and match before relying on them |
| Ops console | `billing_tools/` (Flask: `app.py`, `CronJobs.py`, `topic.py`, `monitor/`) | Cron-job and event-topic operations UI |
| Fraud monitors | `fraud_service_usage/fraud_monitor_v*.py` | Usage-based fraud detection (several versions coexist) |
| Recovery scripts | `recovery_scripts/` | `cancel_invoice.sh`, `issue_invoice.sh`, `OTA_pending_subscribers.sh`, `topup_starter_plan_bundle.sh` |
| Reconciliation | `reconciliation_scripts/`, `billing_reconciliation_scripts/` (`verizon-scripts`, `pwg-scripts`) | Carrier-side reconciliation |
| Churn / recycling | `churn.sh`, `churn_process/` | Churn runs and SIM recycling (incl. Selenium-driven flows) |
| Renewal / downgrade | `renewal_process/`, `downgrade/` | Ad-hoc renewal cleanup and recharge deletion — CSV-driven, destructive, read before running |
| API explorer | `api_explorer/` (React + Vite) | Front-end for browsing/calling APIs |
| Flow diagrams | `flow_diagrams/` (`INDEX.md` + 20 documents) | End-to-end traces: bundle top-up (Verizon / PWG-TMobile), airtime top-up, plan add-on, advance payments, order/invoice payments, SIM replacement, retailer stock & wallet payments, e-vouchers, international credits, reseller/distributor payments |
| Coverage parser | `parse_coverage_data.py` | Small coverage-data helper |
| PR review automation | `.github/workflows/claude-pr-review.yml`, `claude-branch-review.yml` | Automated review on PRs/branches |
| Knowledge base | `.claude/` | Context docs, dependency maps, risk registers, change rules, templates |

### External tooling you will actually use

Java 8 · Maven 3.8+ · MySQL client · Python 3 (ops scripts) · Docker · `kubectl` (manifests live elsewhere) ·
Jenkins (deploys) · JavaMelody (per-service APM, `/monitoring`) · Spring Boot Actuator (`/actuator/*`, broadly
exposed) · Prometheus endpoints exist but have no confirmed scraper.

Not present, despite what the stack suggests: Redis, Kafka, Eureka, Zipkin/Sleuth, Helm, Flyway/Liquibase,
ShedLock, ELK shipping.

---

## 13. Business flows — where to start reading

For a given business question, read in this order:

1. `flow_diagrams/INDEX.md` → the specific numbered flow (payment/topup/order flows are covered best).
2. `.claude/services/business-flows/*.md` — `subscriber-activation`, `recharge`, `billing`, `plan-change`,
   `provisioning`, `cdr-processing`, `sim-fulfillment`, `reseller-commission`.
3. `.claude/services/<service-name>.md` for per-service depth.
4. Then the actual controller/service code — the documents are snapshots, the code is the truth.

---

## 14. Playbooks for common tasks

**Trace a production bug**
1. Identify the service from the endpoint/port (§5) and its property file in `config_properties/`.
2. Find the controller (`grep -rn "@RequestMapping\|@PostMapping" <module>/src/main/java | grep <path>`).
3. Follow into the service + repo module. Expect native SQL and cross-schema reads.
4. There is **no correlation ID** — correlate by MDN/account/transaction id and timestamp across logs.
5. Check whether a `validation_scripts/` script already alerts on this symptom.

**Add or change a field**
1. Find the entity's repo module and **every service that depends on it** (`grep -rn "<repo-module>" --include=pom.xml .`).
2. Remember `ddl-auto=update`: additive is applied automatically; removal leaves an orphaned column.
3. Check whether the field crosses an API response or an event payload — no versioning, no coexistence window.

**Change an API**
1. Find every consumer by grep across modules plus the front-ends/mobile app (out of repo — assume undocumented
   consumers exist).
2. Do not introduce a non-200 status or a new exception on a path that currently soft-fails with 200.

**Change configuration**
1. Find every consumer of the key (`grep -rn "<property>" --include=*.java --include=*.properties .`).
2. Check test *and* production files, and per carrier — Verizon/PWG/GUPI configs are structurally different and
   GUPI is frequently absent entirely.
3. Confirm no compiled constant overrides the value downstream.

**Before you open a PR**
`git status`, `git diff --stat`, then: only intended files, no secrets, no debug logging, no incidental
reformatting, no dependency bumps, and an explicit statement of what you verified and what you could not.

---

## 15. Traps that bite newcomers

1. `billing-repostiory` is a **real module name**, not a typo. Do not "fix" it.
2. `*-utils` and `*-common` modules contain carrier- and brand-specific business logic. Grep for
   `mnoSimProfile`, `MobileNetworkOperators`, `verizon`/`vzw`, `pwg`, `gupi`/`tata`, `lyca`, `infimobile`
   before assuming a module is generic.
3. Duplicated taxonomies exist and have **drifted**: `MnoSimProfileType` vs `MobileNetworkOperators`,
   `SchedulerType` vs `CronJobServices`, two `CryptoHelper` classes, two `UserAddressCommand` classes.
   Changing "the" enum usually means checking two.
4. GUPI/Tata is systematically under-integrated (no circuit breaker, no effective timeout, no retry, often no
   config at all). Never infer GUPI behavior from Verizon or PWG.
5. `SubscriptionEvent.UpdateFlag` ordinal bitmask — append only, never reorder (§8).
6. `ServiceStatusController` is duplicated byte-for-byte in 27 modules and always returns success. A `/status`
   endpoint here verifies nothing.
7. JVM-local `static Map` state guards money-moving and USSD-session paths. They only work with one replica.
   Do not copy the pattern.
8. `@Async` executors mostly have unbounded queues, so `maxPoolSize` is dead configuration.
9. No timezone, locale, or encoding is pinned anywhere — date math depends on the container default.
10. `verifiers/` and `upgrade_verifier/` are untracked in git; they can drift silently.
11. Commented-out pom modules are not necessarily dead code — check `git log` first.
12. "It's only config" is false here: a single `ConfigTable` row drives 124 call sites across 19 modules.

---

## 16. Glossary

| Term | Meaning |
|---|---|
| BSS | Business Support System — billing, ordering, customer management |
| MVNO / MNO | Virtual operator (this platform) / real network operator (Verizon, Tata, …) |
| CDR | Call Detail Record — the raw usage record that gets rated and charged |
| Rating | Turning a CDR into money using plan/tariff rules |
| MDN | Mobile Directory Number — the subscriber's phone number |
| PRR | Verizon's usage/record feed consumed by `prr-cdr` / `prr-collector` |
| ILD | International Long Distance (NetworkIP integration) |
| USSD | `*123#`-style session menus (`ussd-api`) |
| KYC | Know Your Customer identity verification (`user-kyc`) |
| GL | General Ledger — accounting postings (`general-ledger-mgmt`) |
| PWG | Codename for an unidentified wholesale carrier integration |
| GUPI | Codename for the Tata Communications integration |
| SPAL | Internal OCS/network-element provisioning (MML/UDP) |
| DMI | Twilio DID number provisioning service |
| OTA | Over-The-Air SIM provisioning |
| PUK | SIM unblocking key |
| Bucket | A metered allowance (data/voice/SMS) inside a plan or bundle |
| Dunning | Collections process for unpaid postpaid balances |

---

## 17. First-week checklist

- [ ] Read `.claude/CLAUDE.md` and `.claude/QUICK_START.md` (the change rules are enforced, not advisory).
- [ ] `mvn -T1C clean install -DskipTests` to completion once — learn where it breaks.
- [ ] Boot `user-management` locally against a test DB; call one endpoint.
- [ ] Read `flow_diagrams/INDEX.md` plus one full payment flow end to end.
- [ ] Trace one real request from controller → service → repo → MySQL table, and note which schemas it touches.
- [ ] Open `Jenkinsfile` and follow one service from build to `kubectl apply`.
- [ ] Skim `validation_scripts/` to see what production already alerts on.
- [ ] Read `.claude/context/16-known-risks.md` — it is the consolidated risk list.

---

## 18. Where to go deeper

| Question | Document |
|---|---|
| Anything — routing table | `.claude/INDEX.md` |
| System overview / architecture | `.claude/context/00-system-overview.md`, `02-architecture.md` |
| Tech stack detail | `.claude/context/03-tech-stack.md` |
| Service catalog | `.claude/context/04-service-catalog.md` |
| Database topology | `.claude/context/08-database-architecture.md` |
| APIs / events / integrations | `.claude/context/09`, `10`, `11` and their `2x` contract companions |
| Security | `.claude/context/36`–`41`, `.claude/dependencies/security-blast-radius.md` |
| Configuration & feature flags | `.claude/context/42`–`45` |
| Deployment & runtime | `.claude/context/46`–`50` |
| Observability & failure handling | `.claude/context/51`–`56` |
| Testing reality | `.claude/context/57`–`61`, `.claude/rules/test-selection.md` |
| Technical debt / known issues | `.claude/context/62`–`65`, `.claude/dependencies/technical-debt-register.md` |
| Rules for making a change | `.claude/rules/claude-development-workflow.md`, `change-management.md`, `development-rules.md` |
| Rules for reviewing a change | `.claude/rules/code-review-guidelines.md` |

---

*Maintenance note:* when something in this handbook is found stale, fix it here **and** check whether the same
claim is repeated in a `.claude/` document — per `.claude/rules/context-quality.md`, a correction that is not
propagated is not finished.
