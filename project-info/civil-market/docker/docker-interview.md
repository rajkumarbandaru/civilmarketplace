# Docker & DevOps Interview Questions

Answers taken from this codebase. Reference: [docker-guide.md](docker-guide.md) ·
[how-to-run.md](how-to-run.md).

---

## A. Images and builds

**1. Walk me through your Dockerfiles.**
Two kinds. Each Spring service has a tiny one: `eclipse-temurin:21-jre-alpine`, copy
`backend/<service>/target/*.jar`, `ENTRYPOINT java -jar`. The frontend is multi-stage: `node:20` runs
`npm ci` and `npm run build`, then `nginx:alpine` serves `dist`.

**2. Why is the Java build *not* in the Dockerfile?**
It is a deliberate trade-off, and the honest answer is "for local iteration speed". The Maven
reactor builds all 16 modules once, and the images just package the jars — much faster than 16
containers each resolving dependencies. The cost is a build step people forget, which fails as
`COPY ... target/*.jar: no source files`. For CI or a shared environment I would make it multi-stage
with a cached Maven layer, so the image is reproducible from source alone.

**3. Why is the build context the repo root?**
Because a service jar and the shared libraries live in sibling directories; a context scoped to one
service could not see them. That is why every Dockerfile path is repo-relative.

**4. How do you keep image builds fast?**
Layer ordering: dependency manifests copied and installed before source, so an ordinary code edit
reuses the `npm ci` layer. Source files are copied individually, not as a whole directory, so the
host's `node_modules` and `dist` never overwrite what the build stage produced.

**5. Build args vs environment variables?**
Anything Vite inlines must be a **build arg**, because the value is baked into the bundle at build
time; setting it at runtime does nothing because nginx serves a finished file. Backend services are
the opposite: they read environment at startup, so their configuration is `environment:`, and the
same image runs in any environment.

---

## B. Compose and orchestration

**6. How is startup ordered?**
`depends_on` with `condition: service_healthy` where it matters: config-server first with a health
check on `/actuator/health`, then service-registry, then the gateway and all business services. Each
service also declares its infrastructure dependencies.

**7. `depends_on` does not wait for readiness — how do you deal with that?**
That is exactly why the platform services have health checks: `service_healthy` waits for the check,
not just the process. MySQL is declared `service_started`, so a service can still boot while MySQL
is initialising and fail — the pragmatic answer is `docker compose restart <service>`; the correct
one is a readiness check plus retry/backoff in the client, which Spring's connection pool partly
does already.

**8. Why pin `name: rajkumar` in the Compose file?**
Without it Compose derives the project name from the containing folder, so moving the file orphans
every running container and named volume under the old name.

**9. What is `docker-compose.lean.yml` for?**
An overlay for laptops: `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=50 -Xss512k -XX:+UseSerialGC` plus
`mem_limit` per service. Each Spring JVM otherwise claims 25% of host RAM as max heap, and about ten
of those starve the machine. Used as
`docker compose -f docker-compose.yml -f docker-compose.lean.yml up -d`.

**10. How do containers find each other?**
By service name on the `civil-network` bridge: `mysql:3306`, `kafka:9092`, `api-gateway:8080`.
Docker's embedded DNS resolves them. From the host you use the published ports instead — MySQL is
`localhost:3309` here because 3306 was taken.

**11. Explain the Kafka listener configuration.**
Two listeners: `PLAINTEXT://kafka:9092` for containers and `PLAINTEXT_HOST://localhost:29093` for
the host. A broker advertises the address clients should use to reach it, so a single listener would
work for one side and break the other.

**12. Where does data live, and what does `down -v` do?**
Named volumes: `mysql_data`, `redis_data`, `kafka_data`, `elasticsearch_data`, and so on. `down`
removes containers but keeps volumes; `down -v` deletes them, which wipes every database — after
that MySQL re-runs `database/init/*.sql` on the next start.

**13. How are the databases created in the first place?**
`docker/database/init/01-create-databases.sql` is mounted into
`/docker-entrypoint-initdb.d`, which MySQL runs **only on first initialisation** of an empty data
directory. It creates each service's database and the `civil_user` account. Editing it later does
nothing unless the volume is removed — a classic trap.

---

## C. Configuration and secrets

**14. How is configuration layered?**
Three levels: `docker/.env` holds host ports, credentials and provider switches for Compose;
Compose passes selected values into containers as environment; Config Server serves the per-service
YAML. Environment beats config file, so an operator can override without a rebuild.

**15. Are secrets handled properly?**
No, and I would say so directly. `docker/.env` is a plaintext file holding the JWT secret, Razorpay
keys and the Gemini key. It is gitignored and never committed, which is the minimum, but production
needs a real secret manager — Docker/Swarm secrets, Vault, or the cloud provider's — injected at
runtime and rotatable without a rebuild.

**16. How do you switch a channel off locally?**
`EMAIL_PROVIDER`, `SMS_PROVIDER`, `WHATSAPP_PROVIDER` accept `log`, which writes the message to the
service log instead of sending it. Placeholder credentials are also detected and fall back to
logging, so a half-configured environment never breaks OTP login — otherwise nobody could log in
locally.

---

## D. Operations and production readiness

**17. How do you debug a failing service?**
`docker compose ps` for state, `docker compose logs -f <service>` for the cause, `/actuator/health`
for readiness, Eureka at 8761 to confirm registration, `docker exec -it civil_mysql mysql ...` for
data. A gateway 503 usually means the instance has not registered yet.

**18. What is missing before this is production-ready?**
Honestly quite a lot, and naming it is the point: no orchestrator (Compose is single-host, no
rolling deploys or self-healing across nodes), no CI/CD to a real environment, no secret manager,
single MySQL with no replica or backup story, Elasticsearch and Kafka single-node, no TLS
termination configured (the nginx edge container has empty `sites/`), no resource requests/limits
beyond the lean overlay, and no alerting on top of Prometheus.

**19. How would you move this to Kubernetes?**
Each service becomes a Deployment plus Service; Eureka and the Config Server can be replaced by
Kubernetes DNS and ConfigMaps/Secrets, or kept for portability; the gateway becomes an Ingress or
stays as a gateway Deployment; MySQL, Kafka and Elasticsearch move to managed services or operators;
health checks become liveness and readiness probes; the lean overlay's caps become resource
requests and limits. I would keep Flyway running at startup but gate it behind a migration Job to
avoid 16 pods racing on the same schema.

**20. How do you observe the system?**
Actuator endpoints scraped by Prometheus, dashboards in Grafana, distributed tracing in Zipkin, and
per-container logs. What is missing is aggregation — logs live in each container, so I would add a
log shipper and an alert rule set, starting with payment failures and consumer lag.

**21. How long does a cold start take, and why?**
Several minutes on first run: MySQL initialises, images build, then 16 JVMs start, each fetching
config, running Flyway for every tenant schema and registering with Eureka. Subsequent starts are
much faster. If that mattered, the levers are native images (GraalVM), fewer services per host, or
starting only the subset a task needs — which is what the documented "start only these services"
workflow does.
