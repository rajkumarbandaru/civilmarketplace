# 10 — Risks, Decisions, Migration Roadmap & Acceptance Criteria

## 1. Risk registers

Scoring: Likelihood (L) and Impact (I) are rated H/M/L.

### 1.1 Tenant isolation risks

| # | Risk | L | I | Mitigation |
|---|---|---|---|---|
| TI-1 | Pooled connection returned still pointing at tenant A's schema | L | H | Reset on release (exists), pool validation query, `tenant_id` write assertion |
| TI-2 | A service reachable without the gateway trusts spoofed `X-User-*` / `X-Tenant-Id` headers | M | H | Network policy/mesh + signed internal context token (06 §3) |
| TI-3 | Async path (Kafka, scheduler, webhook) processes a record under the wrong tenant | M | H | Mandatory tenant header (exists), `CrossTenantRunner` (exists), webhook token mapping (09 §3) |
| TI-4 | Cache key without tenant prefix serves A's data to B | M | H | Context-building cache wrapper, lint rule, tests |
| TI-5 | Storage path from client input | L | H | Server-built keys, presign scoped to the exact key |
| TI-6 | Search query missing the tenant filter | M | H | Filtered aliases / index per tenant. Services never hit raw indices. |
| TI-7 | Super Admin tooling becomes a cross-tenant backdoor | M | H | Data plane only via the audited impersonation broker, warehouse for analytics |
| TI-8 | Warehouse or BI exposes other tenants to a Tenant Admin | M | H | Row-level security in the semantic layer, per-tenant tests |

### 1.2 Security risks

| # | Risk | L | I | Mitigation |
|---|---|---|---|---|
| SE-1 | Super Admin credential phishing | M | Critical | WebAuthn only, dedicated host, JIT, anomaly detection |
| SE-2 | No MFA today (baseline gap) | H | H | Phase 1 deliverable (§4) |
| SE-3 | Secrets in config or logs | M | H | Secrets broker, write-only fields, log redaction, secret scanning in CI |
| SE-4 | Tenant-controlled content (SVG, templates, CMS) → XSS | M | H | Sanitise/rasterise, logic-less templates, no custom CSS/JS, CSP, separate media domain |
| SE-5 | Dangling custom domain takeover | M | M | Unbind on archive, daily re-verification |
| SE-6 | Impersonation misuse | L | H | Reason, ticket, time box, read-only default, owner notification, audit |

### 1.3 Scalability risks

| # | Risk | L | I | Mitigation |
|---|---|---|---|---|
| SC-1 | Flyway-at-boot and schema count grow linearly with tenants | H | M | Migration controller, multiple clusters (02 §6.3) |
| SC-2 | Noisy-neighbour tenant | M | M | Rate limits, quotas, bulkheads, tier promotion |
| SC-3 | Scheduled jobs O(tenants) | M | M | Sharded, fair runner |
| SC-4 | Metric cardinality explosion from tenant labels | M | M | Bounded tenant-labelled metric set |
| SC-5 | Config resolution on the hot path | L | M | Pre-resolved, versioned bundles |

### 1.4 Configuration management risks

| # | Risk | L | I | Mitigation |
|---|---|---|---|---|
| CM-1 | Global change breaks tenants that inherit it | M | H | Impact analysis + per-tenant validation + global rings (04 §10) |
| CM-2 | Rule change retroactively alters existing contracts | M | H | Rule version snapshot on every transaction (04 §11.2) |
| CM-3 | Rollback to content invalid under the current plan or schema | M | M | Rollback re-validates. Upcasters on read. |
| CM-4 | "Config sprawl": thousands of unused overrides | M | L | Advisory lint, "equal to inherited" cleanup suggestions |
| CM-5 | Config references a registry key an older client can't render | M | M | Registry manifest per build, fallbacks |
| CM-6 | Everything-in-one-JSON blob | M | M | Document-per-concern design (04 §5) |
| CM-7 | Style × theme × layout combinations produce broken UIs | H | M | Compatibility validators, visual reference matrix |

---

## 2. Architecture Decision Records (index)

