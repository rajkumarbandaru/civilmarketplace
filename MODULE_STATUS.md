# RAJKUMAR Module Status — CEP Feature Migration

Tracks migration of construction-ecosystem-platform (CEP) feature scope into RAJKUMAR
(`/home/aryagami/RAJKUMAR`), per the gap analysis at
`/home/aryagami/rajkumar/construction-ecosystem-platform/docs/rajkumar-migration-gap-analysis.md`.
RAJKUMAR is the platform going forward; CEP is reference-only.

## Status legend

| Marker | Meaning |
|---|---|
| ✅ Done | built, wired end-to-end, verified live against the real stack |
| 🚧 In progress | partially built |
| ⬜ Not started | |

## Build order and status

| # | Item | Status | Where | Notes |
|---|---|---|---|---|
| 1 | KYC flow | ✅ Done | `user-service` | `KycDocument` entity (Flyway V2), submit/list/admin-approve/admin-reject endpoints. Approval flips `UserProfile.isVerified`. Verified live: submit → 403 without admin role → approve → profile verified → stats reflect it. |
| 2 | Reputation (reviews) | ✅ Done | new `review-service` (port 8089) | `Review` + `RatingSummary` entities (Flyway V1, DB `civil_engineer_reviews`). Bidirectional reviews gated on a COMPLETED booking via a Feign call to `booking-service`; one review per booking per reviewer; rolling average recomputed on submit and on moderation; one response per review from the reviewed party; admin hide/restore. Verified live: completed-booking gate, duplicate rejection, non-party rejection, bidirectional review, response authorization, admin 403 without role, and summary recompute on hide/restore. |
| 3 | Search | ✅ Done | new `search-service` (port 8092) | Standalone service over Elasticsearch — which was dead infra until now. Two indices: `profiles` (denormalised from auth + user + review services) and `services` (booking-service's ServiceCategory). Full-text with fuzziness, filters (role/city/price range/min rating/verified/available), sorts (relevance/rating/price/experience), admin reindex. Demand-side + staff roles and non-ACTIVE accounts excluded from results (SRS FR-10). Verified live incl. through the gateway with real JWTs. **Freshness caveat below.** |
| 4 | Messaging | ✅ Done | new `messaging-service` (port 8097 host / 8096 container) | One thread per booking between customer and worker, opens once a worker is assigned. `MessageThread` + `Message` (Flyway V1, DB `civil_engineer_messaging`). Send/list/unread-count endpoints, per-party unread counters, `message.sent` Kafka event fans out to a new `notification-service` listener. Verified live end-to-end through the gateway with real JWTs incl. the pre-assignment 400, non-party rejection, unread counts, and the in-app notification landing on the recipient. **Port note + a real notification-service bug found — see below.** |
| 5 | Escrow/Milestone | ✅ Done (backend) | `payment-service` (`EscrowHold`, `Wallet`) | `EscrowHold` + the first real `Wallet`/`WalletTransaction` entities (Flyway V2). Fund via the existing PSP path, payer-confirmed release with commission frozen onto the row, auto-release timer, refund/cancel, dispute freeze + admin resolve, wallet ledger. Escrow figures now feed project-service's rollup. Third audit producer. Verified live incl. auto-release firing. **Two pre-existing payment-service bugs found and fixed — see below.** |
| 6 | Project mgmt | ✅ Done (backend) | new `project-service` (port 8096) | `Project` + `Milestone` + `ProjectDocument` + `ProjectStatusHistory` (Flyway V1, DB `civil_engineer_projects`). Owner-scoped CRUD, milestones with soft-warned over-allocation, status transitions with a booking-completion guard, document references, budget-vs-actual rollup, admin read-only oversight. `bookings.project_id`/`milestone_id` added in booking-service (Flyway V3). Second audit producer after KYC. Verified live through the gateway. **No frontend yet — see below.** |
| 7 | Announcements | ✅ Done | `notification-service` | `Announcement` entity (Flyway V2, DB `civil_engineer_notifications`). Admin one-click broadcast, fans out to a `Notification` row per recipient (type `ANNOUNCEMENT`). New `audit-common` producer. Verified live incl. `"*"` audience matching the ACTIVE user count exactly. See below. |
| 8 | Support/Helpdesk | ✅ Done | new `support-service` (port 8098) | `SupportTicket` + `TicketMessage` (Flyway V1, DB `civil_engineer_support`). Reporter creates/lists tickets, reply thread reuses messaging-service's shape, admin assign + status transitions (OPEN→IN_PROGRESS→RESOLVED/CLOSED), terminal tickets reject new replies. Fourth audit producer. Verified live through the gateway with real JWTs incl. non-party 403, reporter-cannot-self-resolve 403, assign auto-transitioning to IN_PROGRESS, and the hash-chained audit trail for create/assign/resolve. |
| 9 | Audit logging | ✅ Done (KYC only so far) | new `audit-service` (port 8095) + `audit-common` starter | Kafka-based, append-only, hash-chained. `user-service`'s KYC flow instrumented as the first producer. Verified live incl. tamper detection. **Only KYC is instrumented — see below for what's still unaudited.** |
| 10 | UI-config | ✅ Done | `admin-service` (`admin/uiconfig`) + frontend | Backend, admin console and member shell (`Navbar`) all built and verified live incl. Super-Admin-edit → member-reflects, member appearance self-service, and the admin/member permission split. Fifth audit producer. Details below. |
| 11 | Multi-tenancy | ✅ Done (backend) | new `tenant-common` + `tenant-service` (port 8099), all 11 DB-backed services | Schema-per-tenant. Tenant resolved from the request subdomain by the gateway, cross-checked against a `tenant` JWT claim, injected downstream as `X-Tenant-Id`. Every service's rows live in `<prefix>_<tenantKey>`; Flyway runs once per tenant schema at boot and on a `tenant.events` Kafka message, so onboarding needs no restart. Verified live: 4 tenants × 11 schemas, same email registered independently per tenant, cross-tenant token replay 403, client-supplied `X-Tenant-Id` stripped, module gating 404, per-tenant audit trail. **Two pre-existing bugs found — see below.** |

## Codebase conventions (read before building the next module)

- Enum entity fields: pair `@Enumerated(EnumType.STRING)` with
  `@JdbcTypeCode(SqlTypes.VARCHAR)` (`org.hibernate.annotations.JdbcTypeCode` /
  `org.hibernate.type.SqlTypes`), or Hibernate 6 on MySQLDialect will demand a native
  `ENUM(...)` column and fail schema validation against the Flyway-created `VARCHAR` column.
- Auth/identity headers arrive via the gateway's JWT filter: `X-User-Id`, `X-User-Email`,
  `X-User-Role`, `X-User-Name`. Services read them with `@RequestHeader`, never verify the JWT
  themselves. Make role headers `required = false` and check for `null` explicitly — a missing
  *required* header throws before your handler runs and falls through to the generic 500 handler
  instead of a proper 4xx.
- Admin role names (from `auth-service`'s `roles` seed data): `SUPER_ADMIN`, `ADMIN`, `SUB_ADMIN`,
  `REGIONAL_ADMIN`.
- Service layer pattern: `@Transactional` methods on a `@Service`, throwing
  `IllegalArgumentException` for client errors (mapped to 400 by each service's
  `GlobalExceptionHandler`), `@Slf4j` logging on state changes. Controllers stay thin — no
  business logic.
- Flyway migrations are per-service, one file per version (`V1__*.sql`, `V2__*.sql`, ...) under
  each service's `src/main/resources/db/migration/`. Never edit an applied migration.
- New services need: a route added to `api-gateway`'s `GatewayConfig` (with the JWT filter unless
  it's a public/webhook path), a Eureka client dependency, and a `docker-compose.yml` block with
  `depends_on: service-registry` + `config-server` (both `condition: service_healthy`).
- Build/run: this host's default `java`/`mvn` resolve to JDK 17, but RAJKUMAR requires 21 — use
  `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn ...` for compiles, or build inside Docker
  (`docker compose build <service>`) which already pins JDK 21 in the Dockerfile.
- **Pre-existing bug found and FIXED (2026-08-11):** `booking_code` was `VARCHAR(20)` but
  `BookingService.generateBookingCode()` emits 22 chars (`BK-` + 14-digit timestamp + `-` + 4
  digits), so *every* booking creation failed with a data-truncation 500 — booking creation was
  entirely broken. Fixed by widening the column to `VARCHAR(30)` (booking-service Flyway `V2`) and
  matching the `@Column(length)` on the entity, rather than changing the code format, which the
  lookup-by-code API depends on.
- **Pre-existing bug found and FIXED (2026-08-11):** `auth-service` failed to start at all —
  `SecurityConfig` called `.oauth2Login()` unconditionally, but Spring only creates a
  `ClientRegistrationRepository` when `spring.security.oauth2.client.registration.*` is configured,
  which it never was. Fixed by injecting `ObjectProvider<ClientRegistrationRepository>` and applying
  `oauth2Login()` only when a registration is actually present; the service logs which branch it
  took at startup. Password/OTP login is unaffected, and social login switches itself on
  automatically if credentials are ever supplied. The full gateway login path (login → JWT →
  gateway-injected `X-User-*` headers → downstream service) is now verified working end-to-end.
- **Port note:** `api-gateway`'s host port comes from `HOST_PORT_GATEWAY` in `docker/.env`, which
  is **8080** as of 2026-08-21 — check that file rather than trusting this line. It was 8087 for a
  while, when 8080 was taken on this host by an unrelated `projectile_ui` container; that container
  is gone and the default (`${HOST_PORT_GATEWAY:-8080}`) applies again. `review-service` is on 8089,
  `project-service` on 8096.

## Escrow / milestone — verified live 2026-08-12

SRS CP·06 FR-06/FR-09, built into `payment-service` rather than a new service — escrow is the same
money as payments and splitting it would put a network hop inside a financial transaction.

**The invariant to protect:** a hold becomes HELD *only* because its linked payment completed at
the PSP. There is no endpoint by which a payer declares their own money received, so the platform
cannot be talked into releasing funds that never arrived.

- **`escrow_holds`** (Flyway `V2`) — payer/payee, booking, optional project + milestone, amount,
  `PENDING_FUNDING | HELD | RELEASED | REFUNDED | CANCELLED | DISPUTED`, `auto_release_at`, and
  commission frozen onto the row at release so the arithmetic stays reproducible if the rate
  changes ("auditable to the paisa", per the NFR).
- **Wallets are real now.** `wallets`/`wallet_transactions` had existed since payment-service's V1
  with no entity, repository or API behind them — escrow release is the first thing that puts money
  in one. Added `Wallet`/`WalletTransaction` entities, `WalletService` (balance change and ledger
  line always in one transaction), `GET /api/v1/wallets/me`, `/me/transactions`, admin
  `/{userId}`, plus a `held_balance` column for FR-09's dispute freeze.
- **Endpoints** — `/api/v1/escrow`: create, `/mine?role=payer|payee`, `/{id}`,
  `/{id}/release`, `/{id}/refund`, `/{id}/dispute`, plus `/booking/{id}` and `/project/{id}` for
  service-to-service reads. `/api/v1/admin/escrow` lists everything and resolves disputes
  (`RELEASE` / `REFUND` / `HOLD`).
- **Auto-release** (`EscrowAutoReleaseJob`, cron `escrow.auto-release-cron`, default every 10 min):
  a funded hold the payer never confirms releases itself after `escrow.auto-release-days` (7), so a
  silent payer cannot strand a provider's money. Each hold releases in its own transaction — one
  failure must not abort the sweep — and DISPUTED holds are excluded by the *query*, not by a
  filter someone could later drop.
- **project-service integration:** `/summary` now carries `escrowHeld`, `escrowReleased`,
  `escrowHoldCount`, `disputedEscrowCount` and `escrowDataAvailable`, closing the gap recorded when
  project-service shipped. Same null-means-unknown fallback convention as the booking client.
- **Audited** — third `audit-common` producer; create, funding, release, refund/cancel, dispute and
  resolution all land in audit-service.

**Verified live 2026-08-12:** create → release-before-funding refused → PSP funding simulated →
reconciliation promoted the hold to HELD → payee's release attempt 403 → payee disputed → payer's
release blocked while disputed → non-admin resolve 403 → admin resolved RELEASE → commission
₹2,500 on ₹50,000 at 5%, ₹47,500 credited with a matching ledger line → double-release refused →
self-escrow and negative amount refused → duplicate open hold on one milestone refused, allowed
again after cancellation → auto-release fired on schedule (₹20,000 → ₹1,000 commission, ₹19,000
credited, `releasedBy: null`) → audit chain complete → project summary shows the escrow figures.

**Two pre-existing payment-service bugs found and fixed (2026-08-12):**

1. **Every payment creation 500'd whenever the PSP call failed.** `createPaymentOrder` built its
   `payment.created` event with `Map.of(...)`, which throws NPE on a null value — and
   `razorpayOrderId` is null exactly when the Razorpay call failed. The failure path set the
   payment to FAILED correctly and then died on the way out, so the caller got a 500 and never
   learned the payment row existed. On this host, where the Razorpay keys are placeholders, that
   meant *no payment could ever be created*. Fixed with a `HashMap` that tolerates the null and
   also carries the status.
2. **`findByBookingId` returning `Optional` was a latent time bomb.** One booking can now carry
   several payments (one per milestone hold), so the Optional query threw
   `IncorrectResultSizeDataAccessException`. Replaced with
   `findFirstByBookingIdOrderByCreatedAtDesc` and a status-scoped variant; escrow funding uses
   `createEscrowFundingOrder`, which never reuses a booking's pending payment — sharing one payment
   row between holds would fund several holds off a single capture.

**Not built (deliberate):**

1. **Withdrawal / payouts (FR-05)** — needs PSP payout APIs plus the KYC-approved gate. Wallets are
   read-only to users today; money goes in, nothing comes out.
2. **Invoices (FR-01/FR-08)** — no `Invoice` entity exists in payment-service at all, so
   GST-compliant invoicing is untouched and the project rollup still has no invoice figures.
3. **Per-category commission rates (FR-03)** — one platform rate in config
   (`escrow.commission-rate`, 5%). Per-category rates belong to admin-service.
4. **Real dispute resolution (TR·03)** is out of migration scope; `resolveDispute` is the minimum
   that stops disputed money being stuck forever, not the full flow.
5. **`held_balance` has no writer yet** — the column and `WalletService.hold`/`releaseHold` exist
   for FR-09, but nothing calls them, because a dispute today freezes the *escrow hold* before
   release rather than money already in a wallet. That changes when payouts land.
6. **PSP funding could not be exercised end-to-end** — the Razorpay keys on this host are
   placeholders, so the funding payment was marked COMPLETED directly in the database to simulate
   the PSP callback. Everything downstream of that is the real code path, but the Razorpay leg
   itself is unverified.

## project-service — verified live 2026-08-12

SRS ENT·01. The parent record Bookings (and later Invoices and EscrowHolds) hang off, so a Company
can see one budget-vs-actual view across a dozen separate bookings. Own service on **8096**, DB
`civil_engineer_projects`.

- **Entities** (Flyway `V1`): `Project` (owner, type NEW_BUILD/RENOVATION/INTERIOR/SINGLE_TRADE,
  status DRAFT→ACTIVE→ON_HOLD→COMPLETED/CANCELLED, budget ceiling, cost centre),
  `Milestone`, `ProjectDocument` (object-storage `fileRef` only — never inline binary, same rule as
  `KycDocument`), `ProjectStatusHistory` (FR-05's append-only transition log).
- **Endpoints** — `/api/v1/projects`: create, list-mine, get, patch, `PATCH /{id}/status`, delete,
  `/{id}/summary`, `/{id}/history`, milestones (add/list/patch/`/state`/delete), documents
  (attach/list/delete). `/api/v1/admin/projects` is **read-only** oversight for staff roles.
- **booking-service link:** Flyway `V3` adds nullable `project_id` + `milestone_id` to `bookings`
  (no FK — different service, different schema), `CreateBookingRequest` accepts them, and
  `GET /api/v1/bookings/project/{projectId}` feeds the rollup. That path is two segments so it does
  not collide with `GET /{bookingId}`.
- **Rules that are deliberate, not incidental:**
  - Milestone allocation over the ceiling is a **soft warning**, per the SRS — it saves, sets
    `overAllocated`, and logs the override reason to the audit trail.
  - A project cannot be COMPLETED or deleted while any linked booking is non-terminal.
  - When booking-service is unreachable the Feign fallback returns **null, not an empty list** —
    an empty list is indistinguishable from "no bookings", and an outage must never read as
    permission to complete a project. `/summary` degrades instead of failing (a Company consults
    that dashboard during site disputes) and flags `bookingDataAvailable: false`.
  - Deletes are soft, for both projects and milestones — a completed booking must keep its
    historical reference.
  - `@Version` on `Project`: concurrent budget edits are last-write-wins by SRS decree, but the
    loser gets a 409 rather than being silently overwritten.
- **Audited from day one** — second `audit-common` producer after KYC; project create/update/
  status-change/delete and budget over-allocation overrides all land in audit-service.

**Verified live 2026-08-12** through the gateway with real JWTs: create → invalid type and
end-before-start rejected → milestones → over-allocation saved with `overAllocated: true` →
non-owner 403 on read and on edit → admin read + admin list, engineer 403 on both → booking created
with `projectId` → spend appears in the rollup (₹123,900 incl. fee + GST) → COMPLETE and DELETE both
blocked while that booking was live → cancel → all milestones complete (`percentComplete: 100`) →
project COMPLETED → edit-after-terminal 400 → status history shows all three transitions → document
attach → audit events present in audit-service with an intact hash chain.

**Not built (deliberate, and why):**

1. **No frontend.** Backend only, like review- and search-service.
2. **Escrow and invoice figures are absent from the rollup** — actual spend is booking-derived,
   since payments' escrow model is item 5. `EscrowHold` is also what FR-09 really wants the delete
   guard to check; the booking check is the closest available proxy today.
3. **FR-08 (notify everyone booked when scope or budget changes) is logged, not fanned out** —
   that needs announcements (item 7). A ceiling change that strands milestone allocations shows up
   as `overAllocated` in the summary rather than reaching those users.
4. **Cost-centre `BudgetLine` rollup rows** are not materialised; `costCentre` is stored on the
   project for ENT·05 to consume later.
5. **No Company/RBAC scope** — a project has exactly one owner, because RAJKUMAR has no company
   entity (ENT·02/03, out of migration scope). "Project-scoped edit access for team members" is
   unimplementable until that exists.

## Custom theme presets — verified live 2026-08-14

The "Start from a preset" row on the Theme & UI style screen now ends with a **Save current as
preset** chip: it names the values currently in the form and stores them server-side, so they sit
alongside the nine shipped presets for any scope to start from, and carry an ✕ to delete.

- **`ui_theme_preset`** (`admin-service` Flyway `V5`) — the same columns as `ui_theme_config`
  minus `brand_name`/`logo_url`. A preset is a *look*, not an identity: the service drops those two
  fields on the way in, so applying a preset can never rename a workspace to another tenant's
  wordmark. Label is unique, and the key is a slug of it fixed at creation.
- **Endpoints** — `POST /api/v1/admin/theme/presets`, `DELETE /api/v1/admin/theme/presets/{key}`;
  `GET .../presets` returns shipped presets first, then saved ones alphabetically, each carrying
  `builtIn` so the console knows which ones may be deleted. SUPER_ADMIN only, like the rest of the
  screen. Saving under an existing name overwrites that preset rather than erroring.
- Style fields are validated exactly as a theme save is, so a preset can never name a layout or
  button style the shell does not implement.
- **Verified live 2026-08-14** through the gateway: save → brand name and logo dropped → appears in
  the list as `builtIn: false` → re-save under the same name overwrote instead of duplicating →
  `uiStyle: neon` and a blank name refused → deleting a built-in refused → non-admin 403 on both
  writes → delete removed it from the list.
- **Gap found and closed the same day:** `layoutStyle: "topbar"` had been added to the frontend
  shell and to `ThemePresets.LAYOUT_STYLES`, but the running `admin-service` still refused it —
  `admin-service/Dockerfile` copies `target/*.jar`, so `docker compose build` alone ships stale
  code. **Always `mvn package` before `docker compose build` for these services.** Rebuilt properly,
  a workspace saves `topbar` and the member's `/ui-config/me` reflects it.

## UI-config — verified live 2026-08-12

Menu and theme served from the backend instead of compiled into the bundle (CEP MOB·15), ported into
`admin-service` rather than a new service. Four-layer overlay, same as CEP: catalogue default →
per-workspace (role) → per-user override → the member's own appearance.

- **Schema** — `admin-service` Flyway `V2__uiconfig.sql`: `ui_menu_items`, `ui_workspace_menu`,
  `ui_user_menu_override`, `ui_theme_config`, `ui_user_appearance`. Seeds a `PLATFORM` theme row and
  a menu catalogue remapped to the routes `App.tsx` actually renders, with MUI icon names (CEP's
  seed used its own roles and glyph icons — do not copy it verbatim). `V3__drop_unbuilt_uiconfig_
  placeholders.sql` removes the `admin-reports`/`admin-invoices`/`admin-settings` rows that pointed
  at `RevenuePage`/`AdminDashboard` as stand-ins rather than real pages — no dead links in the nav.
- **Backend** — `admin/uiconfig/{model,repository,service,controller,dto}`.
  `UiConfigController` (`/api/v1/ui-config`): `GET /me`, `GET|PUT|DELETE /me/appearance`.
  `AdminUiConfigController` (`/api/v1/admin`): platform theme get/put; `/workspaces` list;
  per-workspace menu and theme (incl. `/theme/effective`) get/put/delete; per-user menu override
  get/put/delete. Admin writes are gated by `requireSuperAdmin(X-User-Role)`. Fifth audit producer
  (after KYC, project-service, escrow/milestone, support-service) — platform and workspace theme
  writes publish to `audit-service` via `audit-common` (no before-state captured; the command plus
  actor and scope is what a theme-change review needs, unlike a booking or payment diff).
- **Division of responsibility (keep it):** Super Admin owns colours, font, radius, UI style and nav
  layout; a member owns only colour mode and density — `ui_user_appearance` has no other columns, so
  the limit holds even against a direct API call.
- **Frontend** — `providers/UiConfigProvider.tsx` (mounted in `main.tsx`), `services/uiConfigApi.ts`,
  `theme.ts` consuming the resolved theme, `pages/settings/AppearancePage.tsx` (`/appearance`),
  `pages/admin/ThemeSettings.tsx` + `components/admin/ThemeEditor.tsx` (`/admin/theme`),
  `pages/admin/WorkspaceManagement.tsx` (`/admin/workspaces`), `AdminLayout` building its nav from
  the `Platform` section of the live menu, and `Navbar` reading the `Work` section
  (`useMenuSection('Work')`) for the top nav plus the `Account` section for the profile
  dropdown/mobile drawer, both via `DynamicIcon`. Falls back to a static `Services`/`Profile` link
  when the menu hasn't loaded, failed, or the user is signed out.
- **Gateway** — covered by the existing `/api/v1/admin/**`, `/api/v1/ui-config/**` → admin-service route.
- **Pre-existing bug found and FIXED (2026-08-12):** admin-service defined its own `CorsFilter`
  (`admin/config/WebConfig.java`, unrelated to this feature — predates it) alongside the gateway's
  centralized `CorsWebFilter`. Requests routed through the gateway got `Access-Control-Allow-Origin`
  set twice with the same value (`http://localhost:3007, http://localhost:3007`), which Chrome
  rejects even though the values match — every browser call to any admin-service route silently
  failed with `TypeError: Failed to fetch`, `net::ERR_FAILED`, no entry in the Network tab's
  Fetch/XHR filter, and no console exception (React Query swallowed it into `isError`), so the
  member shell degraded to its static fallback nav with no visible sign of failure. Curl testing
  never caught this because curl doesn't enforce CORS. Found by pairing a live-browser
  DevTools session (Network → 0 XHR requests despite a logged-in session with a valid token in
  localStorage; a manual `fetch()` in the console surfaced the actual CORS error) with a source
  read that found the redundant filter. Fixed by deleting `WebConfig.java` — CORS is meant to be
  handled once, centrally, at the gateway; no other service defines a competing filter. Verified
  live after redeploy: `curl -D -` now shows one `Access-Control-Allow-Origin` value, and a
  browser hard-refresh renders `Dashboard` in the top nav and `Appearance` in the profile dropdown
  — both driven by the live menu response instead of the fallback.
- **Verified live 2026-08-12**, all of it now confirmed via an actual browser (Chrome, no
  extension available on this host — done by walking the user through DevTools manually): CUSTOMER
  login → `/api/v1/ui-config/me` resolves `Work`/`Account` menu sections exactly as `Navbar`
  consumes them, rendering correctly in the real page → non-admin 403 on
  `PUT /api/v1/admin/workspaces/{role}/theme` → SUPER_ADMIN edits the `CUSTOMER` workspace's
  `primaryColor` → member's next `/me` fetch reflects it immediately → member sets their own
  `colorMode`/`density` via `/me/appearance` and cannot smuggle `primaryColor` in the same call →
  platform theme edit shows up correctly hash-chained in `/api/v1/admin/audit/events?entityType=
  UiTheme`.

## Announcements — verified live 2026-08-12

SRS ENT·04, extending `notification-service` per the build-order notes rather than a new service —
the whole point of an announcement is to land in the same inbox every other notification does.

- **Model:** `Announcement` (Flyway `V2`, DB `civil_engineer_notifications`) — title, body,
  `target_roles` (comma-separated role names, or the literal `'*'` for every ACTIVE user — same
  convention as admin-service's `ui_menu_items.default_roles`, so admins reading either console see
  one rule), `created_by`, `recipient_count`. No draft state: publishing fans out immediately,
  matching the SRS's "one-click" framing.
- **Fan-out reuses the existing `Notification` row**, not a new delivery mechanism — one row per
  recipient (`type=ANNOUNCEMENT`, `referenceType=ANNOUNCEMENT`, `referenceId=<announcementId>`), so
  a member sees it in the same `GET /api/v1/notifications` list as every other notification type.
  No separate member-facing announcements endpoint was built for that reason.
- **Audience resolution** — new `AuthServiceClient` Feign client (service-to-service, bypasses the
  gateway, same pattern admin-service and search-service already use against
  `AdminUserController`) pages through `GET /api/v1/auth/admin/users?role=&status=ACTIVE` per
  requested role, deduping into a `LinkedHashSet<Long>`; `'*'` does one unfiltered ACTIVE-only pass.
  Paging is capped at 100 pages (20,000 users) per role as a synchronous-request safety bound — see
  "Not built" below.
- **Endpoints** — `/api/v1/admin/announcements`: `POST` (publish), `GET` (history, most-recent
  first). Gated the same way as every other admin console in this codebase:
  `X-User-Role` in `SUPER_ADMIN`/`ADMIN`/`SUB_ADMIN`/`REGIONAL_ADMIN`, header `required = false` so
  a missing role 403s here instead of 500ing in the framework.
- **Gateway** — new `/api/v1/admin/announcements/**` path added to notification-service's route,
  positioned **before** admin-service's `/api/v1/admin/**` catch-all (same reason review-service's
  admin route already has to precede it — the broader pattern would otherwise swallow the
  narrower one silently).
- **Audited** — new `audit-common` producer (`notification-service` had none before this);
  `CREATE` action, `recordCount` set to the resolved audience size.

**Verified live 2026-08-12** through the gateway with real JWTs: publish to a single role
(`CUSTOMER`) → `recipientCount: 1` → that customer's `/api/v1/notifications` contains it → non-admin
`POST` 403s → admin history lists it → hash-chained audit event present with the right
`recordCount`. Then publish with `targetRoles: ["*"]` → `recipientCount` matched
`SELECT COUNT(*) FROM users WHERE status='ACTIVE' AND is_deleted=0` exactly (10).

**Not built (deliberate):**

1. **Fan-out is synchronous on the request thread.** Fine at today's user counts; a platform that
   outgrows the 100-page/20k-per-role cap needs an async/batched job, not a bigger constant.
2. **No separate member-facing "announcements" list/read-receipt UI** — deliberately reuses the
   existing notification inbox and its `is_read`/`read_at` columns rather than duplicating them.
3. **No multi-channel delivery** (email/SMS/push) — `channel` is hardcoded to `IN_APP`, same as
   every other `NotificationService.createNotification` call site today; the CEP note about
   "multi-channel" broadcast is not carried over since no other notification type in this codebase
   sends multi-channel yet either.
4. **No edit/recall/expiry** — an announcement is a fire-and-forget broadcast, not an editable
   record; the DB row exists for history/audit, not for later mutation.
5. **Frontend admin console screen** — not built; exercised via direct API calls only. Same
   "no frontend yet" position as review-, search-, and project-service.

## messaging-service — verified live 2026-08-12

- **Model:** `MessageThread` (one per `booking_id`, unique) + `Message` (Flyway V1, DB
  `civil_engineer_messaging`). A thread has exactly two parties — the booking's `customerId` and
  `workerId` — mirroring `booking-service`'s own model rather than a general N-party thread, since
  every booking is a two-party job.
- **Lazy thread creation.** No thread exists until the first message is sent; sending validates via
  Feign to `booking-service` that a worker is actually assigned (`workerId != null`) and that the
  sender is one of the two parties. Trying to message before a worker is assigned returns a 400 with
  a specific reason, not a generic error — verified live.
- **Endpoints:** `POST/GET /api/v1/bookings/{bookingId}/messages`, `GET /api/v1/threads` (inbox,
  most-recent-first), `GET /api/v1/threads/unread-count`. Reading a thread's messages marks it read
  for the caller only — the other party's unread counter is untouched. Verified live: unread count
  correctly increments on the recipient side of a send and drops to 0 after that party reads.
- **Notification fan-out:** publishes `message.sent` (Kafka) on every send; `notification-service`
  gained a new `@KafkaListener` that turns it into an in-app `MESSAGE_RECEIVED` notification for the
  recipient. Verified live: the worker's `/api/v1/notifications` list contained the new-message
  notification after the customer sent the first message.
- **Real bug found and fixed in `notification-service`, not messaging-service:** its Kafka consumer
  factory's `value-deserializer` is `JsonDeserializer` (config-server's `notification-service.yml`),
  which delivers an **already-parsed object**, not a raw JSON string — but every existing
  `@KafkaListener` in `KafkaNotificationConsumer` declares a `String message` parameter and calls
  `objectMapper.readValue(message, Map.class)` on it. That mismatch has apparently existed since
  those listeners were written; it was never caught because **`message.sent` is the first event any
  producer has actually sent live** against that consumer group — `user.registered`/`otp.sent`/
  `payment.completed`/`booking.created` are all still-dead topics with no real producer (same
  situation Elasticsearch was in before search-service). Fixed only the new `handleMessageSent`
  listener, declaring `Map<String, Object>` to match what's actually delivered — did **not** touch
  the other four listeners, since fixing dead code with no live producer isn't verifiable and risks
  a change no one asked for. Apply the same `Map` fix to each when it gets a real producer.
- **Port collision caught before it shipped:** default host port would have been 8096, but a
  concurrently-developed `project-service` (built by a different session working on this same repo)
  already claims `HOST_PORT_PROJECT=8096` in `.env`. Moved messaging's host mapping to 8097 (internal
  container port stays 8096 — only the host-side mapping changed). If you see a port bind failure on
  8096/8097, check both this section and project-service's for which is authoritative now.
