# 07 — B2B, B2C & Hybrid Commerce · Construction Domain · Extensibility

## 1. Commerce model

B2B and B2C are **not two systems**. They are two sets of *capabilities* over one party model
(02 §4.1) and one commerce core:

| Concept | B2C usage | B2B usage |
|---|---|---|
| Party | Individual customer | Organization (buyer) |
| Counterparty | Worker / professional / organization | Supplier / contractor / manufacturer (organization) |
| Discovery | Search + map + ratings | Supplier directory + RFQ broadcast + catalogs |
| Price | List/quoted price, instant booking | Negotiated quotes, price lists per buyer, volume tiers, contracts |
| Commitment | Booking | Purchase Order (from an accepted quotation) |
| Fulfilment | Service visit, milestones | Delivery / GRN, partial deliveries, service completion certificates |
| Billing | Receipt / GST invoice at payment | Tax invoice against PO, credit terms, e-invoicing (IRN) and e-way bill (India, above thresholds) |
| Payment | Prepaid / escrow / milestone | Credit (Net-N), advance %, escrow for new relationships, TDS |
| Approval | Self | Buyer-side approval matrix (04 §11) |
| Trust | Reviews, KYC | Verification of GST/registration, relationship history, credit limits |

**Tenant mode** (04 configuration + 08 entitlements): `commerce.modes = [B2C] | [B2B] | [B2B, B2C]`.
The mode controls which capabilities, navigation and registration journeys exist. It does **not**
fork data models.

---

## 2. Diagram 12 — B2B Flow

```mermaid
sequenceDiagram
    autonumber
    participant BA as Company A (Buyer org)
    participant PL as Tenant Marketplace (procurement)
    participant SB as Company B (Supplier org)
    participant WF as Approval workflow
    participant PY as Payments
    participant NT as Notifications

    BA->>PL: Create RFQ (materials: 500 bags OPC 53, delivery site, date)
    PL->>PL: match suppliers (category, region, verified, capability=SUPPLIER)
    PL->>NT: notify invited/matched suppliers
    SB->>PL: Submit Quotation (price, taxes, delivery, validity, terms)
    PL-->>BA: Compare quotations (side-by-side)
    BA->>WF: Accept quotation → create PO draft
    WF->>WF: approval matrix (amount, role) — rules.approval
    WF-->>PL: PO approved
    PL->>SB: Purchase Order issued (PO number, snapshot of terms)
    SB->>PL: Acknowledge PO
    SB->>PL: Dispatch (e-way bill ref, vehicle, partial qty)
    BA->>PL: Goods Receipt (GRN: received/rejected qty)
    SB->>PL: Tax Invoice (against PO + GRN, IRN if applicable)
    PL->>PL: 3-way match PO ↔ GRN ↔ Invoice
    BA->>PY: Pay per terms (Net 30 / advance / escrow release)
    PY->>PY: commission (rules.commission snapshot), TDS
    PY-->>SB: Settlement
    BA->>PL: Rate supplier (B2B review)
```

### 2.1 B2B relationships

| Relationship | Meaning | Effects |
|---|---|---|
| `PREFERRED_SUPPLIER` | Buyer ↔ Supplier | Appears first in RFQ matching, can have a buyer-specific price list |
| `CONTRACTED` | Framework agreement | Contract rates, credit limit, terms |
| `SUBCONTRACTOR_OF` | Contractor → Subcontractor | Work orders flow down. Parent visibility into project progress. |
| `DEALER_OF` / `DISTRIBUTOR_OF` | Manufacturer → Dealer/Distributor | Channel pricing, territory |
| `BLOCKED` | Either side | Excluded from matching |

Relationships are **within one tenant**. Cross-tenant B2B trade is **not** supported by default,
see 10 §3 (decision D4).

---

## 3. Diagram 13 — B2C Flow

```mermaid
sequenceDiagram
    autonumber
    actor C as Customer
    participant S as Search
    participant P as Provider (worker / professional / contractor / company)
    participant B as Booking / Quotation
    participant Y as Payments (escrow)
    participant R as Reviews

    C->>S: Search "civil engineer near me" (filters: rating, price, verified)
    S-->>C: Results (tenant-scoped index)
    C->>P: View profile, portfolio, reviews
    alt instant booking (fixed-price service)
        C->>B: Book slot
    else quotation needed (site work)
        C->>B: Request quote (site details, photos)
        P->>B: Quote (scope, price, milestones)
        C->>B: Accept quote → Booking
    end
    B->>Y: Payment (advance / full / milestone into escrow)
    P->>B: Service delivered / milestone done
    C->>B: Confirm completion
    B->>Y: Release escrow (minus commission per snapshotted rules)
    C->>R: Review provider
    P->>R: Review customer (bidirectional, existing)
```

---

## 4. Diagram 14 — B2B + B2C Flow (hybrid tenant)

```mermaid
flowchart LR
    subgraph Demand
        C["Homeowner<br/>(B2C customer)"]
        BO["Builder org<br/>(B2B buyer)"]
    end
    subgraph Supply
        W["Mason / Labour<br/>(individual provider)"]
        CT["Contractor firm<br/>(org: CONTRACTOR + BUYER)"]
        SUP["Cement supplier<br/>(org: SUPPLIER)"]
        MR["Machinery owner<br/>(org/individual: EQUIPMENT_PROVIDER)"]
    end

    C -- "B2C: quote & book house extension" --> CT
    CT -- "B2B: RFQ cement & steel" --> SUP
    CT -- "B2B: rent excavator (rental booking)" --> MR
    CT -- "B2C-style: hire daily labour (booking)" --> W
    BO -- "B2B: tender for project package" --> CT
    SUP -- "B2C: retail sale of materials" --> C

    CT -. "one organization, two roles:<br/>SELLER to homeowner, BUYER from supplier" .- CT
```