| ADR | Decision | Status |
|---|---|---|
| ADR-001 | Tenant experience is configuration applied to one shared core. No per-tenant apps, forks or branches. | Accepted (principle) |
| ADR-002 | Keep schema-per-tenant as the Standard tier. Add a `tenant_id` column to all tenant tables as defence in depth. | Proposed |
| ADR-003 | Tiered hybrid placement (T1 standard / T2 dedicated DB / T3 cell). T0 pooled deferred. | Proposed |
| ADR-004 | Opaque immutable `tenant_id` (ULID) alongside the immutable `tenant_key` | Proposed |
| ADR-005 | Super Admin in a separate identity realm and host. Tenant `SUPER_ADMIN` role renamed `TENANT_OWNER`. | Proposed |
| ADR-006 | Owner onboarding by invitation. The Super Admin never sets a tenant user's password. | Proposed |
| ADR-007 | Configuration Service with versioned documents per scope, per-key override policy, publish pointers, roll-forward rollback | Proposed |
| ADR-008 | Theme, UI style, layout and branding as four orthogonal axes. Styles and layouts are code in a registry. Selection is config. | Proposed |
| ADR-009 | Business rules as typed documents and decision tables, not a general rules engine. Transactions snapshot rule versions. | Proposed |
| ADR-010 | Entitlements separate from feature flags and tenant feature state | Proposed |
| ADR-011 | Signed internal context token from gateway to services | Proposed |
| ADR-012 | CDC to a warehouse for cross-tenant analytics. No cross-schema OLTP queries. | Proposed |
| ADR-013 | Secrets in Vault/KMS with per-tenant DEK. Crypto-shredding on delete. | Proposed |
| ADR-014 | Domain modules via a manifest/SPI. `Vertical` becomes data. | Proposed |
| ADR-015 | Party model: roles and capabilities on persons and organizations, not user types | Proposed |

---

## 3. Open decisions (need product/business input)

| # | Question | Options | Recommendation |
|---|---|---|---|
| D1 | Mobile distribution | (a) white-label binary per tenant via App Factory, (b) one container app, (c) both by plan | (c): container by default, white-label for Enterprise |
| D2 | API host | One shared `api.platform.com` vs per-tenant API hosts | Shared for mobile, host-derived for web |
| D3 | Tenant Admin editing rights for theme/layout | Full vs presets-only by plan | By plan (08 §3.1) |
| D4 | Cross-tenant B2B (a supplier in tenant A selling to a buyer in tenant B) | Not supported / platform-brokered exchange later | Not supported now. If needed, design a separate "Network" product with explicit consent and data-sharing contracts. |
| D5 | Single identity across tenants ("platform passport") | Independent accounts (current) / federated identity | Keep independent. Revisit when a real use case appears. |
| D6 | Approval (maker–checker) for tenant publish | Always / by tier / never | By tier: Enterprise and custom domains always, Starter auto |
| D7 | Platform take-rate on tenant commission | None / fixed / per plan | Per plan, GLOBAL-locked key |
| D8 | Data residency offering | Single region / regional cells | Single region now, regional cells as an Enterprise add-on |

---

## 4. Migration roadmap from the current codebase

| Phase | Goal | Key deliverables | Exit criteria |
|---|---|---|---|
| **0: Hardening** | Close baseline gaps | MFA (TOTP/WebAuthn) in auth-service, signed internal context, network policies, tenant-aware frontend bootstrap (`/tenant-resolution` → bootstrap), cross-tenant test suite, **tenant-scoped integration credentials**: replace the single global `razorpay.*`, `spring.mail.*`, `twilio.*` and `GEMINI_API_KEY` in `config-repo` with a per-tenant `IntegrationResolver` plus a separate platform credential set (06 §9.1) | Isolation test suite green in CI. Super Admin requires MFA. A payment, email or SMS in tenant A provably uses tenant A's credentials, and no tenant path can reach a platform credential. |
| **1: Config foundation** | Configuration Service | Evolve admin-service `uiconfig` into scoped, versioned documents with the override policy. Migrate `Tenant` branding columns into the `branding`/`theme`/`style`/`layout` documents. Publish and rollback. | Theme change → publish → rollback demo on two tenants, with no code change |
| **2: Platform Factory** | Wizard + lifecycle | Draft store, 15-step wizard, validation engine, provisioning saga, the extended lifecycle, the Domain Manager (subdomains first) | Create → publish a tenant end-to-end in < 10 min with no manual steps |
| **3: Entitlements** | Plans and features | Feature catalog, plans, subscriptions, evaluator, gateway/service guards, quotas | Downgrade/upgrade scenarios pass. Enabled modules always ⊆ entitlement. |
| **4: Experience engines** | Style packs, layouts, blocks, CMS | Token pipeline (OKLCH ramps), style-pack registry, layout templates per surface, block library, preview tokens | Reference presets A/B/C render from one build. Visual matrix green. |
| **5: B2B** | Organizations and procurement | Party model migration, organizations, capabilities, relationships, RFQ → PO → GRN → invoice | Hybrid tenant flow (Diagram 14) runs end to end |
| **6: Scale & ops** | Tiers, cells, warehouse | Placement map, migration controller, CDC warehouse, per-tenant restore runbook, custom domains with ACME, secrets broker | Tenant moved between clusters in a drill. Single-tenant PITR drill passes. |

