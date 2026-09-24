# Civil Market — Project & Interview Documentation

Documentation for the **Civil Engineering Marketplace** (`~/RAJKUMAR`): a multi-tenant services
marketplace built as 16 Spring Boot microservices behind a Spring Cloud Gateway, with a React 18 +
TypeScript front end, orchestrated with Docker Compose.

Every `.md` here has a matching `.html` (built with `tools/build_html.py`), so it reads either in an
editor or in a browser.

---

## Start here

| If you want to… | Read |
|---|---|
| Explain this project in an interview | **[00-interview-guide.md](00-interview-guide.md)** — pitch, facts, walkthrough, STAR stories |
| Understand the whole system | [overview/architecture.md](overview/architecture.md) |
| Run it on your machine | [docker/how-to-run.md](docker/how-to-run.md) |
| See a flow end to end | [overview/action-flows.html](overview/action-flows.html) — 39 actions traced |

---

## Tool-wise documentation

### UI — React 18, TypeScript, Vite, MUI
| File | What it covers |
|---|---|
| [ui/ui-guide.md](ui/ui-guide.md) | Stack, folder structure, how the UI calls the API, dev vs Docker mode, environment variables |
| [ui/ui-interview.md](ui/ui-interview.md) | 21 questions: Redux vs React Query, sessionStorage, interceptors, build-time env, caching, theming |

### Backend — Java 21, Spring Boot 3.2, Spring Cloud
| File | What it covers |
|---|---|
| [backend/backend-guide.md](backend/backend-guide.md) | All 16 services with ports and databases, the gateway route table, coding conventions |
| [backend/backend-interview.md](backend/backend-interview.md) | 33 questions: gateway filters, tenancy, Kafka idempotency, transactions, security, Spring specifics |

### Docker — Compose, images, infrastructure
| File | What it covers |
|---|---|
| [docker/docker-guide.md](docker/docker-guide.md) | Every container, startup order, networking, volumes, `.env` settings |
| [docker/how-to-run.md](docker/how-to-run.md) | Three ways to run it, daily commands, troubleshooting table |
| [docker/docker-interview.md](docker/docker-interview.md) | 21 questions: build strategy, health gating, secrets, production gaps, Kubernetes |

### Overview — architecture and flows
| File | What it covers |
|---|---|
| [overview/architecture.md](overview/architecture.md) | The seven layers, how UI/backend/Docker connect, design decisions, repo layout |
| [overview/request-flows.md](overview/request-flows.md) | Step-by-step: page load, login, authenticated call, Feign, Kafka, payments, tenancy |
| [overview/flowcharts.md](overview/flowcharts.md) | 15 Mermaid diagrams: architecture, startup, gateway decisions, sequences, state machines |
| [overview/action-flows.html](overview/action-flows.html) | 39 user/admin/system actions, each with a step table and sequence diagram |

### Interview preparation
| File | What it covers |
|---|---|
| [interview/system-design.md](interview/system-design.md) | Whiteboarding this system, capacity, failure modes, scaling order, design-fresh answer |
| [interview/star-stories.md](interview/star-stories.md) | 8 real bugs and decisions as STAR stories, with the file that proves each |
| [interview/rapid-fire.md](interview/rapid-fire.md) | 60 one-line answers across project, gateway, data, events, payments, UI, Docker, behavioural |

---

## Quick facts to have ready

| | |
|---|---|
| Stack | Java 21 · Spring Boot 3.2 · Spring Cloud 2023.0.1 · React 18 · TypeScript · Vite · MySQL 8 · Redis 7 · Kafka · Elasticsearch 8 · Docker Compose |
| Services | config-server, service-registry (Eureka), api-gateway, auth, user, booking, payment, notification, admin, project, review, search, messaging, support, audit, tenant |
| Entry points | UI http://localhost:3000 · Gateway http://localhost:8080 · Eureka http://localhost:8761 · Grafana http://localhost:3001 |
| Test login | `superadmin@civileng.test` / `Password123!` (seeded by the `docker` profile) |
| One-line summary | Tenant resolved at the edge, JWT validated once at the gateway, each service owns its schema, anything asynchronous goes through Kafka |

---

## Rebuilding the HTML

```bash
cd ~/RAJKUMAR/project-info/civil-market
python3 tools/build_html.py          # every .md -> .html (Mermaid diagrams render in the browser)
python3 tools/gen_action_flows.py    # regenerates overview/action-flows.html
```

`action-flows.html` is generated from a data structure in its script, not from Markdown, because
each action's step table and sequence diagram come from the same source.
