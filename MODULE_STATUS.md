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
- **Port note:** `api-gateway`'s host port is `8087` (via `HOST_PORT_GATEWAY`), not the container's
  internal 8080 — 8080 is already taken on this host by an unrelated `projectile_ui` container.
  `review-service` is on 8089, `project-service` on 8096.

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
curl -s -X POST http://localhost:8087/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"customer@civileng.test","password":"Password123!"}'
```

The gateway's JWT filter turns that token into the `X-User-Id`/`X-User-Role`/`X-User-Email`/
`X-User-Name` headers every downstream service reads — always test through port 8087 rather than
hand-setting those headers against a service's own port, or you bypass the auth path entirely.

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
