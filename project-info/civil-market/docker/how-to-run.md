# 06 — How to Run (start / stop / rebuild / debug)

## Prerequisites

| Tool | Version | Check |
|---|---|---|
| JDK | **21** | `/usr/lib/jvm/java-21-openjdk-amd64/bin/java -version` |
| Maven | 3.9+ | `mvn -version` |
| Node.js / npm | 18+ (Docker uses 20) | `npm -v` |
| Docker + Compose v2 | recent | `docker --version`, `docker compose version` |
| RAM | ~12–16 GB for the full stack | use the lean overlay |

**Important:** on this machine the default `java`/`mvn` point to JDK 17. This project needs 21, so
prefix Maven commands with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`.
Optional helper:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64    # put in ~/.bashrc to make it permanent
```

---

## Option 1 — Everything in Docker (recommended)

### Step 1: Build the backend jars (Docker does NOT compile Java)

```bash
cd ~/RAJKUMAR/backend
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn clean package -DskipTests
```
This creates `backend/<service>/target/*.jar` for every module. The service Dockerfiles copy those jars.

### Step 2: Check `docker/.env`

```bash
cd ~/RAJKUMAR/docker
cp .env.example .env     # only if .env doesn't exist yet
```
At minimum it needs `JWT_SECRET` (a base64 key of at least 256 bits, e.g. `openssl rand -base64 64`) and
`MYSQL_ROOT_PASSWORD`. Channels set to `log` need no real credentials.

### Step 3: Start the stack

```bash
cd ~/RAJKUMAR/docker
docker compose -f docker-compose.yml -f docker-compose.lean.yml up -d --build
```
(Without the lean overlay: `docker compose up -d --build`. It uses much more memory.)

The first start takes a few minutes: MySQL initializes, then config-server, then Eureka, then the services.

### Step 4: Check that everything is up

```bash
docker compose ps                                    # all should be "running"/"healthy"
curl -s http://localhost:8080/actuator/health         # gateway → {"status":"UP"}
curl -s http://localhost:8761/eureka/apps -H 'Accept: application/json' \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print([a['name'] for a in d['applications']['application']])"
```
Or open the Eureka dashboard at http://localhost:8761 and check that every service is listed.

### Step 5: Use it

| What | URL | Login |
|---|---|---|
| **Web app** | http://localhost:3000 | `superadmin@civileng.test` / `Password123!` |
| Tenant subdomain | http://&lt;tenant&gt;.localhost:3000 | |
| API gateway | http://localhost:8080 | |
| Eureka | http://localhost:8761 | |
| Config server | http://localhost:8889/&lt;service&gt;/default | |
| Swagger (per service) | http://localhost:&lt;host-port&gt;/swagger-ui.html | |
| RabbitMQ UI | http://localhost:15672 | guest / guest |
| Elasticsearch | http://localhost:9200 | |
| Zipkin | http://localhost:9411 | |
| Prometheus | http://localhost:9090 | |
| Grafana | http://localhost:3001 | admin / admin |
| MySQL | localhost:3309 | civil_user / civil_pass (or root) |

Other seeded users (same password): `admin@`, `customer@`, `worker@`, `engineer@`,
`architect@`, `contractor@`, `surveyor@`, `supplier@`, `labour@` + `civileng.test`.

Quick API test:
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"superadmin@civileng.test","password":"Password123!"}'
```

---

## Option 2 — Backend in Docker, UI in dev mode (best for UI work)

```bash
# 1. backend + infrastructure (everything except the frontend container)
cd ~/RAJKUMAR/docker
docker compose -f docker-compose.yml -f docker-compose.lean.yml up -d \
  mysql redis zookeeper kafka rabbitmq elasticsearch \
  config-server service-registry api-gateway tenant-service \
  auth-service user-service booking-service payment-service notification-service \
  admin-service project-service review-service audit-service messaging-service \
  support-service search-service