**Phase 0 status (2026-09-24).** Done and verified on the running stack
(`scripts/security/phase0_live_check.py`, `mfa_live_check.py`), with unit and Playwright tests:
tenant-scoped integration credentials with the Super Admin console; the gateway strips
client-supplied `X-User-*`/`X-Internal-*`; identity forwarded to services is HMAC-signed and
every service (tenant-service included) refuses unsigned or forged identity; only the gateway,
frontend, nginx and the MinIO API are published (`docker-compose.debug.yml` re-publishes the
rest on 127.0.0.1); tenant-scoped OTP and refresh-token keys, OTP no longer logged, five-guess
limit; refresh tokens and MFA tickets refused as access tokens; TOTP two-step sign-in required
for SUPER_ADMIN (all sign-in paths, recovery codes, replay and guess limits); the frontend
resolves its workspace before sign-in. Still open: Kubernetes network policies (no cluster yet —
the compose network plays that role locally), WebAuthn, and running the live isolation check in
CI, which needs the full stack in the pipeline.

**Phase 1 status (2026-09-24).** Exit criterion met on the running stack
(`scripts/config/phase1_theme_rollback_check.py`: change → publish → rollback on `acme` and
`bhoomi`, independent of each other, no code change). Built in admin-service: `branding`,
`theme`, `style` and `layout` documents with a code-owned schema and per-key override policy
(TENANT/ROLE), validated on every publish (types, enums, bounds, safe URLs, WCAG contrast
warning); immutable versions grouped into releases with publish pointers; history, key-level
diff and roll-forward rollback that re-validates; import of the previous theme rows as version 1;
onboarding and operator branding published as releases; published branding served publicly for
the sign-in screen; version history with rollback in Admin → Theme. Deferred: GLOBAL/DOMAIN
scopes, drafts with approval and preview tokens, scheduled publishing, Kafka/Redis cache fan-out
(clients read on every sign-in today), moving navigation into documents, and dropping the
retired `ui_theme_config` table.

**Phase 2 status (2026-09-24).** Exit criterion met on the running stack
(`scripts/factory/phase2_create_publish_check.py`): wizard draft → DRAFT tenant → publish → ACTIVE
in ~15 s with no manual step, then the owner sets their own password from the emailed link and
signs in (enrolling MFA) to their own workspace. Built: wizard drafts with autosave and optimistic
locking; DRAFT/PROVISIONING/PROVISIONING_FAILED states with a single lifecycle state machine and
status history; a persisted, resumable provisioning saga (every tenant-scoped service acknowledges
on `tenant.provisioned`; owner account; ACTIVE; invitation last) with retry, discard and resend;
single-use hashed owner invitations; live provisioning progress in the console. Fixed on the way:
review-service never provisioned tenants created after it booted (no Kafka); auth-service events
carried no tenant header (OTP and invitation emails were dropped); ZooKeeper on anonymous volumes
desynchronised Kafka topic IDs; the gateway cached "draft" for a minute after go-live. Deferred:
custom-domain verification (Phase 6), maker–checker approval, rules/integrations wizard steps
(integrations can be set on a DRAFT tenant from its detail page), preview tokens, compensation on
discard of services' already-created schemas (they are left empty).

**Phase 3 status (2026-09-24).** Exit criterion met on the running stack
(`scripts/entitlements/phase3_entitlements_check.py`): Professional → Starter stops Projects at
the gateway within 30 s with the tenant's choice and data kept; an add-on, a dated grant or an
upgrade brings it back; booking #501 on Starter is refused with 402 until capacity is added;
running modules ⊆ entitlement for every tenant. Built in tenant-service: code-owned feature
catalog (base modules, sellable modules, limits, add-ons), immutable versioned plans (Starter,
Professional, Enterprise v1; existing tenants grandfathered on Enterprise), subscriptions with
status (SUSPENDED suspends the tenant), expiring attributed grants (≤ 12 months, auto-lapse), the
entitlement evaluator, running = choices ∩ entitlement (published to the gateway and every
service), impact preview before plan changes, and an internal entitlements endpoint for services.
Quotas: web-common `Quotas` (30 s cache, fails open for commercial limits, 402), enforced for
bookings per month in booking-service. Console: plan card with limits, dormant modules, change
plan with preview, grants; plan-aware module picker and wizard plan choice. Deferred: usage
ledger and metering consumer, seat and storage enforcement, soft limits and overage billing,
usage dashboard, feature flags service, plan authoring UI (new versions ship as migrations).

