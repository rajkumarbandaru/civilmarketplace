# Construction Ecosystem Platform — Multi-Tenant Architecture & Design Specification

**Status:** Draft v1.0 · **Date:** 2026-09-24 · **Scope:** Architecture and design only. No production code, SQL,
React components or Spring classes are defined here. Those come after this specification is approved.

---

## 0. The one idea everything else follows

```
                         YOUR PLATFORM (one codebase, one core)
                                      │
                                 SUPER ADMIN
                                      │
                               PLATFORM FACTORY
                                      │
          ┌───────────────────────────┼───────────────────────────┐
          ▼                           ▼                           ▼
      COMPANY A                   COMPANY B                   COMPANY C
      Tenant A                    Tenant B                    Tenant C
          │                           │                           │
   Blue + Glassmorphism      Green + Material            Black + Luxury
   Marketplace layout        Corporate layout            E-Commerce layout
   B2B + B2C                 B2B only                    B2B + B2C
          │                           │                           │
          └───────────────────────────┼───────────────────────────┘
                                      ▼
                         TENANT EXPERIENCE LAYER  (configuration, data only)
                                      ▼
                            SHARED PLATFORM CORE  (code, identical for all)
```

> **A tenant's style is not a separate application.** It is a *Tenant Experience Layer*, which is
> versioned configuration data. The platform resolves it at runtime and applies it to the same
> shared core. Company A and Company B differ in identity, capabilities and rules. They never
> differ in which code they run.

Here is what this means in practice for every team:

| Team | What the principle means for you |
|---|---|
| Frontend / Mobile | There is one bundle and one app shell. Themes, styles and layouts are *selected by key* from a registry that ships in the code. Tenant names, colours and logos never appear in source code. |
| Backend | Every business rule that can differ per tenant is read from resolved configuration. `if (tenant == "acme")` is a defect. |
| Database | Tenant identity is structural: it comes from the schema and from `tenant_id`. Code never infers it or relies on convention. |
| DevOps | Onboarding a tenant is a data operation. It needs no deployment, no restart and no per-tenant branch. |
| QA | Testing "tenant X" means testing *a configuration*. The test matrix is configurations × one build. |
| UI/UX | You design *token sets, style packs and layout templates*. You do not design tenant-specific screens. |

---

## 1. Document map

Read the files in order. Each one stands alone for its audience.

| # | File | Contents | Primary audience |
|---|---|---|---|
| 1 | [01-overview-and-business.md](01-overview-and-business.md) | Architecture overview, business architecture, the Super Admin architecture, Super Admin vs Tenant Admin, the admin experience | Everyone |
| 2 | [02-tenant-and-data-architecture.md](02-tenant-and-data-architecture.md) | Tenant isolation, data classification (global/tenant/shared/override), `tenant_id` rules, the user model and RBAC, the database strategy | Backend, DB, Security |
| 3 | [03-platform-factory.md](03-platform-factory.md) | The Platform Factory, the 15-step wizard, the tenant lifecycle, the factory data model | Backend, Frontend, QA |
| 4 | [04-configuration-architecture.md](04-configuration-architecture.md) | Configuration levels, inheritance, precedence, versioning, validation, the dependency engine, defaults, publishing and rollback, the business-rules engine | Backend, Frontend, QA |
| 5 | [05-experience-engines.md](05-experience-engines.md) | The Branding, Theme, UI Style and Layout engines, and the frontend/mobile configuration architecture | Frontend, Mobile, UI/UX |
| 6 | [06-security-architecture.md](06-security-architecture.md) | Super Admin security, tenant security, request, domain, authentication and authorization flows, secrets, audit | Security, Backend, DevOps |
| 7 | [07-commerce-b2b-b2c.md](07-commerce-b2b-b2c.md) | B2B, B2C and hybrid architecture, the construction domain, industry-module extensibility | Product, Backend |
| 8 | [08-subscription-and-entitlements.md](08-subscription-and-entitlements.md) | Plans, subscriptions, the feature catalog, entitlements, flags, quotas, runtime evaluation | Backend, Frontend, Product |
| 9 | [09-infrastructure-and-nfr.md](09-infrastructure-and-nfr.md) | Cache, storage, notifications, backend and scalability architecture, NFRs, observability, DR, zero-downtime deployment | DevOps, Backend, SRE |
| 10 | [10-risks-decisions-roadmap.md](10-risks-decisions-roadmap.md) | Risk registers, the ADR index, open decisions, migration from the current codebase, acceptance criteria | Architects, leads, AI agents |

