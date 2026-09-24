# Angular UI — VIPLine-billing (AryaDash)

Interview-prep notes for the Angular side of `~/VIPLine-billing`.
Open the HTML files in a browser (flowcharts need internet once to load Mermaid):

```bash
xdg-open ~/RAJKUMAR/project-info/infi/angular/index.html
```

## Read in this order

| # | File | What it covers |
|---|---|---|
| 1 | [index.html](index.html) / [00-interview-guide.md](00-interview-guide.md) | **Start here.** 60-second pitch, architecture, 9 flowcharts (build, bootstrap, login, guard, menu→page, form submit, HTTP building, session expiry, component communication), real-time, designer, Angular concepts mapped to files, 20 interview Q&As, improvements, how to run |
| 2 | [03-senior-developer-playbook.html](03-senior-developer-playbook.html) / [.md](03-senior-developer-playbook.md) | **For 5+ years of experience.** How to pitch the project at three depths, STAR ownership stories taken from real code, senior Q&A (blast radius, performance, leaks, testing, migration, security, review, estimation, mentoring), a system-design round, live-coding patterns, an improvement roadmap, behavioural prompts and a two-week checklist |
| 3 | [projects/index.html](projects/index.html) / [projects/README.md](projects/README.md) | How many projects there are (2 apps + 14 products) with a table and a relationship chart |
| 4 | [projects/01-aryadash-dashboard.html](projects/01-aryadash-dashboard.html) / [.md](projects/01-aryadash-dashboard.md) | The Angular 15 engine: layers, request lifecycle, screen JSON anatomy |
| 5 | [projects/02-aryadash-designer.html](projects/02-aryadash-designer.html) / [.md](projects/02-aryadash-designer.md) | The Angular 21 AI config generator: signals, streaming, server.js |
| 6 | `projects/products/<product>.html` / `.md` | One page per product: facts, run command, menu + access, features, view types, flowchart, screen files, talking points |

## Products (one page each)

| # | Product | HTML | Markdown |
|---|---|---|---|
| 1 | VIP Line | [html](projects/products/vip-line.html) | [md](projects/products/vip-line.md) |
| 2 | Arya CRM | [html](projects/products/arya-crm.html) | [md](projects/products/arya-crm.md) |
| 3 | Arya Billing Admin (Xmobee) | [html](projects/products/arya-billing-admin.html) | [md](projects/products/arya-billing-admin.md) |
| 4 | Arya Billing (reseller) | [html](projects/products/arya-billing.html) | [md](projects/products/arya-billing.md) |
| 5 | AryaConnect | [html](projects/products/arya-connect.html) | [md](projects/products/arya-connect.md) |
| 6 | Arya Spect | [html](projects/products/arya-spect.html) | [md](projects/products/arya-spect.md) |
| 7 | MetriXcope (BSS Metrics) | [html](projects/products/bss-metrics.html) | [md](projects/products/bss-metrics.md) |
| 8 | Daily Reports | [html](projects/products/daily-reports.html) | [md](projects/products/daily-reports.md) |
| 9 | EDR System | [html](projects/products/edr-system.html) | [md](projects/products/edr-system.md) |
| 10 | Multi-Tenant Admin | [html](projects/products/multi-tenant.html) | [md](projects/products/multi-tenant.md) |
| 11 | OCS Monitor | [html](projects/products/ocs-monitor.html) | [md](projects/products/ocs-monitor.md) |
| 12 | Portease MNP Solutions | [html](projects/products/port-mnp-solutions.html) | [md](projects/products/port-mnp-solutions.md) |
| 13 | Projectile | [html](projects/products/projectile.html) | [md](projects/products/projectile.md) |
| 14 | Wirepay | [html](projects/products/wirepay.html) | [md](projects/products/wirepay.md) |

## One-screen summary

```
                 aryadash-designer (Angular 21, AI)
                          │ writes JSON configs
                          ▼
 aryadash-dashboard (Angular 15 engine, ~80 components)
   angular.json --configuration=<product>
     swaps environment.ts, config/global.json, routes.ts, assets
                          │
   ┌──────────┬───────────┼────────────┬──────────── ... 14 products
 VIP Line  Arya CRM  Billing Admin   Wirepay
                          │
 Browser: Login → AuthGuard → Menu → TableviewComponent
          → view items (table/form/wizard/...) → AppConfig.readConfig(screen.json)
          → RestAPIService (SESSIONID or Bearer) → backend (user_mgmt, billing, ...)
```

These files are generated from the real repo. The per-product pages and every `.html` file come
from a script; the guide and app pages are hand-written Markdown.
