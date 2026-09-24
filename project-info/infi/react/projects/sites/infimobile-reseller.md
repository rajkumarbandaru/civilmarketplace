# Site 2 · InfiMobile Reseller Portal — `config/reseller`

A B2B portal for resellers, retailers and their staff: wallet, stock, activations (single and
bulk), recharges, staff/retailer management, commissions and reports. Same engine, side-nav
layout, full-page authentication.

## Facts

| Item | Value |
|---|---|
| Brand / theme | InfiMobile |
| Layout | **side nav** + topbar (wallet balance, search, light/dark toggle) |
| Auth | `required: true`, `initialPage: login`, `landingPage: dashboard` |
| Roles | Reseller Aggregator · Reseller Retailer · Reseller Staff |
| Size | 39 pages · 39 modals · 222 scripts · 25 CSS files |
| API contract | 23 backends · 112 APIs (wallet, inventory, user, KYC, MNO, payment, billing reports…) |
| External scripts | Google Analytics, Meta pixel, config-driven contact widget |
| Run | `npm run dev:reseller` (port 4011) |

## Pages

| Area | Pages |
|---|---|
| Auth | `login`, `forgot-password`, `reset-password`, `newpassword`, `verify-2fa`, `secondary-validation` |
| Home | `dashboard` (KPI cards, wallet usage donut, recent activations / recharges tables) |
| Sell | `activations` (new / port-in), `bulk-requests` (bulk upload + bulk details), `activation-status`, `recharge`, `plans` |
| Stock | `stock-request`, `return-products`, `product-management` |
| People | `staff-management`, `retailer-management`, `create-new-staff`, `create-new-reseller`, `add-commission` |
| Money | `load-wallet`, payment result pages (`payment-success-page`, `-failed-page`, `-pending`, `-timeout`, `-fraudulent`) |
| Other | `reports`, `usage`, `account`, `customer-tools` |

## Login → dashboard

```mermaid
flowchart TD
  A["/any-page"] --> G{"AppShell: authenticated?"}
  G -- no --> L["/login (no chrome)"]
  L --> H["sha256-password.js"] --> API["resellerLogin API"] --> V["signInValidation.js"]
  V -- ok --> S["sessionStorage rpAuthenticated + sessionId<br/>globalData.userInfo (localStorage)"]
  S --> R["return URL or /dashboard"]
  V -- fail --> T["toast error"]
  G -- yes --> P["render page inside side-nav layout"]
  P --> X{"API returns 403 / Invalid session?"}
  X -- yes --> CL["clear session, remember URL, go to /login"]
```

## Dashboard load

```mermaid
sequenceDiagram
  participant D as dashboard.json (section load event)
  participant API as executeApi
  participant UI as KPI cards
  D->>D: dataReady from GlobalDataProvider (userInfo.resellerId)
  D->>API: metricsApi reseller_metrics/{resellerId}
  D->>API: stock summary, wallet, recent activations, recharges
  Note over D,API: one loader shown for the whole chain
  API-->>UI: results in localData → TextBlock dataPath / tables
```

## Bulk activation (bulk-requests)

```mermaid
flowchart LR
  U["Upload CSV/XLSX (FileUploader)"] --> P["parse + validate rows"]
  P --> C["Check Bulk Compatibility API (batch)"]
  C --> PO["poll status"]
  PO --> E["edit / fix rows · download incompatible"]
  E --> CF["confirm modal → Process Bulk Batch"]
  CF --> H["Bulk Details tab: job history + per-batch detail modal, QR download"]
```

## Things I can talk about here

- Dark → light theming with tokens, pre-hydration theme script, `critical.css` gotcha.
- localStorage persistence race and shimmer-forever KPIs.
- Table action buttons resolving through the `actions` mapping.
- Login page and step pages redesign inside the JSON engine (not a new app).