**Phase 4 status (2026-09-24).** Exit criterion met: reference presets A (Aurora Marketplace =
blue + glass + marketplace), B (Evergreen Corporate = green + material + corporate) and C (Onyx
Boutique = black + luxury + e-commerce) render from one build, and the visual matrix
(`frontend/e2e/experience-matrix.spec.ts`: 3 presets × light/dark × desktop/mobile, screenshots
plus per-axis and contrast assertions) is green and stable across runs; applied live to three
tenants by `scripts/experience/phase4_experience_check.py`. Built: OKLCH token pipeline (ramps
50–950, per-mode semantic tokens, runtime contrast fallback, CSS custom properties); style-pack
registry (default, flat, elevated, material, glass, luxury, brutalist) with reduced-transparency
fallback; site layouts arranging a block library on the home page; a registry manifest the
backend is contract-tested against; the public theme bundle so anonymous visitors see the
tenant's look; signed 15-minute preview links bound to the tenant; Website layout and Preview in
app in the theme editor. Fixed: home-page literals (radii, an amber button, a white search box
unreadable in dark mode) that overrode the tenant's theme. Deferred: per-surface layouts for the
customer app and admin portal, CMS-editable block placement, component-token overrides
(advancedTheming), edge-injected critical CSS, a lint gate for literal colours.

**Phase 5 status (2026-09-24).** Exit criterion met: the hybrid tenant flow of Diagram 14 runs end to
end on the live stack (`scripts/procurement/phase5_hybrid_flow_check.py`, 45 checks). A homeowner
books a house extension that is assigned to a contractor; the contractor's firm — one organization
that is both CONTRACTOR and BUYER — raises an RFQ for that booking's materials, compares two
suppliers' quotations, accepts the cheaper, has the order approved by a colleague (never by whoever
raised it), and takes it through acknowledgement, partial and final goods receipts and supplier
invoices, the three-way match rejecting an over-billed and an over-priced invoice, until the order
closes. Built: `procurement-service` (schema per tenant, provisioned by the Factory before a tenant
goes live) with organizations and capabilities, members added by email and linked on first sign-in
(OWNER / APPROVER / MEMBER), PREFERRED_SUPPLIER and BLOCKED relationships, RFQ → quotation → PO
(snapshot of the accepted quotation, approval above the organization's threshold, default
₹1,00,000) → GRN (received and rejected per line, never accepting more than ordered) → invoice
(three-way match: quantity within accepted-and-not-yet-invoiced, unit price within 2%, same tax
rate); every step audited. Platform: a `procurement` module in the marketplace vertical, in
Professional and Enterprise (plan version 2; version 1 subscribers moved, as it only adds) and a
Starter add-on, gated at the gateway, and a Procurement workspace in the app (organizations, RFQs
with side-by-side comparison, orders with receipts and invoices). Deferred: the party-model
migration of existing users into organizations (membership is by email for now), dispatch /
e-way bill, e-invoicing (IRN), payment of approved invoices through payment-service (Net-N,
advance, escrow, TDS), buyer-specific price lists and CONTRACTED terms, supplier notifications of
RFQs and orders, B2B reviews, and tenders from a builder to contractors.

Each phase follows the project's standing rule: every integrated feature ships with unit/component
tests **and** Playwright end-to-end tests that exercise it in the browser.

---

## 5. Verification & acceptance criteria (for QA and AI agents)

### 5.1 Isolation (must never fail)

- For every tenant-scoped endpoint: tenant B's token on tenant A's host → 403. Tenant B's token
  with tenant A's resource id on B's host → 404. A missing tenant context → 400.
- Kafka: a record without a tenant header is dropped and alerted, and nothing is written.
- Cache: the key-scan test finds no tenant data outside a `{t:…}` prefix.
- Storage: a presigned URL for A's object is unusable for any other key. A listing is impossible.
- Search: a query from B never returns A's documents (property-based test over seeded data).
- Super Admin: a tenant realm token on any `/api/platform/**` route → 404/403.

### 5.2 Configuration

- Precedence table tests: for each key category, a matrix of {global, domain, tenant, app, role,
  user} presence and locks gives the expected value and provenance.
- A publish produces a new ETag. Clients reflect the change without a reload within 60 s.
- Rollback restores the previous content as a new version. Audit shows actor, reason and diff.
- A global change's impact analysis lists exactly the inheriting tenants.
- Invalid contrast, incompatible layout, and unmet dependencies are blocked with field-level messages.

### 5.3 Experience (visual)

- Reference tenants A (Blue + Glass + Marketplace, B2B+B2C), B (Green + Material + Corporate, B2B)
  and C (Black + Luxury + E-Commerce, B2B+B2C) render from **the same build** and the same routes.
  Playwright visual snapshots cover light and dark, and desktop and mobile widths.
- The CI gate finds no hex literal, tenant name or tenant logo path in the frontend source.

### 5.4 Lifecycle & factory

- Every state transition in 03 §4 is covered, including provisioning failure → retry → compensate.
- A suspended tenant: APIs return 423, the branded page is shown, provider webhooks are still accepted.
- A deleted tenant: key destroyed, schemas dropped, prefix purged, and the key cannot be reused.

### 5.5 Entitlement

- Disabling a feature hides its UI, and its routes return 404 at both the gateway and the service. Data is retained.
- Hard quota → 402/429 with a stable error code. Soft quota → warning event.