- **Not done:** no audit producer on messaging-service itself (messages aren't classified sensitive
  per SRS CP·04/05, so this was a deliberate omission, not an oversight — revisit if that
  classification changes). Announcements and Support now both exist and reuse this thread shape,
  per the build-order notes above.

## support-service — verified live 2026-08-12

CEP itself only had OPS·02 as a stub, so there was no proven implementation to port — this was
original design, not an adaptation.

- **`SupportTicket`** (reporterId, assigneeId nullable, subject, description, category, priority
  `LOW|MEDIUM|HIGH|URGENT`, status `OPEN|IN_PROGRESS|RESOLVED|CLOSED`, closedAt) +
  **`TicketMessage`** for the reply thread (Flyway `V1__initial_schema.sql`, DB
  `civil_engineer_support`), mirroring `MessageThread`/`Message`'s shape rather than inventing a
  second thread model.
- **Endpoints** — `/api/v1/support/tickets`: create, list-mine, get, `PATCH /{id}/status`,
  `POST/GET /{id}/messages`; `/api/v1/admin/support/tickets`: list-all (queue), `PATCH
  /{id}/assign`.
- **Authorization**: reporter/assignee/admin-role can view and reply; only the assignee or an
  admin role can transition status — the reporter reports and replies but does not self-resolve.
  Assigning a ticket auto-transitions `OPEN` → `IN_PROGRESS`. A `RESOLVED`/`CLOSED` (terminal)
  ticket rejects new replies and further status changes.