### Diagram index (all 26 diagrams you asked for)

| # | Diagram | Location |
|---|---|---|
| 1 | Overall Platform Architecture | 01 §2 |
| 2 | Super Admin Architecture | 01 §4 |
| 3 | Platform Factory | 03 §1 |
| 4 | Tenant Creation | 03 §3 |
| 5 | Tenant Lifecycle | 03 §4 |
| 6 | Tenant Isolation | 02 §2 |
| 7 | Configuration Hierarchy | 04 §2 |
| 8 | Configuration Inheritance | 04 §3 |
| 9 | Theme Resolution | 05 §3 |
| 10 | Layout Resolution | 05 §5 |
| 11 | Feature Resolution | 08 §5 |
| 12 | B2B Flow | 07 §2 |
| 13 | B2C Flow | 07 §3 |
| 14 | B2B + B2C Flow | 07 §4 |
| 15 | Tenant Request Resolution | 06 §4 |
| 16 | Domain Resolution | 06 §5 |
| 17 | Authentication Flow | 06 §6 |
| 18 | Authorization Flow | 06 §7 |
| 19 | Super Admin vs Tenant Admin | 01 §5 |
| 20 | Database Architecture | 02 §7 |
| 21 | Cache Architecture | 09 §1 |
| 22 | Media Storage Architecture | 09 §2 |
| 23 | Notification Architecture | 09 §3 |
| 24 | Subscription & Feature Entitlement | 08 §2 |
| 25 | Configuration Publishing | 04 §8 |
| 26 | Configuration Rollback | 04 §9 |

---

## 2. Configuration levels (used consistently in every document)

The spec separates **seven** levels. The six you named appear here along with ROLE, which the codebase
already has (the admin-service "workspace").

| Level | Owner | Where it lives | Can it be overridden? | Example |
|---|---|---|---|---|
| **PLATFORM** | Engineering (code and infrastructure) | Code, deployment config, Config Server | **No.** These are invariants and hard ceilings. | Maximum token lifetime, allowed hash algorithms, the list of theme/style/layout keys the code can render, the upload size ceiling |
| **GLOBAL** | Super Admin, at runtime | Configuration Service, `GLOBAL` scope | Yes, where the key's override policy allows it | Default primary colour `#000000`, default notification templates, default cancellation window |
| **DOMAIN** | Super Admin, per industry module | Configuration Service, `DOMAIN:{module}` scope | Yes | Construction defaults: service categories, unit-of-measure master, default quotation validity |
| **TENANT** | Tenant Admin (within plan) or Super Admin | Configuration Service, `TENANT:{tenantId}` scope | Yes | Tenant A primary colour `#0057FF`, commission 8% |
| **APPLICATION** | Tenant Admin | `TENANT:{tenantId}/APP:{surface}` | Yes | The admin portal uses a compact sidebar while the customer app uses bottom navigation |
| **ROLE** (workspace) | Tenant Admin | `TENANT:{tenantId}/ROLE:{role}` | Yes | The contractor workspace shows a "Projects" menu |
| **USER** | The end user | `TENANT:{tenantId}/USER:{userId}` | Only on keys marked user-overridable | Dark mode, density, language |

**Effective value = the most specific level that sets the key and is allowed to set it.** Values
are always bounded by PLATFORM constraints and by the tenant's entitlements. The full rules are in
[04 §4](04-configuration-architecture.md).

---

## 3. Absolute rules → where each one is enforced

| Rule | Enforced by | Document |
|---|---|---|
| R1–R4: no hardcoded tenant configuration, colours, branding or layouts | Registry-by-key rendering, a lint rule banning tenant identifiers in source, CI grep gate | 05 §8, 10 §5 |
| R5: no frontend-only isolation | Gateway, service and repository layers all enforce tenant context. The UI hides things as a courtesy, never for security. | 06 §3 |
| R6: A never accesses B | Schema-per-tenant, `tenant_id` column assertion, JWT tenant claim cross-check, storage prefix policy, cache key namespace | 02 §2, 06 §3 |
| R7–R8: Super Admin owns the platform; Tenant Admin is bounded | Separate identity realm, separate portal host, separate role namespace | 06 §2, 01 §5 |
| R9: overrides where permitted | Per-key override policy (`overridableAt`, `lockedAt`) | 04 §4 |
| R10–R11: versioned, rollback-capable config | Immutable configuration versions with a publish pointer | 04 §7–9 |
| R12: no plaintext secrets | Envelope encryption with KMS, write-only secret fields | 06 §9 |
| R13: auditable tenant config | Every publish emits a hash-chained audit event | 06 §10 |
| R14: B2B and B2C coexist | One party model in which organisation and individual are both parties | 07 |
| R15: construction is the first domain, not a limit | Domain modules plug into a domain-agnostic core | 07 §6 |

