# 04 — Docker & Infrastructure

All containers are defined in **`docker/docker-compose.yml`**. The Compose project name is pinned
to `rajkumar`, so container and volume names don't change if the folder moves.
Settings come from **`docker/.env`**, which Compose reads automatically when you run it from `docker/`.

## Files

| File | Purpose |
|---|---|
| `docker/docker-compose.yml` | The full stack: infrastructure + 16 Spring services + frontend + nginx |
| `docker/docker-compose.lean.yml` | Overlay that caps JVM heap/memory (`MaxRAMPercentage=50`, SerialGC, `mem_limit` 512–768 MB). Use it on a laptop |
| `docker/.env` | Real values: host ports, DB users, providers, secrets. **Do not commit.** |
| `docker/.env.example` | Template for `.env` |
| `docker/database/init/01-create-databases.sql` | Runs **once**, on first MySQL start: creates each service's database and the `civil_user`/`civil_pass` user |
| `docker/database/init/02-tenant-grants.sql` | Grants for per-tenant schemas |
| `docker/monitoring/prometheus.yml` | Prometheus scrape targets |
| `docker/monitoring/grafana/` | Grafana datasources and dashboards provisioning |
| `docker/nginx/` | Optional edge reverse proxy (`sites/` is empty, so it isn't wired up yet) |
| `backend/<svc>/Dockerfile` | `eclipse-temurin:21-jre-alpine` + `COPY backend/<svc>/target/*.jar app.jar` |
| `frontend/Dockerfile` | Multi-stage node build → nginx |

Build context for every image is the **repo root** (`context: ..`), which is why the Dockerfiles use
repo-relative paths such as `backend/auth-service/target/*.jar`.

## Containers

### Infrastructure

| Service | Image | Host port(s) | Used for |
|---|---|---|---|
| mysql (`civil_mysql`) | mysql:8.0 | **3309**→3306 | One DB per service, one schema per tenant. Volume `mysql_data` |
| redis (`civil_redis`) | redis:7-alpine | **6381**→6379 | OTPs, sessions, gateway rate limit, caching |
| zookeeper | cp-zookeeper:7.5.0 | 2181 | Kafka coordination |
| kafka (`civil_kafka`) | cp-kafka:7.5.0 | 9092, **29093**→29092 | Event bus. Inside Docker `kafka:9092`; from the host `localhost:29093`. Topics are auto-created |
| rabbitmq | rabbitmq:3.12-management | 5672, 15672 (UI) | Queues for notification-service. UI guest/guest |
| elasticsearch | elasticsearch:8.11.0 | 9200, 9300 | Search indices (single-node, security off, 512 MB heap) |
| zipkin | openzipkin/zipkin:2.24 | 9411 | Distributed tracing (in-memory) |
| prometheus | prom/prometheus | 9090 | Scrapes `/actuator/prometheus` |
| grafana | grafana/grafana | 3001→3000 | Dashboards (admin/admin) |

### Application

| Service | Host port | depends_on (startup gates) |
|---|---|---|
| config-server | 8889 | — (healthcheck `/actuator/health`) |
| service-registry | 8761 | config-server healthy |
| api-gateway | 8080 | redis healthy, service-registry healthy, config-server healthy |
| auth-service | 8093 | mysql, redis healthy, kafka, registry healthy, config healthy |
| user-service | 8094 | mysql, kafka, registry, config |
| booking-service | 8083 | mysql, redis, kafka, registry, config |
| payment-service | 8084 | mysql, kafka, registry, config |
| notification-service | 8085 | mysql, kafka, redis, rabbitmq, registry, config |
| admin-service | 8086 | mysql, kafka, registry, config |
| project-service | 8096 | mysql, kafka, registry, config |
| tenant-service | 8099 | mysql, kafka, registry, config |
| support-service | 8098 | mysql, kafka, registry, config |
| review-service | 8089 | mysql, registry, config |
| audit-service | 8095 | mysql, kafka, registry, config |
| messaging-service | 8097 (→8096) | mysql, kafka, registry, config |
| search-service | 8092 | elasticsearch, mysql, kafka, registry, config |
| frontend | 3000 | api-gateway |
| nginx | 8010, 8443 | api-gateway, frontend |

## Startup order (what Compose does)

```
1. mysql, redis, zookeeper, rabbitmq, elasticsearch, zipkin, prometheus   (in parallel)
   kafka (after zookeeper) · grafana (after prometheus)
          │
2. config-server ──(wait until healthy)──┐
          │                              │
3. service-registry ──(wait until healthy)
          │
4. api-gateway + all business services (in parallel)
     each one: fetch config from config-server → connect MySQL/Redis/Kafka →
               run Flyway for every tenant schema → register in Eureka
          │
5. frontend (nginx) → nginx edge
```

Note that `service_started` for mysql only means the container has started, not that MySQL is ready.
A service that boots before MySQL finishes its first-time initialization can fail. Restarting that
service fixes it: `docker compose restart <service>`.

The **tenant-service** is not in anyone's `depends_on`. Other services read the tenant registry
directly over JDBC at boot, so MySQL (not the tenant-service container) is what they need.

## Networking

- One bridge network: `civil-network`. Containers reach each other by **service name**:
  `mysql:3306`, `redis:6379`, `kafka:9092`, `rabbitmq:5672`, `elasticsearch:9200`,
  `config-server:8888`, `service-registry:8761`, `api-gateway:8080`.
- From your **host machine**, use `localhost:<host port>` (e.g. MySQL at `localhost:3309`).
- Kafka has two listeners: `PLAINTEXT://kafka:9092` for containers and
  `PLAINTEXT_HOST://localhost:29093` for the host.

## Volumes (data survives `docker compose down`)

`mysql_data`, `redis_data`, `kafka_data`, `rabbitmq_data`, `elasticsearch_data`,
`prometheus_data`, `grafana_data`.

`docker compose down -v` **deletes** them, including all database data. After that, MySQL re-runs
the `database/init` scripts on the next start.

## Key `.env` settings

| Variable | Current value | Effect |
|---|---|---|
| `HOST_PORT_GATEWAY` | 8080 | Gateway on the host |
| `HOST_PORT_FRONTEND` | 3000 | UI on the host |
| `HOST_PORT_CONFIG` | 8889 | Config server on the host |
| `HOST_PORT_MYSQL` | 3309 | MySQL on the host |
| `HOST_PORT_AUTH` / `USER` / `PROJECT` | 8093 / 8094 / 8096 | Direct service ports |
| `HOST_PORT_REDIS` / `KAFKA` | 6381 / 29093 | |
| `HOST_PORT_NGINX` / `_SSL` | 8010 / 8443 | Edge nginx |
| `AUTH_PROFILE` | `docker` | Seeds test users. `docker,social` also turns on Google/Facebook login |
| `EMAIL_PROVIDER` / `SMS_PROVIDER` / `WHATSAPP_PROVIDER` | brevo / log / log | `log` = print to the log only |
| `JWT_SECRET` | (secret) | Shared by auth-service (signs) and api-gateway (verifies). **They must match** |
| `RAZORPAY_KEY_ID/SECRET/WEBHOOK_SECRET` | (secret) | payment-service |
| `GEMINI_API_KEY` | (secret) | support-service's Ask AI |
| `*_DB_USERNAME` / `*_DB_PASSWORD` | civil_user / civil_pass | Per-service DB credentials |