- **Scope discipline honored**: CRUD + status transitions + reply thread only — no SLA/escalation
  logic, per the gap analysis putting real dispute resolution (TR·03) out of scope.
- Fourth audit producer (after KYC, project-service, escrow/milestone) — ticket create, assign,
  and status-change all publish to `audit-service` via `audit-common`.
- Port 8098 (previously the next free slot after messaging's 8096/8097).
- **Verified live 2026-08-12** through the gateway with real JWTs: ticket create as CUSTOMER →
  non-party WORKER gets 403 on read → reporter attempting to self-resolve gets 403 → ADMIN assigns
  to self (ticket flips to `IN_PROGRESS`) → ADMIN replies → ADMIN resolves (`closedAt` set) →
  reply after resolve gets 400 → all four events show up correctly hash-chained in
  `/api/v1/admin/audit/events?entityType=SupportTicket&entityId=1`.

## audit-service — verified live 2026-08-12

Async, cross-cutting audit trail. Producers publish to Kafka topic `audit.events` via the
`audit-common` starter; `audit-service` consumes and persists — so a slow or down audit-service never
blocks a business transaction.

- **`audit-common`** — Spring Boot auto-configured starter (`AuditAutoConfiguration` registered via
  `META-INF/spring/...AutoConfiguration.imports`) exposing `AuditPublisher`, `AuditEventMessage`,
  `AuditAction`, `AuditTopics`. Any service adds the dependency and injects `AuditPublisher` — no
  Kafka wiring of its own needed (`@ConditionalOnMissingBean` so it won't fight a service that
  already defines its own `KafkaTemplate`, e.g. auth-service/payment-service).
- **`audit-service`** (port 8095, DB `civil_engineer_audit`, Flyway `V1__initial_schema.sql`) —
  `AuditIngestService` (`@KafkaListener`), `AuditQueryService`, `AuditHasher` (shared hashing logic —
  see integrity note below), entities `AuditEvent` / `AccessAnomalyAlert` / `ErasureRequest`,
  `AdminAuditController` (`/api/v1/admin/audit/**`) and `PrivacyController` (`/api/v1/privacy/**`).
- **Hash-chained, append-only.** Each row stores `previous_hash` (the prior row's hash) and its own
  `event_hash`. `GET /api/v1/admin/audit/integrity` recomputes every row's hash from its *current*
  stored content and compares to `event_hash` (catches in-place edits), then re-walks the
  `previous_hash` chain (catches deletion/reordering — a deleted row's neighbours still each hash
  correctly alone, so content verification can't catch that by itself; only the chain check can).
  Both checks verified live: directly `UPDATE`d a row's `before_state` in MySQL → integrity flagged
  `brokenAtEventId` with "row was edited"; directly `DELETE`d a row → flagged with "record was
  deleted or reordered".
- **Bulk-read anomaly detection** verified live: 51 admin reads of the KYC pending queue (crossing
  the default 50-record/10-min threshold) produced exactly one `AccessAnomalyAlert`, deduplicated
  against repeats in the same window.
- **Right-to-access export and erasure requests** verified live: `GET /api/v1/admin/audit/export` and
  the self-service `GET /api/v1/privacy/my-audit-report` both return every event where the caller (or
  queried user) is `subjectUserId`; `POST /api/v1/privacy/erasure-requests` records a request.
- **Instrumented producer so far: `user-service`'s `KycService` only** — CREATE (submit), READ (own
  documents + the admin pending-review queue, with `recordCount` feeding anomaly detection), APPROVE,
  REJECT, each carrying before/after state and the KYC owner as `subjectUserId`. Verified live end to
  end through the gateway with real JWTs: submit → both READs → APPROVE, all four rows appeared
  correctly in the audit log with a valid chain.
- **Wiring done:** root `pom.xml` modules, `config-repo/audit-service.yml`, `docker-compose.yml`
  block (`HOST_PORT_AUDIT:-8095`, depends on mysql + kafka + registry + config-server),
  `database/init/01-create-databases.sql` create/grant, `AUDIT_DB_USERNAME`/`AUDIT_DB_PASSWORD` in
  `.env`, and the gateway route for both paths.

**Not yet audited — the actual gap now, not a build-verification gap:** auth-service
(login/logout/role changes — arguably the single most important thing to audit and still silent),
booking, payment, review moderation, admin-service (including its own UI-config writes, called out
in that section above), and search-service's reindex reads of profile data. Adding a producer to each
is now a small, repeatable change (see `KycService`'s `audit(...)` helper as the template) — it does
not require touching audit-service itself.

## search-service — known limitation, deliberate

The index is rebuilt by **polling** the owning services (`search.reindex-cron`, default every 5
minutes) plus an on-startup rebuild and a manual `POST /api/v1/admin/search/reindex`. None of
auth/user/review publish change events yet, so there is nothing to subscribe to. This **does not meet
the SRS CP·02 requirement of < 60 s index lag** — a profile edit can take up to 5 minutes to appear
in search. Closing that gap means having those services publish domain events (Kafka is already in
the stack) and having search-service consume them for incremental updates; the full rebuild should
stay as a periodic reconciliation safety net.

The rebuild is intentionally full-replace rather than incremental: the dataset is small, and a
partially-failed incremental sync can drift silently, whereas a failed full rebuild just leaves the
previous index in place. Revisit at a scale where deleting and re-adding every document is too
expensive.

Reindex is also resilient by design: each source has a Feign fallback, and a missing rating summary
degrades that profile to rating 0 rather than failing the whole run.

## The "CORS error on login" chain (all fixed 2026-08-11)

The UI's CORS error on Sign In was **three** separate defects stacked, each hiding the next. Worth
knowing because two of them are invisible to curl:

1. **Frontend called the wrong port.** `api.ts` defaulted to `http://localhost:8080` and the dist
   was built with no `VITE_API_BASE_URL`, so the browser hit port 8080 — an unrelated app on this
   host — which returns no CORS headers. Fixed the default to 8087 and added `frontend/.env`.
   **Vite inlines env vars at build time**, so the container's runtime env does nothing for an
   already-built `dist`; the dist must be rebuilt (`npm run build`) and the image rebuilt.
2. **Gateway CORS didn't allow the frontend's origin.** Allowed list was hardcoded to ports
   3000/5173 while the frontend serves on 3007. Now `cors.allowed-origins`, defaulting to the
   localhost *and* 127.0.0.1 forms of 3000/3007/5173 — those are distinct origins to a browser.
3. **Duplicate `Access-Control-Allow-Origin` headers.** Both the gateway and auth-service set CORS,
   so responses carried the header twice; browsers reject that ("contains multiple values, but only
   one is allowed") while **curl happily reports 200**, which makes it easy to misdiagnose as fixed.
   The gateway's `CorsWebFilter` runs outside the route filter chain, so `DedupeResponseHeader`
   cannot strip the downstream copy. Fixed by making the **gateway the sole owner of CORS** and
   disabling it in auth-service. *Any new service must not add its own CORS config.*

Also note the **PWA service worker** caches the JS bundle aggressively — after rebuilding the
frontend, an unregister + `caches.delete()` (or hard reload) is needed or the browser keeps serving
the old bundle with the old API URL.

Debugging lesson: verify browser-facing behaviour **in a browser**. Every one of these returned a
clean 200 to curl.

## Notification channels — email, SMS, WhatsApp (2026-08-15)

`notification-service` delivers over four channels. Each has a `provider` setting taking either
its real provider or `log` (the message is written to the service log and nothing leaves the
cluster). **A real provider whose credentials are still the config-repo `placeholder` degrades to
logging rather than throwing** — a half-configured environment never breaks OTP login or booking.

| Channel | Provider setting | Real provider | Credentials |
|---|---|---|---|
| Email | `app.email.provider` (`EMAIL_PROVIDER`) | `smtp` (JavaMail) or `brevo` (HTTP API) | SMTP: `SMTP_HOST`/`SMTP_USERNAME`/`SMTP_PASSWORD`. Brevo: `BREVO_API_KEY` alone |
| SMS | `app.sms.provider` (`SMS_PROVIDER`) | Twilio | `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_PHONE_NUMBER` |
| WhatsApp | `app.whatsapp.provider` (`WHATSAPP_PROVIDER`) | Twilio WhatsApp | the same Twilio account + `TWILIO_WHATSAPP_FROM` |
| In-app | — | a `notifications` row | — |

- **Email has two providers.** Brevo's SMTP relay needs a *login* as well as an SMTP key (two
  separate dashboard values), whereas its HTTP API needs only `BREVO_API_KEY` — so
  `EMAIL_PROVIDER=brevo` can be configured from one secret. `BrevoEmailSender` posts to
  `https://api.brevo.com/v3/smtp/email`. **Verified live 2026-08-15**: a real OTP email delivered,
  Brevo returning a `messageId`.
- `EMAIL_FROM_ADDRESS` must be a **verified sender** on the relay account — Brevo, SendGrid and
  friends all reject anything else with a 400. It used to be hardcoded to
  `noreply@civilengineer.com`, which no real account owns; it is now per-environment.
- `TwilioGateway` initialises the Twilio SDK once (it is process-wide static state) and is shared
  by SMS and WhatsApp: same Messages API, the channel is selected purely by the `whatsapp:` address
  prefix.
- `PhoneNumbers` normalises stored national numbers to E.164 before dispatch
  (`SMS_DEFAULT_COUNTRY_CODE`, default `+91`) — Twilio rejects anything else.
- **The Thymeleaf email templates did not exist before this change.** `EmailService` referenced
  `otp-template`, `welcome-template`, `booking-confirmed-template` and `payment-received-template`,
  all of which threw at render time and were swallowed by the catch-and-log — every email was a
  silent non-delivery. They now live in `resources/templates/email/` over a shared `_layout.html`,
  covered by `EmailTemplateRenderTest` so a broken fragment fails the build instead of going quiet.
- `NotificationDispatcher` + the `NotificationRequest` record are the single fan-out entry point;
  which channels an event uses is decided per event type in `KafkaNotificationConsumer`.
- **Pre-existing bug found and FIXED:** every Kafka listener except `message.sent` declared a
  `String` parameter while the consumer factory's `value-deserializer` is `JsonDeserializer`, which
  delivers a parsed object. All listeners now take `Map<String, Object>`.
- **Placeholder detection matters more than it looks.** The "unconfigured → fall back to logging"
  guard originally matched only the literal word `placeholder`, so the `your_email@gmail.com` /
  `ACXXXX…` values that ship in `.env.example` were treated as *live* credentials — Twilio would
  init with garbage and every send threw into the catch-and-log. Twilio is now validated by real
  shape (`AC` + 32 hex, token 32 hex) and SMTP rejects template markers, both pinned by
  `CredentialDetectionTest`.
- **Pre-existing bug found and FIXED:** the OTP resend cooldown throws `IllegalStateException`,
  which had no handler and fell through to the catch-all — callers got a 500 "unexpected error"
  instead of "Please wait N seconds", which is the entire point of the response. Now a 429.
- `POST /api/v1/admin/notifications/dispatch` (admin roles only) sends over any channel on demand —
  the "did the credentials land?" check that avoids provoking a real booking or payment.

### OTP over all three channels

`OtpChannel` (`EMAIL`, `SMS`, `WHATSAPP`) drives both registration verification and OTP sign-in.
Auth never touches a provider itself: it stores the code and emits `otp.sent`, and
notification-service delivers it.

- **Registration** takes `verificationChannel` (defaults to `EMAIL`) and sends a verification code
  as part of `POST /api/v1/auth/register`. The frontend then shows a verification step —
  registration already returns a session, so it offers "Skip for now" rather than gating access.
- **Sign-in** offers password *or* OTP, and the OTP tab now picks Email / SMS / WhatsApp.
  `POST /api/v1/auth/otp/send` takes an optional `channel`; without one it defaults to the channel
  the identifier implies.
- **Seeded phone numbers are stored in E.164** (`+91…`), matching what real registrations save:
  the frontend converts before submitting, so the previously-bare national numbers made every
  seeded account fail phone OTP with "Mobile number not registered". The seeder migrates existing
  bare numbers on startup.
- Codes are keyed `email:<userId>` or `phone:<userId>`. SMS and WhatsApp share the phone key —
  both are delivered to the same number and prove the same thing — so `otp/verify` needs no
  channel and is keyed on the identifier alone.
- A code sent to one identifier still cannot be replayed against the other.

## One account per email / phone — no duplicates (2026-08-15)

Enforced at three layers, because each one alone has a hole.

**1. Canonical form (`AccountIdentifiers`).** A uniqueness check is only as good as the string it
compares. `9493564235`, `+91 94935 64235` and `09493564235` were three distinct values that each
passed the "phone already registered?" check, and `Ravi@x.com` could slip past a lookup for
`ravi@x.com`. Every write path now normalises first — email lowercased/trimmed, phone to E.164 via
`app.phone.default-country-code` (default `+91`). Unparseable input is returned unchanged so it
surfaces as a validation error rather than a bogus "already registered".

**2. Service-layer checks on every write path.** Registration already checked both identifiers.
`AdminUserController.updateUser` checked **neither** — an admin could assign an in-use email or
phone to another account (email hit the V1 constraint as a 500; phone silently duplicated). It now
excludes the user's own row so a no-op edit doesn't clash with itself. `CustomOAuth2UserService`
normalises the provider-supplied address, so a Google login for `Ravi@x.com` resolves to the
existing `ravi@x.com` account instead of creating a second one.

**3. Database constraints.** `email` has been UNIQUE since V1; **`phone` had only a plain index**,
so nothing stopped two concurrent registrations both passing the check and both inserting. Flyway
`V2__unique_phone.sql` normalises legacy national-format numbers to E.164 and adds `uk_user_phone`.
NULLs stay allowed (OAuth2 signups have no phone). A constraint hit now maps to a 409 via
`DataIntegrityViolationException` rather than a 500.

Soft-deleted rows still occupy their identifiers, matching how the V1 email constraint already
behaved; freeing one requires a hard delete.

Verified live 2026-08-15 — rejected: duplicate email in different case, duplicate phone as bare vs
E.164, admin reassigning an in-use phone, admin reassigning an in-use email. Accepted: a genuinely
new account, and an admin no-op edit of a user's own number. Normalisation rules are pinned by
`AccountIdentifiersTest`.

Known nicety, not a hole: a formatted number like `+91-94935-64235` is rejected by
`RegisterRequest`'s `@Pattern` before normalisation runs, so it reads as "Invalid phone number
format" rather than being cleaned up. No duplicate can result; the frontend always submits E.164.

## Nav and theme stale until refresh after login — FIXED (2026-08-15)

**Symptom:** signing in did not update the side nav, top nav or theme; they only corrected
themselves after a manual page refresh.

**Cause:** `UiConfigProvider` cached the snapshot under a bare constant key, `['ui-config','me']`,
with no user identity in it and nothing clearing it on sign-out. Sign out, sign back in as a
different role, and React Query found a cache entry for that key that was still inside its
5-minute `staleTime` — so it served the *previous* user's menu and theme and did not even refetch.
A full page reload drops the in-memory cache, which is exactly why refreshing "fixed" it.

Worth being precise about what was **not** the cause: the request interceptor reads the token from
the Redux store, and the login reducer sets `isAuthenticated`, the token and localStorage in one
synchronous reducer. A first login in a fresh tab always fetched correctly — only the second
sign-in in the same tab was wrong.

**Fix:** the query key now carries the user id (`['ui-config','me',<id>]`), so a different user is
a cache miss; and sign-out calls `removeQueries` on the prefix, so a snapshot cannot outlive the
session it belongs to (`gcTime` is 30 minutes). `UI_CONFIG_QUERY_KEY` stays exported as the prefix
— `invalidateQueries` matches by prefix, so every existing `refresh()` caller is unaffected.

How visible this was: SUPER_ADMIN resolves to 15 menu items with primary `#667eea`, CUSTOMER to 4
with `#ff5722`, so a stale snapshot left admin-only nav entries on a customer's screen.

## Dummy dev logins

Seeded by `auth-service`'s `DevUserSeeder` (`@Profile({"local","docker"})` — never runs in any other
environment; `docker-compose.yml` sets `SPRING_PROFILES_ACTIVE=${AUTH_PROFILE:-docker}` on
auth-service, so set `AUTH_PROFILE=` to get a clean environment). Idempotent: skips accounts that
already exist, except the SUPER_ADMIN, which is reconciled onto the address and password below on
every startup (an environment seeded before the change is migrated off `superadmin@civileng.test`
rather than gaining a second SUPER_ADMIN). **Password for all accounts: `Password123!`, except the
SUPER_ADMIN — see its own password in the table.**

| Role | Email | Password |
|---|---|---|
| SUPER_ADMIN | rajkumarbandaruit@gmail.com (mobile `+919493564235`) | `Testing@123` |
| ADMIN | admin@civileng.test | `Password123!` |
| CUSTOMER | customer@civileng.test | `Password123!` |
| WORKER | worker@civileng.test | `Password123!` |
| LABOUR | labour@civileng.test | `Password123!` |
| LABOUR_CONTRACTOR | contractor@civileng.test | `Password123!` |
| CIVIL_ENGINEER | engineer@civileng.test | `Password123!` |
| ARCHITECT | architect@civileng.test | `Password123!` |
| SURVEYOR | surveyor@civileng.test | `Password123!` |
| MATERIAL_SUPPLIER | supplier@civileng.test | `Password123!` |

Log in through the gateway to get a JWT, then pass it as `Authorization: Bearer <token>`:

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"customer@civileng.test","password":"Password123!"}'
```

The gateway's JWT filter turns that token into the `X-User-Id`/`X-User-Role`/`X-User-Email`/
`X-User-Name` headers every downstream service reads — always test through the gateway (see the
port note above) rather than hand-setting those headers against a service's own port, or you bypass
the auth path entirely.

## Adding a new microservice — checklist (learned the hard way building `review-service`)

Missing any of these produces a confusing startup failure rather than an obvious error:

1. `<module>` entry in the root `pom.xml`.
2. Service `pom.xml`, `Dockerfile` (copy `booking-service`'s), `src/main/resources/application.yml`
   (config-client bootstrap only), and `src/test/resources/application.yml` (disables config/Eureka).
3. **`config-server/src/main/resources/config-repo/<service>.yml`** — this holds the real datasource,
   Eureka, and JPA config. The service will fail with *"Failed to determine a suitable driver class"*
   if it's absent or not deployed.
4. **Rebuild and redeploy `config-server` after adding that file** — it is served from the
   config-server's own classpath, so a new config file does nothing until config-server's jar is
   repackaged and its container recreated. `mvn -pl <newservice> -am package` does **not** rebuild
   config-server (it isn't a dependency), which makes this easy to miss.
5. `CREATE DATABASE` + `GRANT` in `database/init/01-create-databases.sql` **and** run the same SQL
   by hand against the running MySQL — the init script only executes on a *fresh* volume, so an
   existing stack will never pick it up. Root password is in `civil_mysql`'s `MYSQL_ROOT_PASSWORD`
   env var (a random hex string, not the compose-file default).
6. **`<SERVICE>_DB_USERNAME` / `<SERVICE>_DB_PASSWORD` in `.env`** — there is a real `.env` whose
   DB password is a shared random hex string, *not* the `civil_pass` default written into
   `docker-compose.yml`. Omitting these yields `Access denied for user 'civil_user'`.
7. `docker-compose.yml` service block with `depends_on` on mysql + `service-registry` +
   `config-server` (the latter two `condition: service_healthy`).
8. `api-gateway` route in `GatewayConfig`. **Route order matters** — Spring Cloud Gateway matches in
   declaration order, so a narrower path must precede a broader one. `/api/v1/bookings/*/reviews`
   is declared *before* the `/api/v1/bookings/**` booking-service route, or booking-service
   swallows it.

Also: `@Valid` on a `@RequestBody` runs *before* the controller body, so a DTO field you intend to
populate from a `@PathVariable` must not be `@NotNull` on the DTO — validation fails first.

## Multi-tenancy — verified live 2026-08-18

Schema-per-tenant, subdomain-resolved, with a per-tenant module set. Tenants are white-label
platform operators: `acme` (civil marketplace), `hostelfee` (Hostel Fee Collection Platform,
`FEE_COLLECTION` vertical), `bhoomi` (Bhoomi360, `PROPERTY` vertical), plus the `platform`
operator tenant that owns tenant administration.

**Why schema-per-tenant rather than a `tenant_id` column:** a missed query filter in a shared
schema is a silent cross-tenant read, and there are ~45 entities across 11 services to get right.
A wrong schema name fails loudly instead. The cost is that Flyway must run per tenant per service
and cross-tenant reporting cannot `JOIN` — see the open items below.

- **`tenant-common`** — the whole runtime, added to a service with one dependency plus a
  `platform.tenant` config block. `TenantContext` (ThreadLocal, no ambient default),
  `TenantHeaderFilter` (400s an untenanted request), a Hibernate
  `MultiTenantConnectionProvider` that switches the connection catalog per checkout **and resets
  it on release** (a pooled connection handed back still pointing at tenant A is exactly how the
  next tenant reads the wrong data), per-tenant Flyway, a `tenant.events` listener, a Feign
  interceptor, Kafka producer/record interceptors, and `CrossTenantRunner` for scheduled jobs.
- **`tenant-service` (8099)** — the registry, deliberately *not* tenant-scoped. Other services
  read its database over plain JDBC at boot rather than over HTTP: they need the tenant list
  before their EntityManagerFactory exists, and an HTTP dependency would put tenant-service in
  every service's startup path.
- **Tenant administration is gated on the operator tenant, not on a role.** A tenant's own
  SUPER_ADMIN must not be able to create or suspend a sibling tenant; `platform` + `SUPER_ADMIN`
  is required. Verified: 403 for a SUPER_ADMIN of `acme`, 403 for a non-admin of `platform`.
- **Off-request paths all had to be handled explicitly**, and each was a real hole:
  `@Scheduled` jobs (escrow auto-release, announcement release) now fan out via
  `CrossTenantRunner` or they would only ever serve the bootstrap tenant; Kafka records carry the
  tenant as a header stamped by a `ProducerInterceptor` and bound by a `RecordInterceptor`; Feign
  calls propagate the header because service-to-service traffic does not pass the gateway.
  A record with **no** tenant header is dropped rather than processed, since defaulting it would
  write one tenant's data into another's schema.
- **Grants** — schemas are created at runtime, so fixed per-database grants cannot cover them.
  `docker/database/init/02-tenant-grants.sql` adds pattern grants for `civil\_engineer\_%` and
  `admin\_db\_%` (admin-service is the one service outside the common prefix).

**Pre-existing bug found and FIXED (2026-08-18):** notification-service's migration set was not
reproducible from scratch. `V1` creates `email_templates` with an old shape
(`template_name`/`body_html`/`is_active`) and `V3` — which defines the real one the entity maps —
guards its `CREATE` with `IF NOT EXISTS`, so on a fresh schema V3 silently keeps V1's table and
the service fails at runtime with `Unknown column 'active'`. The legacy single-tenant database
escaped it because V1 was applied there before that block was added to the file. Fixed additively
in `V7__fix_email_templates_shape.sql` rather than by editing an applied migration. Nothing was
lost: V3 deliberately seeds no rows (built-ins load from the classpath), and V1's seed used
columns the code never reads.

**Pre-existing bug found and FIXED (2026-08-18):** `audit-common` builds its own `ProducerFactory`
from scratch, so `spring.kafka.producer.*` never reaches it. Audit events therefore arrived at
audit-service with no tenant header and were dropped. The interceptor is now wired into that
factory by class name, guarded by a classpath check so audit-common still works in a service
without tenant-common.

**Build trap:** Maven does **not** repackage a service's fat jar when only a dependency
(`tenant-common`, `audit-common`) changed — `classes/` is unchanged, so the jar plugin considers
the jar up to date and the service silently ships the old library. Always `mvn clean install`
after touching a shared module, then rebuild images.

**Config trap:** `config-repo/auth-service.yml` is a multi-document YAML with an
`on-profile: social` document after a `---`. Appending config to the end of that file lands it in
the inactive profile and it is never served. Check for `---` before appending to any config-repo
file.

**Not built (deliberate, and why):**

1. **No cross-tenant admin views.** Schema-per-tenant cannot `JOIN` across tenants, so the
   operator console's "all tables with a tenant filter" needs a fan-out query layer that runs a
   query per schema and merges/pages in memory. Not started.
2. **The `FEE_COLLECTION` and `PROPERTY` verticals are registry entries only.** Their module keys
   (`residents`, `feeplans`, `invoices`, `collections`, `properties`, `listings`, `leases`,
   `valuations`, `landrecords`) exist in `PlatformModule` and are gated at the gateway, but no
   entities, services or UI have been built behind them.
3. **No tenant-aware frontend.** The shell does not yet read `/api/v1/tenant-resolution` for
   per-tenant branding or hide menu items for disabled modules.
4. **The legacy single-tenant schemas still exist** (`civil_engineer_users`, etc.) and hold the
   pre-multi-tenancy data. Nothing reads them now; no migration of that data into a tenant schema
   has been done.

## Tenant administration UI — built 2026-08-18

`tenant-service` had exposed the operator API since multi-tenancy went in, but nothing in the
console called it: onboarding a tenant meant a hand-written curl request, and the
`platform`-SUPER_ADMIN rule was something an operator had to be told rather than shown. The
console now has a **System → Tenants** tab.

- `frontend/src/services/tenantApi.ts` — client for the four operator endpoints, plus the
  `PlatformModule` / `Vertical` / `TenantStatus` mirrors the screen renders from.
- `frontend/src/pages/admin/TenantManagement.tsx` — list, create dialog, and a per-tenant
  status + module editor.
- `admin-service` Flyway `V18` seeds the `admin-tenants` menu row (System group, sort 175, so it
  sits immediately before Workspaces — the two get confused, and a tenant contains workspaces).
- Route and `FALLBACK_NAV` entry added in `App.tsx` and `AdminLayout.tsx`.

**Tenants vs workspaces.** Worth stating because the naming invites the mistake: a *tenant* is a
whole customer with its own schemas and subdomain; a *workspace* is one role inside a tenant, and
"Workspaces" only configures that role's menu and theme.

**The 403 is the authorization, not the menu row.** `default_roles` is `SUPER_ADMIN`, but
tenant-service independently requires `X-Tenant-Id` be `platform` — so a tenant's own Super Admin
sees the tab and gets a 403 from every call behind it. The screen renders that 403 as an
explanation rather than an empty table, which is the honest presentation: hiding the tab client-side
would imply the rule is about the role when it is about the tenant.

**Module editing deliberately omits the horizontal set.** `auth`, `users`, `payments`,
`notifications`, `support`, `admin`, `audit` and `messaging` render as read-only chips. They are
shown, because an operator asking "what does this tenant have?" wants the whole answer, but they
are not checkboxes — switching off `auth` breaks the tenant outright and should not be one
misclick away from the modules the operator came to change. The PUT always re-sends them.

**Verified live** through the gateway with a real `platform` SUPER_ADMIN token: menu row served in
`/api/v1/ui-config/me`, `GET /tenants` 200, `POST /tenants` 201 (created `uitest` on the
`FEE_COLLECTION` vertical, default modules resolved from the vertical), `PUT
/tenants/uitest/modules` 200, `PATCH /tenants/uitest/status` 200, and `GET /tenants` as an ADMIN
403. `tsc --noEmit` and the production bundle both build clean.

**Left behind:** the test tenants `uitest` (SUSPENDED), `brandy`, `brandytwo` and `plainco` are
still there, with schemas provisioned in all 11 services. There is no delete endpoint — dropping
one means removing the row and its 11 schemas by hand.

## Gateway truncated every proxied response — FIXED 2026-08-18

Symptom in the browser: `net::ERR_INCOMPLETE_CHUNKED_ENCODING`, with logins appearing to fail
intermittently. A third pre-existing multi-tenancy bug, alongside the two recorded above.

`TenantResolutionGlobalFilter.filter()` ended with a `.switchIfEmpty(... reject ...)` placed
*after* the `flatMap` that calls `chain.filter()`. `chain.filter()` returns `Mono<Void>`, which
completes **empty on every successful proxied request** — so the "no workspace is served at this
host" rejection fired after the downstream response was already committed. `reject()` then called
`add()` on the now-read-only response headers, threw `UnsupportedOperationException`, and Netty
closed the connection mid-body. Every response through the gateway was being truncated; only
responses the browser parsed strictly showed it.

Fixed in three parts:

1. The no-tenant branch moved *inside* the `flatMap` (via an `Optional` wrap), so it cannot be
   reached by a request that succeeded. This is the actual fix — a `switchIfEmpty` after a
   `Mono<Void>` is always wrong, because "completed empty" is what success looks like there.
2. `reject()` returns early if the response is already committed, logging instead of corrupting
   the stream. Defense in depth for the next filter that gets this wrong.
3. The `Host` header's port is stripped before the tenant lookup. `localhost:8080` was never
   going to match a tenant keyed by hostname, so every local request paid a tenant-service round
   trip and a 400 before falling back.


## Tenant branding at onboarding — built 2026-08-18

The create form now also takes the tenant's **logo, colours and UI styling**, and the tenant's
console comes up wearing them the first time anyone signs in.

**Why this needed a path through Kafka rather than a second API call.** The obvious
implementation — create the tenant, then POST its theme — cannot work here, and the reason is
worth writing down. The operator filling the form is on the `platform` tenant; the new tenant's
`admin_db_<key>` schema does not exist yet, because provisioning is asynchronous; and the gateway
strips any caller-supplied `X-Tenant-Id`, which is what stops a header being a cross-tenant write.
So there is no request the operator could make that lands in the new tenant's schema. The branding
travels on the `tenant.events` message instead and is applied by admin-service the moment Flyway
has built the schema.

**The pieces:**

- `tenant-common`: `TenantBranding` (the shape, plus validation), carried on `TenantEventMessage`.
- `tenant-common`: `TenantProvisionedCallback`, a hook `TenantProvisioningListener` runs after a
  successful migrate. Each callback is isolated — a service failing to seed its own optional rows
  must not turn a provisioned tenant into a logged failure.
- `tenant-service`: nine nullable columns on `tenants` (Flyway `V2`), validated on create and
  echoed back on every read.
- `admin-service`: `TenantThemeSeeder` implements the callback and writes the tenant's `PLATFORM`
  theme row, inside `TenantContext.runAs` because the Kafka thread carries no tenant.
- `frontend`: the create dialog is now two tabs — Identity and Branding — with colour pickers, a
  live logo preview, and the style dropdowns. The tenant detail screen shows what a tenant was
  onboarded with, read-only.

**Validation is in tenant-common, not just the form.** An unknown UI style would be stored happily
and then silently fall back to the default at render time — "saved but did nothing", which is
expensive to diagnose. `TenantBranding.validate()` refuses it at the boundary with the field named,
so it is a 400 on create.

**The closed sets are duplicated, and that duplication is tested.** tenant-common cannot depend on
admin-service, so `TenantBranding` restates `UI_STYLES`, `BUTTON_STYLES`, `LAYOUT_STYLES`,
`COLOR_MODES` and `DENSITIES`. `TenantBrandingOptionsTest` (admin-service, 5 tests) asserts each
list equals admin-service's original, so adding a style in one place and forgetting the other
fails the build instead of producing a tenant nobody can explain.

**Bug found and fixed while testing this:** the seeder's first version skipped when a `PLATFORM`
theme row already existed — which is *always*, because admin-service's own `V2__uiconfig.sql`
seeds one for every schema. Branding was accepted, stored, published, and then silently dropped.
The row's `version` is the signal that actually works: `UiConfigService.updateTheme` bumps it on
every save, so version 1 means untouched default and anything higher means the tenant has chosen
for itself. The seeder now overwrites at version 1 and sets it to 2, which also makes a redelivered
provisioning event a no-op.

**Seeding only, never re-pushing.** Branding rides the creation event alone, deliberately: a
status change re-publishing it would overwrite a tenant's own colours every time an operator
suspended and reactivated them. The detail screen therefore shows branding read-only and points at
the tenant's own Theme & UI style screen, which owns it from provisioning onward.

**Also fixed:** `Tenant.createdAt` was `insertable = false`, so a create returned `createdAt: null`
and the caller had to re-read the tenant to learn when it was made. Now `@CreationTimestamp`, and
the console shows a Created column and a created timestamp on the detail header. Separately,
Lombok's `isEmpty()` on `TenantBranding` was serialising as an `"empty": false` field on every
tenant response — `@JsonIgnore`d.

**Verified live** through the gateway: branded create 201 with every field echoed back and the
theme row landing in `admin_db_brandytwo` (all nine values, `brand_name` defaulted to the tenant
name, version 2); unbranded create leaving `admin_db_plainco` on the shipped default at version 1;
`primaryColor: "blue"` → 400 naming the field; `uiStyle: "neon"` → 400 listing the allowed values.
`tsc --noEmit`, the production bundle, and the 5 drift tests all pass.

## Tenant console review pass — 2026-08-18

Eight issues found by reviewing the built screens against real screenshots, all fixed.

**1. Branding could not be changed after creation.** The largest gap: every tenant created before
branding existed (`acme`, `platform`, `bhoomi`, `hostelfee`) had no path to branding at all. Now
`PUT /api/v1/tenants/{key}/branding` (operator-only), and an **Edit branding** dialog on the tenant
detail screen.

The interesting part is the guard. Seeding a tenant that has never touched its theme is harmless;
replacing colours a tenant chose for themselves is not — and the operator cannot tell which one
they are about to do, because the theme lives in the tenant's schema. So admin-service exposes
`GET /api/v1/admin/tenant-theme/{key}` (same operator-only rule, returns only a version and a
boolean — never the tenant's actual colours), and the dialog says plainly which case this is before
the operator commits. The `tenant.events` message carries a `brandingUpdate` flag so
`TenantThemeSeeder` knows an explicit edit may overwrite where a creation seed may not.

`TenantContext.callAs` was added alongside `runAs` for this — reading one value out of another
tenant's schema, with the same restore-what-was-bound contract.

**2. The colour swatches lied.** An empty colour field rendered a blue swatch, which reads as
"primary is already blue" when nothing is set. Unset now shows a muted hatched tile, and a set
colour gets a clear button to go back to inheriting.

**3. "Create tenant" greyed out without saying why.** With an invalid email and the Branding tab
open, the button was dead and nothing on screen explained it. The blocking reason is now named next
to the button, clicking it jumps to the offending tab, and the tab carrying the error is marked.

**4. `PENDING` was offered as a status button.** It means "created but not yet provisioned" — a
state the platform sets and clears itself. Offering it let an operator move a live tenant into a
state that describes something untrue and changes nothing. Removed from the buttons; still rendered
as a status.

**5. No preview.** Nine dropdowns and three hex fields described a result the operator could not
see — they cannot sign in as the tenant to check. There is now a live miniature of the shell
(sidebar placement, colours, density, radius, button fill) beside the controls, labelled
approximate.

**6. The vertical picker hid its consequences.** Choosing one silently decides a dozen modules;
they are now listed as chips under the dropdown at the moment the choice is made.

**7. Header printed the key twice** (`acme · acme · ops@acme.test`) because subdomain equals key
for most tenants. Subdomain and custom domain now show only when they differ.

**8. Cramped dialog** — `sm` with everything in one column. Now `md`, two columns, preview beside
the controls.

**Also:** the tenant list gained a search box (name, key, subdomain, contact — an operator chasing
a ticket usually has the email, not the key) and a status filter; and **Save modules** now confirms,
naming the modules being removed, because removing one starts 404ing that tenant's users
mid-session.

**Bug found while testing this:** the new theme-status endpoint threw `ResponseStatusException`,
which admin-service's `GlobalExceptionHandler` does not recognise — so an unauthorised caller got a
500 instead of a 403. The service has its own `AccessDeniedException` mapped to 403; using the
framework's exception in a codebase with its own handler chain silently produced the wrong status.

**Verified live** through the gateway: `theme-status` reporting `customised:false` for a
never-touched tenant and `true` for a seeded one; branding set on `acme` (which had none) landing
in `admin_db_acme` with version 2; an overwrite of `brandytwo`'s already-customised theme landing
at version 3; `primaryColor: "red"` → 400; and both new endpoints returning 403 to an ADMIN.
`tsc --noEmit`, the production bundle, and the 5 drift tests all pass.

**Still not built:** there is no tenant delete. The test tenants `uitest`, `brandy`, `brandytwo`
and `plainco` remain, each with schemas in 11 services; removing one means dropping the row and its
schemas by hand.

## Tenant colours in the list — 2026-08-18

The list now shows each tenant's chosen colours: a **Brand** column with the logo and up to three
swatches (primary, accent, sidebar, each with the hex on hover), and the tenant's primary colour as
a stripe down the left edge of its row. A tenant with no branding reads "Platform theme" rather
than showing an empty cell.

Colour is the fastest way to tell tenants apart once there are more than a handful — an operator
recognises "the green one" well before they read a key.

**The colour is applied as an edge, not to text or chips**, deliberately: a tenant-chosen colour is
arbitrary, and using it for foreground or fill would fight the console's own light/dark theme and
could land unreadable. The edge is legible whatever the tenant picked.

**Caveat worth knowing:** these swatches show what an *operator* set as the tenant's branding, which
is what tenant-service stores. A tenant that has since changed its own theme will not be reflected
here — the tenant's live theme lives in its own schema, and the operator console only reads a
version from it (`GET /api/v1/admin/tenant-theme/{key}`), never the colours.

**Extended 2026-08-18:** the colour now runs across the whole row, not just the edge — the tenant's
primary washed over the row background, a filled dot beside the name, the full-strength left edge,
and the Configure action in the same colour. The row reads as that tenant's row at a glance.

The wash is deliberately faint (7% in light, 14% in dark, roughly doubled on hover). Stronger than
that and the row's own text stops meeting contrast against a colour the *operator* picked, not the
designer — the name and contact still have to be readable on every row. For the same reason the
name itself is never coloured; the dot carries the identity instead.

`rowAccent()` trims a stored colour to six digits before it reaches MUI's `alpha()`, which cannot
parse the `#RRGGBBAA` form the columns allow and would throw — taking the whole list down rather
than one cell.

## search-service tenant isolation — built 2026-08-19

The last isolation gap. search-service is the only store on the platform that is not MySQL, so it
inherits nothing from `tenant-common`'s schema-per-tenant layer — its indices were platform-wide
and a search from tenant A would have returned tenant B's profiles.

**Isolation is one index per tenant**, not a tenant field filtered in the query. `profiles_acme`,
`services_acme`. A query physically cannot reach another tenant's documents; a filter would put the
boundary at the mercy of every future edit to the nine query builders, and one forgotten `filter`
would be a cross-tenant read that no test would notice.

- `search/config/TenantIndex.java` — the only place a tenant becomes an index name. Derived from
  `TenantContext.require()`, which throws when nothing is bound, so background work that forgot
  `runAs` fails loudly instead of resolving to a shared index.
- Both documents moved to `indexName = "#{@tenantIndex...}"`, `createIndex = false` — there is no
  single index to create at startup, and startup has no tenant bound.
- The two `ElasticsearchRepository` interfaces are **deleted**. Spring Data repositories resolve
  their index once, from the entity; every read and write now goes through `ElasticsearchOperations`
  with explicit `IndexCoordinates`, so the tenant a write lands in is visible at the call site.
- `ReindexService` sweeps per tenant via `CrossTenantRunner`. Its Feign reads were previously made
  with no tenant header at all — which the tenanted services correctly answer with 400 — so the
  index it left behind was pre-tenancy data.
- `SearchTenantProvisioner implements TenantProvisionedCallback`: a new tenant's indices are built
  the moment it goes ACTIVE, instead of being unsearchable until the next 5-minute sweep. This is
  why `spring.kafka.bootstrap-servers` is now set for this service.
- `POST /api/v1/admin/search/reindex` rebuilds the caller's own tenant only. The cross-tenant sweep
  stays on the scheduler — a tenant admin must not be able to spend the platform's reindex budget
  on, or learn document counts of, another tenant.
- A tenant queried before its first reindex has no index; `NoSuchIndexException` is served as an
  empty result set rather than a 500, so a newly onboarded tenant does not look broken.
- `platform.tenant.enabled=true` with a `registry` block but **no `schema-prefix`** — the tenant
  context, header filter and Feign propagation, none of the MySQL schema layer. The MySQL driver is
  a runtime dependency purely so `TenantRegistry`'s plain-JDBC read of the tenant list works.
- Compose: search-service gains `mysql` + `kafka` deps and `TENANT_DB_*`.

**Verified live 2026-08-19.** Per-tenant indices exist with genuinely different contents
(`services_platform` 21 docs vs `services_brandy` 10; `profiles_platform` carries two users
`profiles_brandy` does not). Through the gateway: a brandy token on `platform.localhost` is 403
("Token is not valid for this workspace"); `acme.localhost` — which has no `search` module — is 404
before authentication even runs; a client-supplied `X-Tenant-Id: platform` on `brandy.localhost` is
stripped and still returns brandy's 10; and a direct call to :8092 with no tenant header is 400
rather than a silent shared read.

### Follow-ups — closed 2026-08-20

The three loose ends left after the build above (backlog replay, a SUSPENDED tenant's indices, the
pre-tenancy platform-wide indices) are fixed. A fourth, a real race, turned up while verifying.

- **Tenant-event backlog replay.** `tenant-common`'s consumer was `auto-offset-reset=earliest` on a
  group with no committed offsets, so every service re-provisioned every tenant that had ever
  existed on boot — in search-service's case a full reindex per tenant several times over, on top
  of the startup sweep. Now `latest`. Safe because every consumer already reconciles the whole
  tenant list at startup (schema bootstrap elsewhere, the reindex sweep here); the events only need
  to carry tenants created *while the service is running*, which is exactly what they now do.
  This is a `tenant-common` change, so the other services pick it up on their next image build.
- **`IndexPruner`** deletes any `profiles*`/`services*` index no active tenant owns, at the end of
  each sweep. That covers both a suspended/deleted tenant's leftovers and the pre-tenancy
  platform-wide `profiles`/`services`. Safe to delete because the indices are a read replica — a
  tenant returning to ACTIVE is rebuilt by the provisioning callback and the next sweep. It refuses
  to run when the registry read fails *or comes back empty*, which is far more likely to be a bad
  read than a platform with no tenants and would otherwise delete everything. It only considers
  names this service creates; anything else in the cluster is left alone.
- **Race found: a new tenant's first index build outran the services it reads from.** All the
  services provision the same tenant from the same Kafka message at the same moment, so
  search-service was calling auth-service against a schema Flyway had not migrated yet and taking
  the 500 as a failed provision — the tenant then had no index until the 5-minute sweep.
  `SearchTenantProvisioner` now runs off the listener thread and retries
  (`search.provision-retries: 5`, `provision-retry-delay-seconds: 15`), with the sweep still the
  backstop. `spring.task.scheduling.pool.size: 2` so a retry and the sweep do not queue behind each
  other.

**Verified live 2026-08-20.** Boot logged exactly one reindex per tenant and zero replayed
provisioning events (previously several rounds of both), and pruned `profiles`, `services`,
`profiles_uitest`, `services_uitest` — leaving exactly the 16 indices the 8 active tenants own. A
tenant created against the running stack reproduced the race (attempt 1 got auth-service's 500) and
succeeded on attempt 2, 15s later. Suspending two test tenants had their four indices pruned by the
next sweep. Search through the gateway still returns platform's profiles and services.

## Admin surface: role gate, duplicate removal, `web-common` — 2026-08-21

Started as a de-duplication pass and turned up a hole first.

**The hole.** Nothing checked the caller's role on the admin surface. The gateway's
`JwtAuthGatewayFilterFactory` *injected* `X-User-Role` but never enforced it, and all nine
controllers under `admin.controller` — users, bookings, invoices, categories, dashboard, analytics,
revenue, reports, service catalogue — had no check of their own. booking-service's own
`AdminBookingController` had none either and went straight to the repository. Any member with a
valid token for the workspace could list and delete users, cancel or complete any booking, and edit
the service catalogue. Only the newer packages (`uiconfig`, `settings`, `content`) were guarded,
which is why the admin/member split verified in the UI-config section held while this did not: the
check was per-handler and opting in had simply been forgotten as controllers were added.

**Two layers, both required.**

- **Gateway** — `JwtAuthGatewayFilterFactory` now refuses `/api/v1/admin/**` for a non-staff role,
  matched on a segment boundary so a future `/api/v1/administrators` is unaffected. Placed on the
  prefix rather than per route, so a new admin route is gated the moment it is added.
- **Service** — `web-common`'s `AdminRoleInterceptor`, bound to configurable path patterns
  (`platform.web.admin-guard.path-patterns`). Patterns rather than one constant because the prefix
  is not uniform: booking-service mounts its staff endpoints under `/api/v1/bookings/admin/**`.

Neither suffices alone. Every service also listens on its own port inside the Docker network, where
nothing strips a caller-supplied `X-User-Role`; and the service-level check trusts a header only the
gateway makes trustworthy. Verified both ways — see below.

**Duplicate admin surfaces removed from the edge.** Every admin operation was reachable twice:
through admin-service's console API at `/api/v1/admin/**`, and directly on the owning service at
`/api/v1/bookings/admin/**`, `/api/v1/auth/admin/**`, `/api/v1/payments/admin/**`,
`/api/v1/users/admin/{profiles,stats}`. admin-service's versions are pure Feign passthrough, and
the console has always used them; the second set was a second public door onto the same operations.
`InternalOnlyPathFilter` (a gateway `GlobalFilter` at `HIGHEST_PRECEDENCE`) now 404s them from
outside — 404 and not 403, because whether an internal endpoint exists is not an outside caller's
business. They are not deleted: admin-service reaches them over Feign and that is why they exist.

`/api/v1/users/admin/kyc/**` is deliberately **not** blocked. Only `profiles` and `stats` are
duplicated; KYC review has no console proxy, so blocking the prefix wholesale would have left no way
to approve a KYC document at all.

**`IdentityFeignInterceptor`** is what makes the gating possible. admin-service's Feign clients
previously arrived anonymous, so gating booking-service or payment-service would have broken the
console. It copies `X-User-*` onto outbound calls, skipping any header a client already sets
explicitly (admin-service's `AuthServiceClient` names `X-User-Role` itself). Modelled on
`tenant-common`'s `TenantFeignInterceptor` and complementary to it — that one carries which
workspace, this one carries who. Deliberately adds nothing when there is no inbound request:
a scheduled sweep has no caller, and inventing one would let a job pass a check no human authorised.

**New module `web-common`.** Replaces:

- **13 copies of `GlobalExceptionHandler`** — ten were byte-identical bar the package and which
  local `AccessDeniedException` they imported. Those ten now use `PlatformExceptionHandler`.
  auth-, booking- and admin-service keep their own: their body shape (`ErrorResponse`,
  `ApiResponse`) is one their callers already depend on. `platform.web.error-handler: false` opts
  them out.
- **11 copies of `AccessDeniedException`** (ten classes plus one nested inside user-service's
  `AdminKycController`).
- **16 copies of the `ADMIN_ROLES` set**, hand-written in every controller that gated on it — a role
  added to auth-service's seed data would have had to be found in each. Now `StaffRoles`. The
  gateway keeps its own copy: it is WebFlux and cannot depend on a `spring-boot-starter-web` module.

**Three latent defects found and fixed while verifying:**

1. **Every unmatched path was a 500.** `NoResourceFoundException` fell through to the
   `@ExceptionHandler(Exception.class)` catch-all in all 13 handlers, so a mistyped URL read as a
   server fault — and `/actuator/prometheus`, which these services do not expose, answered 500 to
   every scrape rather than 404.
2. **A bad path variable was a 500.** `/api/v1/bookings/nope` against a `Long` id raised
   `MethodArgumentTypeMismatchException` into the same catch-all. Now 400.
3. **`@ConditionalOnClass` on a `@Bean` method does not prevent class loading.** The first cut of
   `WebCommonAutoConfiguration` guarded the Feign interceptor that way and tenant-service — which
   has no Feign — died on `ClassNotFoundException: feign.RequestInterceptor` at startup. A
   method-level condition is evaluated only after Spring reflects over the configuration class, and
   that resolves every `@Bean` return type first. Both optional pieces are now nested
   `@Configuration` classes carrying a *class-level* `@ConditionalOnClass`, checked from ASM
   metadata before the class is loaded. Worth remembering for any future starter in this codebase.

**Two stale tests fixed** (both failing on `main` before this work, unrelated to it):
`AdminProfileControllerTest` was missing a `@MockBean` for `KycDocumentRepository`, which the
controller has taken since KYC shipped; `AdminBookingControllerTest` was missing one for
`CatalogueService` and still stubbed `findAll()` after the controller moved to
`findAllByOrderByNameAsc()`.

**Verified live 2026-08-21** through the gateway with real JWTs, 42/42 checks: all nine console
endpoints 403 for a CUSTOMER and 200 for an ADMIN; the seven duplicated edge paths 404; the other
services' admin surfaces (support, announcements, projects, escrow, audit) still 403/200 correctly;
member and public surfaces (`ui-config/me`, `catalogue`, `notifications`, `support/tickets`,
`projects`, `wallets/me`) unchanged; KYC review still reachable at 200 for an ADMIN and 403 for a
CUSTOMER; unmatched routes 404 and bad path variables 400. Both layers checked independently — on
booking-service's and admin-service's own ports with the gateway bypassed, a forged
`X-User-Role: CUSTOMER` and a missing header are both 403 while `ADMIN` is 200. Backend build green
across all 21 modules with all 76 tests passing.

**Not done (deliberate):**

1. **The BFF's extra hop stays.** Collapsing admin-service's passthrough into the owning services
   would remove a network hop but also the one place a console-wide role check and audit trail can
   live. The duplication removed here is the second *public door*, not the proxy.
2. **Tenant branding vs admin-service's `ui_theme_config`** — the remaining real duplication, and a
   data-model change rather than an HTTP-layer one. Untouched.
3. **The five-copy Feign clients** (`UserServiceClient`, `BookingServiceClient`, `AuthServiceClient`
   and their fallback factories) are still per-service. They differ in which method subset each
   service needs, so consolidating them means designing one interface per callee rather than
   deleting duplicates — a larger change than this pass.

## Duplicate removal, round two — 2026-08-21

The first pass took the exception layer. This one takes the rest. Duplicate class *names* across
`src/main` went from 14 to 4, and the 4 that remain are deliberate.

**What was actually duplicated, and what only looked it.** The distinction mattered more than the
count. Four names were carried by classes doing genuinely different jobs, which made the codebase
look more duplicated than it was and hid the real duplicates among them.

Consolidated into `web-common`:

| Was | Now | Note |
|---|---|---|
| `UserNameResolver` ×2 (booking, payment) | `web.common.client.UserNameResolver` | Byte-identical bar comments. Its test went with it; the two service copies covered the same cases with different fixtures. |
| `UserServiceClient` ×2 (booking, payment) | `UserNameClient` | Identical `getUserName` interface. |
| `UserServiceClientFallbackFactory` ×2 | `UserNameClientFallbackFactory` | Identical bar a log message. |
| `BookingServiceClient` ×2 (review, messaging) | `BookingLookupClient` | Identical `getBooking` interface. |
| `BookingServiceClientFallbackFactory` ×2 | `BookingLookupClientFallbackFactory` | Both `bookingId -> null`. |
| `BookingDto` ×3 (messaging, review, project) | `web.common.client.BookingDto` | Three partial views of one upstream contract. Union of fields; `@JsonIgnoreProperties` makes the narrower callers' nulls harmless. |
| `AuthServiceClient` ×2 (search, notification) | `UserDirectoryClient` | Same endpoint, different signatures — notification's was a superset. That one shipped; search passes null for the two filters. |
| `StatusChangeRequest` ×2 (project, support) | `web.common.dto.StatusChangeRequest` | Differed only in `@NotNull` vs `@NotBlank`. `@NotBlank` is strictly stronger on a String, so that is the one kept. |

Renamed, because they were never duplicates — same name, different code:

`search`'s `UserServiceClient` → `UserProfileClient`; `support`'s → `MaterialRatesClient`;
`search`'s `BookingServiceClient` → `ServiceCatalogueClient`; `project`'s → `ProjectBookingsClient`;
`project`'s `PaymentServiceClient` → `ProjectEscrowClient`; `tenant`'s `KafkaProducerConfig` →
`TenantTopicConfig` (it declares a topic; auth's configures a producer factory). Fallback factories
renamed to match.

**Kept duplicated, deliberately:** the three `GlobalExceptionHandler`s (auth, booking, admin serve
a body shape their callers depend on) and the three `Admin*Controller` pairs (the BFF proxy and the
owning service's endpoint — that pairing *is* the architecture the previous section established).
admin-service keeps its own `<Callee>ServiceClient` family: as the BFF, "the client for service X"
is exactly what those are, and nothing else collides with the names now.

**Wiring note.** Feign clients in a shared package are not found by a service's default scan, and
`@Component` on a fallback factory there is silently ignored. Each consuming service names
`com.civileng.marketplace.web.common.client` in `@EnableFeignClients(basePackages = ...)`, and the
factories are declared as beans by `SharedClientConfiguration` rather than annotated. Missing the
first is a startup failure naming the missing client bean; missing the second is a runtime failure
when the fallback is needed, which is worse — hence beans, not stereotypes.

**A real regression this work exposed, and the fix.** The staff-role gate from the previous section
broke search-service's reindex, and the earlier verification missed it because it only exercised
request paths, never the scheduler. `IdentityFeignInterceptor` deliberately propagates nothing when
there is no inbound request — a background job must not borrow a passer-by's identity — so the
reindex sweep and the announcement job arrived at auth-, user- and booking-service with no role and
were refused. The profile index would have quietly emptied.

Fixed with `platform.web.admin-guard.exclude-path-patterns`, naming the specific read endpoints
those jobs call: auth's `/users` and `/users/*/name`, user's `/profiles`, `/profiles/*`, `/stats`,
booking's `/categories`. Safe because `InternalOnlyPathFilter` already makes those prefixes
unreachable from the gateway — they are service-to-service reads with no external door, and the
externally reachable equivalents on admin-service stay gated. The list is specific read paths on
purpose: a wildcard would take the mutating endpoints with it.

**Verified live 2026-08-21**, 44/45 checks (the one miss was a wrong test URL, re-checked green):
the role gate and the sealed internal paths behave exactly as the previous section recorded; every
consolidated class exercised through a real request — `UserNameResolver` via `/admin/bookings` and
`/admin/invoices`, `BookingDto` via review-, messaging- and project-service, `UserDirectoryClient`
via announcements and via a forced reindex, `StatusChangeRequest` via support, `ProjectEscrowClient`
via wallets, `ServiceCatalogueClient` via search. `POST /api/v1/admin/search/reindex` completed
"9 profiles, 21 services in 965 ms" with **zero** refusals logged at auth-, user- or
booking-service, where before the fix auth-service alone logged 41. Backend build green across all
21 modules, 71 tests passing.

## Duplicate removal, frontend — 2026-08-21

Same pass, UI side. The frontend had few duplicate *file names* (two `index.ts`), so the
duplication was all in repeated logic — and in two places the copies had drifted into a visible
inconsistency.

**`utils/currency.ts` — replaces six money formatters.** `RevenuePage`, `AdminDashboard`,
`BookingManagement`, `InvoicesPage`, `AnalyticsPage` and `bookingPricing.formatRupees` each had
their own. Two divergences mattered:

1. **Compact form stopped at lakh in three of them.** `RevenuePage` reached crore; `AdminDashboard`
   and `AnalyticsPage` did not. The same ₹1,20,00,000 read as "₹120.0L" on the dashboard and
   "₹1.20Cr" on the revenue screen.
2. **Three used the browser's default locale.** `BookingManagement`'s `₹${amount.toLocaleString()}`
   grouped as 100,000 rather than the 1,00,000 an Indian reader expects — and the lakh/crore
   grouping is the point of this app's numbers, not something to leave to the reader's browser.

There was also a raw `₹{booking.totalAmount}` on the member dashboard with no formatting at all, so
a booking read as `₹123900` there and `₹1,23,900` on the admin screen. `formatCurrency` (exact,
`en-IN`) and `formatCompactCurrency` (Cr/L/K) now serve both, and `formatRupees` stays as a named
re-export because the booking flow imports it in a dozen places and the name reads better next to
the pricing maths.

**`utils/statusColors.ts` — replaces four colour maps.** `DashboardPage`'s booking-status map knew
five of the thirteen statuses a booking can hold, against `BookingManagement`'s full set. A booking
in ASSIGNED, DISPUTED or AWAITING_PAYMENT rendered neutral grey on the member dashboard and a real
colour on the admin screen — the same booking, two readings, with grey implying "nothing to see".
The full map serves both now. `UserManagement`'s map is a different vocabulary (account statuses)
and is kept as its own export; support-ticket colours already lived in one place
(`supportApi.statusColor`) and were left there.

**Eight screens were bypassing the date preference.** `utils/datetime.ts` and `useDateTime()`
already existed so that the timezone and date format chosen in settings take effect everywhere —
but `EmailLogPage`, `AlertsPage`, `MaterialPricesPage`, `ServiceCatalogueManagement` and
`TenantManagement` called `toLocaleDateString`/`toLocaleString` directly and silently ignored it.
All now go through the hook. Two of the helpers were module-level functions that could not call a
hook; `InAppPreview` was converted to a block body to do so.

One call is deliberately left on browser-local time and now says so: `formatClock` in
`EmailLogPage`, the clock on a chat bubble inside the SMS/WhatsApp phone mockups. Those frames show
how the message looked on the recipient's handset, and a handset shows local time. The in-app
preview next to them is read inside this app, so that one does use the workspace timezone — the
distinction is the reason the comment is there.

**Verified live 2026-08-21** in Chrome against the deployed stack. Admin bookings render
`₹1,00,000` with lakh grouping where the old browser-locale formatter gave `₹100,000`. The member
dashboard shows a booking in ASSIGNED as violet — grey before this change — and its Amount column
now groups. Dates render `17/08/2026` from the configured `DD/MM/YYYY` on both member and admin
screens. `tsc --noEmit` clean, production build clean, and the deployed container serves the shared
chunks (`currency-BEHlZtdj.js` byte-identical to the local build, `statusColors-*.js` present, and
the `DashboardPage` chunk no longer contains a raw `₹` literal).