---

## 4. Baseline: what already exists in this repository

This spec *evolves* the running system. It does not replace it. Implementers should know what is
already there. Full detail is in `MODULE_STATUS.md`.

| Capability | Current state | Where this spec takes it |
|---|---|---|
| Tenancy model | Schema-per-tenant across 11 services (`tenant-common`). The tenant is resolved from the subdomain at the gateway, cross-checked with the JWT `tenant` claim, and passed on as `X-Tenant-Id`. Client-supplied `X-Tenant-Id` is stripped. | Kept as the **standard tier**. Adds `tenant_id` columns as defence in depth, plus dedicated-database and dedicated-cell tiers (02 §6) |
| Tenant registry | `tenant-service`: key, subdomain, custom domain, a flat branding columnset, `plan` string, `Vertical`, CSV `enabled_modules`, `TenantStatus {PENDING, ACTIVE, SUSPENDED, ARCHIVED}` | Becomes the **Platform Factory** backend with the full lifecycle, domains, subscriptions and entitlements (03, 08) |
| UI configuration | admin-service `uiconfig`: a four-layer overlay (catalogue → workspace → user override → user appearance), a platform theme, `TenantBranding` with closed sets for style, layout and density | Generalised into the **Configuration Service** with versioned scopes and the Theme, Style and Layout engines (04, 05) |
| Module gating | Gateway 404s routes for disabled modules | Becomes the enforcement point for **feature entitlements** (08) |
| Audit | Kafka-fed, append-only, hash-chained `audit-service`, with five producers | Adds a separate **platform audit stream** for Super Admin actions (06 §10) |
| Passwords | BCrypt cost 12 | Kept. Argon2id for new hashes, rehash on login (06 §6) |
| MFA | **Not implemented** | Mandatory for Super Admin, and policy-driven for tenants (06 §2) |
| Tenant-aware frontend | **Not built.** The shell does not yet read `/api/v1/tenant-resolution`. | The bootstrap protocol and experience engines (05) |
| Cross-tenant reporting | **Not built.** Schemas cannot `JOIN`. | CDC into a tenant-stamped analytics store (02 §8) |

---

## 5. Glossary

| Term | Meaning |
|---|---|
| **Platform** | The whole product: code, infrastructure and the global configuration owned by the platform owner. |
| **Platform Factory** | The Super Admin subsystem that creates, configures, validates, publishes and runs tenant platforms. |
| **Tenant** | One independent customer company operating its own branded platform. It is the isolation boundary. |
| **Operator tenant** | The reserved `platform` tenant that hosts the Super Admin portal. It never holds business data. |
| **Tenant Experience Layer** | The resolved branding, theme, style, layout, navigation, content and feature set for one tenant, application and user. It is pure data. |
| **Organization** | A company *inside* a tenant (a supplier, a contractor firm, a buyer). It is **not** a tenant. |
| **Party** | Anyone who transacts: an individual person or an organization. |
| **Workspace** | The configuration view of one role inside a tenant (existing admin-service term). |
| **Surface / Application** | One front end of a tenant: `website`, `customer-app`, `admin-portal` or `mobile`. |
| **Module** | A deployable capability such as `bookings` or `procurement`. It belongs to the core (horizontal) or to a domain (vertical). |
| **Feature** | A commercially meaningful capability in the Feature Catalog. It maps to one or more modules and settings. |
| **Entitlement** | What a tenant is *allowed* to use, derived from its plan, add-ons and grants. |
| **Feature flag** | An *operational* switch for rollout and kill switches, independent of commercial entitlement. |
| **Config document** | One versioned, schema-validated unit of configuration, for example `theme` or `rules.cancellation`. |
| **Publish pointer** | The reference that marks which config version is live for a scope. Rollback moves the pointer. |
| **Cell** | An independent deployment stamp (services, databases, cache) that hosts a set of tenants. |