**The key point:** the contractor firm is simultaneously a *seller* (B2C, to the homeowner) and a
*buyer* (B2B, from the supplier). One organization carries both capabilities. The commission rules,
tax treatment and payment terms differ **by transaction type**, and are resolved from `rules.*`
keyed by `(transactionType, category, partyTypes)`, not by user type.

---

## 5. Construction ecosystem (first domain module)

### 5.1 Party capabilities and profiles

| Group | Capabilities / profiles |
|---|---|
| Demand | Customer (individual), Builder/Developer (org), Contractor (as buyer) |
| Individual providers | Worker, Labour (skilled/unskilled trade), Civil Engineer, Architect, Surveyor, Interior designer, Site supervisor |
| Organizational providers | Contractor, Subcontractor, Construction Company, Service Provider |
| Material trade | Supplier, Manufacturer, Dealer, Distributor, Material Seller |
| Equipment | Vehicle Owner, Machinery Owner, Equipment Provider |

### 5.2 Domain capability modules

| Module | Key aggregates | Existing service | Notes |
|---|---|---|---|
| `services` (catalog) | ServiceCategory (GLOBAL taxonomy + tenant extensions), ServiceOffering | booking-service | |
| `bookings` | Booking, Slot, Assignment | booking-service | exists |
| `quotations` | QuoteRequest, Quotation, QuoteLine | new or booking | Shared by B2C and B2B |
| `projects` | Project, Milestone, ProjectDocument | project-service | exists |
| `materials` | Product, SKU, UoM, PriceList, Inventory (optional) | new `catalog-service` | HSN codes |
| `procurement` | RFQ, PurchaseOrder, Dispatch, GRN, Invoice | new `procurement-service` | B2B |
| `rental` | Equipment, Availability, RentalBooking, Operator, Meter readings | new `rental-service` | Machinery and vehicles |
| `jobs` | JobPosting, Application, Engagement | new `jobs-service` | Labour hiring |
| `reviews` | Review, RatingSummary | review-service | exists |
| `social` | Post, Reel, Comment, Follow | new | Moderation rules |
| `payments` | Escrow, Wallet, Settlement | payment-service | horizontal |

### 5.3 DOMAIN-level configuration pack: `construction`

- The GLOBAL construction taxonomy (categories, trades, material classes), units of measure
  (bags, m³, sq ft, brass, MT), and HSN/SAC mappings.
- Default rules (48 h free cancellation for site work, milestone payment templates, a 3-way-match
  tolerance of 2%).
- Default registration forms per profile (for example, a civil engineer requires a degree
  certificate and registration number).
- Default layouts and presets (for example, "Service Marketplace" home with a category grid and
  "Find a professional" search).
- Default notification templates (quote received, PO issued, delivery due).
- A fixture pack for the wizard preview (03 §2.1).

---

## 6. Future extensibility: domain modules on an unchanged core

```mermaid
flowchart TB
    subgraph CORE["MULTI-TENANT CORE (unchanged across industries)"]
        direction LR
        K1["Tenancy · Factory"] --- K2["Identity · RBAC"] --- K3["Configuration · Experience engines"]
        K3 --- K4["Entitlements"] --- K5["Party model"] --- K6["Payments · Notifications · Media · Audit · Search · Messaging"]
    end
    subgraph SPI["Domain Module SPI (contract)"]
        S1["module manifest: key, version, features, permissions"]
        S2["config document schemas + DOMAIN defaults"]
        S3["party capabilities & profile types"]
        S4["registry: layouts, blocks, nav items (frontend)"]
        S5["events published/consumed"]
        S6["schema migrations (per tenant)"]
    end
    CORE --- SPI
    SPI --- M1["BuildOS Construction"]
    SPI --- M2["BuildOS Real Estate<br/>(existing PROPERTY vertical keys)"]
    SPI --- M3["BuildOS Healthcare"]
    SPI --- M4["BuildOS Logistics"]
    SPI --- M5["BuildOS Education / Retail<br/>(existing FEE_COLLECTION)"]
```

**Module manifest (conceptual contents):** `moduleKey`, `version`, `requiresCore ≥ x.y`,
`features[]` (for the Feature Catalog, with dependencies), `permissions[]` (namespaced
`<module>.*`), `configSchemas[]`, `domainDefaults`, `partyCapabilities[]`, `frontendRegistry`
(layouts, blocks, routes, nav items), `events`, `services` (the backends that implement it),
`migrations`.

**Rules**

1. The core **never imports** a domain module. Modules depend on core SPIs only.
2. A domain module cannot add a new *configuration level* or bypass tenancy. It uses `tenant-common` like every other service.
3. `Vertical` (existing enum) becomes **data**, a registry of installed domain modules. A new
   industry is a deployment of module services plus catalog entries. It is not an enum change in
   the core.
4. A tenant runs **one primary domain**. It may add compatible modules from other domains
   (for example, Construction + Real Estate listings) if entitled.
