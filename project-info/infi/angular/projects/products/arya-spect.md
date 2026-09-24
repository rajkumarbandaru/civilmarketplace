# 6. Arya Spect (IMBIL Telecom)

> Product folder: `aryadash-dashboard/src/config/arya-spect/` - built from the shared AryaDash engine. [Back to all projects](../README.md) - [Interview guide](../../00-interview-guide.md)

## What it is

Monitoring / lawful-intercept style console: targets, users, audit events, error logs, tenants and subscriptions.

## Key facts

| Item | Value |
|---|---|
| Browser title | IMBIL Telecom |
| Theme colours | primary `#d50000`, fade `#181818` |
| Timezone | UTC+0100 |
| localStorage prefix | `arya-spect` |
| Session cookie | `SESSIONID_ARYA_SPECT` |
| Auth mode | session cookie |
| Login URL (user / admin) | `li/user_signin/` / `service_mgmt/service-management/` |
| Menu entries | 7 |
| Pages in global.json | 7 |
| Screen JSON files | 6 |
| Routes | 11 (login + 10) using `DashboardComponent`, `LoginPageComponent`, `TableviewComponent` |
| Environment | `{ production: false, showVersion: true, configBase: "arya-spect/", productType: '' }` |
| Brand assets | `src/assets-arya-spect/` |
| Backend API modules (dev proxy) | `/billing_reports/`, `/billing_validations/`, `/email_mgmt/`, `/li/`, `/metrics/`, `/smesg_mgmt/`, `/smesg_mgmt/ws/`, `/user_mgmt/`, `/voice_mgmt/ws/` |

## How to run

```bash
cd ~/VIPLine-billing/aryadash-dashboard
ng serve --configuration=arya-spect   # http://localhost:4200
ng build --configuration=development-arya-spect
```

## Menu and access

| Menu | Route | Sub-pages | Who can see it |
|---|---|---|---|
| Dashboard | `/dashboard` | Dashboard | everyone |
| Targets | `/targets` | Targets | everyone |
| Users | `/users` | Users | everyone |
| Audit Events | `/auditevents` | Audit Events | role = GlobalAdmin; mode strict |
| Error Logs | `/errorlogs` | Error Logs | role = GlobalAdmin; mode strict |
| Tenant | `/tenant` | Tenant | everyone |
| Subscriptions | `/subscriptions` | Subscriptions Request, Subscriptions | everyone |

## Product-specific features (keys in global.json)

- `change-password` - Change-password flow
- `user-profile` - Role-based profile icon
- `summary-gauges` - Dashboard gauges
- `topn-tables` - Dashboard top-N tables
- `metrics-summary` - Dashboard metric tiles

## View types used

Page items in `global.json -> pages`: `table` x7, `modal-form` x5

Definitions inside screen JSON files: `form` x10, `table` x7, `modal-form` x4

## Flow

```mermaid
flowchart TD
    B["ng serve --configuration=arya-spect"] --> C["global.json + routes.ts of arya-spect baked into bundle"]
    C --> LG["Login page - IMBIL Telecom"]
    LG --> AU["POST li/user_signin/ (admin: service_mgmt/service-management/) - SHA-256 password"]
    AU --> CK["Cookie SESSIONID_ARYA_SPECT + store-data in localStorage"]
    CK --> LAND["Landing page (per role)"]
    LAND --> G["AuthGuard + access rules"]
    G --> MENU["Menu"]
    MENU --> M0["Dashboard"]
    MENU --> M1["Targets"]
    MENU --> M2["Users"]
    MENU --> M3["Audit Events"]
    MENU --> M4["Error Logs"]
    MENU --> M5["Tenant"]
    MENU --> M6["Subscriptions"]
    M0 --> TV["TableviewComponent / DashboardComponent renders page items"]
    M1 --> TV
    M2 --> TV
    M3 --> TV
    M4 --> TV
    M5 --> TV
    M6 --> TV
    TV --> JS["AppConfig.readConfig(screen JSON)"]
    JS --> API["RestAPIService -> /billing_reports/, /billing_validations/, /email_mgmt/, /li/"]
    API --> UI["Table / form / chart shown to user"]
```

## Screen JSON files

|  |  |  |  |
|---|---|---|---|
| `auditevents.json` | `errorlogs.json` | `subscription.json` | `targets.json` |
| `tenant.json` | `users.json` |  |  |

## Talking points for an interview

- Built from the **same codebase** as the other products; only `src/config/arya-spect/`, its environment file and asset folder differ.
- Adding a screen here = add a JSON file + a `pages`/`menu` entry in `global.json` + a route in `routes.ts` (component `TableviewComponent`).
- Uses **session-cookie** login: `sessionKey` from the login response is stored in cookie `SESSIONID_ARYA_SPECT` and sent as the `SESSIONID` header.
- Menu items carry `access` rules, so the menu, the route guard and the page builder all hide what the role may not see.
