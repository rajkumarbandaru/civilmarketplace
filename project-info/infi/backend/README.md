# Infi Mobile BSS — Backend Flow Documentation

Walkthroughs of the three core subscriber journeys in `billing_micro_services`, a Java 8 / Spring Boot
microservices telecom BSS running on MySQL and Kubernetes. Each document covers:

- a Mermaid sequence diagram
- a step-by-step trace with file:line references
- UI-facing, internal, and external API calls
- where orders and user data are stored, and which tables are written
- engineering notes on risks found while reading the code

| # | Flow | What it covers |
|---|------|----------------|
| 1 | [Purchase](01-purchase-flow.md) · [interactive HTML](01-purchase-flow.html) | Plan browse → payment (Stripe/wallet) → `order_table` / `order_item` → SIM allocation → USPS/UPS shipment |
| 2 | [Activation](02-activation-flow.md) · [interactive HTML](02-activation-flow.html) | SIM/eSIM activation → `subscription` + `sim_info` PreActive → `ACTIVATED` event → Verizon / T-Mobile / GUPI MDN allocation → Active |
| 3 | [Recharge](03-recharge-flow.md) · [interactive HTML](03-recharge-flow.html) | `PaymentFacade` validator/routing/dispatch → webhook confirm → `prepaid_subscription_records` → `BUNDLE_ACTIVATION` event → carrier |
| 4 | [Usage / CDR](04-cdr-flow.html) (HTML only) | Verizon ReportGateway pull, T-Mobile file drop, NetworkIP collection → scheduled parse + bulk insert → MongoDB / MySQL |
| 5 | [Postpaid billing](05-postpaid-billing-flow.html) (HTML only) | Monthly invoice cron → `Topic.Invoice` → billing-integ raises invoice → SES email + per-carrier SMS; dunning every 2h |

## Overview pages

- [index.html](index.html): landing page linking everything below
- [engineer-guide.html](engineer-guide.html) · [06-engineer-guide.md](06-engineer-guide.md): how to work in this system at ~5 years' experience — onboarding, change protocol, task recipes, debugging playbooks, risk radar, interview talking points
- [architecture.html](architecture.html): whole-system map with 6 animated journeys
- [tech-stack.html](tech-stack.html): every tool used and how it works in this system
- [07-project-handbook.md](07-project-handbook.md): the whole repository in one file — module taxonomy, the 23
  deployable services with their ports, data/config/event/security reality, build and deploy, every in-repo
  tool and script, newcomer traps, glossary
- [08-interview-guide.html](08-interview-guide.html): how to describe this project out loud — 30-second /
  2-minute / 10-minute pitches, numbers to have ready, a whiteboard sequence, STAR stories, and answers to the
  six questions this architecture invites

## Services involved

| Service | Role |
|---|---|
| catalogue-management | Plans, bundles, add-ons |
| self-service-payment | Payments, gateway routing, webhooks, order creation, recharge fulfilment |
| wallet-management | Wallet balance ledger |
| inventory-management | SIM / device stock, ICCID validation, warehouse |
| shipment-management | USPS/UPS labels, pickup, tracking |
| user-management | Users, orders, subscription activation |
| event-mgmt-utils | DB-polling event bus (`Topic.Subscription`, `Topic.PrepaidSubscriptionRecord`, …) |
| mno_mgmt + mno-gateway-api | Verizon integration |
| pwg-mno-mgmt + pwg-api | T-Mobile (vCare/PWG) integration |
| gupi_mgmt | Tata/GUPI integration |