# 2. UI with hot reload
cd ~/RAJKUMAR/frontend
npm install          # first time
npm run dev          # → http://localhost:5173   (/api proxied to localhost:8080)
```

Start only what your screen needs. The minimum for logging in is:
`mysql redis zookeeper kafka config-server service-registry api-gateway tenant-service auth-service admin-service`.

---

## Option 3 — One service from your IDE / terminal (for debugging Java)

Keep infrastructure, config-server, Eureka and the gateway in Docker. Stop the container for the service you
want to debug, and run that service locally:

```bash
cd ~/RAJKUMAR/docker
docker compose stop booking-service

cd ~/RAJKUMAR/backend
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -pl booking-service -am spring-boot:run
```
When running outside Docker, the service must use **host** addresses, not container names:
config server `localhost:8889`, Eureka `localhost:8761`, MySQL `localhost:3309`,
Redis `localhost:6381`, Kafka `localhost:29093`. Override them with environment variables or a local profile
if the config-repo defaults use the Docker names.

Also note that a service running on your host registers in Eureka with your host's address. The gateway runs
inside Docker and may not be able to reach that address. If gateway calls fail, call the service directly on its port
(adding the `X-User-*` / `X-Tenant-Id` headers yourself), or run the gateway locally as well.

---

## Everyday commands (run from `~/RAJKUMAR/docker`)

```bash
# status / logs
docker compose ps
docker compose logs -f auth-service              # follow one service
docker compose logs --tail=200 notification-service   # OTPs appear here when provider=log

# after changing Java code in one service
cd ../backend && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  mvn -q -pl auth-service -am package -DskipTests && cd ../docker
docker compose up -d --build auth-service

# after changing a shared lib (audit-common / tenant-common / web-common)
#   rebuild every service that uses it: -pl <svc1>,<svc2> -am, then up -d --build those services

# after changing frontend code (Docker mode)
docker compose up -d --build frontend

# after changing config-server's config-repo YAML
#   rebuild config-server (the configs are on its classpath), then restart the affected services
docker compose up -d --build config-server && docker compose restart auth-service

# restart / stop
docker compose restart api-gateway
docker compose stop                 # stop everything, keep containers
docker compose down                 # remove containers, KEEP data volumes
docker compose down -v              # remove containers AND DATA (DB wiped, init SQL re-runs)

# open a DB shell
docker exec -it civil_mysql mysql -ucivil_user -pcivil_pass
#   SHOW DATABASES;   -- per-tenant schemas look like civil_engineer_auth_<tenant>
```

## Tests and checks

```bash
cd ~/RAJKUMAR/backend && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn test
cd ~/RAJKUMAR/frontend && npx tsc --noEmit && npm run lint
```
CI configuration: `.github/workflows/ci.yml`.

---

## Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| `COPY ... target/*.jar: no source files` during `docker compose build` | Jars not built. Run the Maven package step first |
| `release version 21 not supported` | Maven is using JDK 17. Set `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` |
| Service exits right after start, "Could not connect to config server" | config-server not healthy yet. Check `docker compose logs config-server`, then `restart <svc>` |
| Service fails on first boot with a DB connection error | MySQL was still initializing. Run `docker compose restart <svc>` |
| Gateway returns **503** for a route | The target service isn't registered in Eureka yet (wait about 30 s) or has crashed. Check the logs |
| Gateway returns **401** | Missing, expired or wrong JWT, or `JWT_SECRET` differs between auth-service and api-gateway |
| **403** with an empty body on login | Origin not allowed by CORS (unusual host/port), or tenant mismatch between the token and the subdomain |
| UI shows old code after a rebuild | Hard-refresh (the PWA service worker caches). `index.html` itself is never cached |
| `VITE_*` change has no effect | They are build-time values. Rebuild the frontend image or restart `npm run dev` |
| Port already in use | Change the matching `HOST_PORT_*` in `docker/.env`, then `docker compose up -d` |
| Machine very slow / OOM | Use `-f docker-compose.lean.yml` and start only the services you need |
| No OTP email arrives | `EMAIL_PROVIDER=log` or placeholder SMTP credentials. Read the OTP from `docker compose logs notification-service` |
| Social login buttons do nothing | Needs `AUTH_PROFILE=docker,social` plus real Google/Facebook credentials |
| Ask AI says unavailable | `GEMINI_API_KEY` is not set in `docker/.env` |
