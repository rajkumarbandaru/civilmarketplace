# 07 — Flowcharts

These diagrams are Mermaid. They render on GitHub and in the VS Code Markdown preview
(install the "Markdown Preview Mermaid Support" extension if they show as code).
A built `flowcharts.html` sits next to this file (`python3 tools/build_html.py` regenerates it).

## 1. System architecture

```mermaid
flowchart TB
    U["User / Browser"]

    subgraph EDGE["Edge"]
        FE["Frontend nginx :3000<br/>serves React bundle<br/>proxies /api to gateway"]
        VITE["Vite dev server :5173<br/>(dev mode only)"]
    end

    subgraph GW["API Gateway :8080"]
        T1["Tenant resolution<br/>Host to X-Tenant-Id"]
        T2["Internal-path block"]
        T3["JWT check<br/>adds X-User-* headers"]
        T4["Route by path<br/>lb://service via Eureka"]
        T1 --> T2 --> T3 --> T4
    end

    subgraph PLAT["Platform"]
        CFG["Config Server :8888"]
        EUR["Eureka Registry :8761"]
    end

    subgraph SVC["Business microservices"]
        AUTH["auth"]
        USER["user"]
        BOOK["booking"]
        PAY["payment"]
        NOTI["notification"]
        ADM["admin"]
        PROJ["project"]
        REV["review"]
        SRCH["search"]
        MSG["messaging"]
        SUP["support"]
        AUD["audit"]
        TEN["tenant"]
    end

    subgraph INFRA["Data and messaging"]
        MYSQL[("MySQL")]
        REDIS[("Redis")]
        KAFKA{{"Kafka"}}
        RABBIT{{"RabbitMQ"}}
        ES[("Elasticsearch")]
    end

    subgraph OBS["Observability"]
        ZIP["Zipkin"]
        PROM["Prometheus"]
        GRAF["Grafana"]
    end

    U --> FE
    U -.-> VITE
    FE -->|"/api/**"| T1
    VITE -.->|"/api/**"| T1
    T4 --> SVC
    SVC -->|"fetch config at boot"| CFG
    SVC -->|"register"| EUR
    T4 -->|"lookup"| EUR
    SVC --> MYSQL
    SVC --> KAFKA
    AUTH --> REDIS
    GW --> REDIS
    NOTI --> RABBIT
    SRCH --> ES
    SVC -.-> ZIP
    PROM -.->|"scrape /actuator"| SVC
    GRAF --> PROM
```

## 2. Docker startup order

```mermaid
flowchart TB
    START(["docker compose up -d --build"]) --> INF

    subgraph INF["Step 1: infrastructure (parallel)"]
        direction LR
        M[("mysql")]
        R[("redis")]
        Z["zookeeper"] --> K{{"kafka"}}
        RB{{"rabbitmq"}}
        E[("elasticsearch")]
        P["prometheus"] --> G["grafana"]
        ZK["zipkin"]
    end

    INF --> C["Step 2: config-server"]
    C --> CH{"config-server<br/>healthy?"}
    CH -- no --> C
    CH -- yes --> SR["Step 3: service-registry (Eureka)"]
    SR --> SRH{"Eureka<br/>healthy?"}
    SRH -- no --> SR
    SRH -- yes --> APPS

    subgraph APPS["Step 4: gateway + services (parallel)"]
        direction LR
        A1["api-gateway"]
        A2["auth / user / booking"]
        A3["payment / notification / admin"]
        A4["project / review / audit"]
        A5["messaging / support / search / tenant"]
    end

    APPS --> FEC["Step 5: frontend (nginx :3000)"]
    FEC --> NG["Step 6: nginx edge :8010"]
    NG --> READY(["Open http://localhost:3000"])
```

## 3. What happens when one service boots

```mermaid
flowchart TB
    S(["java -jar app.jar"]) --> C1["Contact config-server<br/>load service.yml"]
    C1 --> C1Q{"Config<br/>reachable?"}
    C1Q -- no --> FAIL1["Exit / retry<br/>fix: docker compose restart svc"]
    C1Q -- yes --> DB["Connect MySQL"]
    DB --> DBQ{"MySQL<br/>ready?"}
    DBQ -- no --> FAIL2["Startup error<br/>fix: restart svc after MySQL init"]
    DBQ -- yes --> TEN["tenant-common reads tenant registry"]
    TEN --> FLY["Run Flyway migrations<br/>for every tenant schema"]
    FLY --> CONN["Connect Kafka / Redis /<br/>RabbitMQ / Elasticsearch as needed"]
    CONN --> REG["Register in Eureka"]
    REG --> UP(["/actuator/health = UP<br/>gateway can route here"])
```

## 4. Request through the API gateway (decision flow)

```mermaid
flowchart TB
    IN(["Request: /api/v1/..."]) --> H["Read Host header<br/>e.g. acme.localhost:3000"]
    H --> TQ{"Known tenant<br/>subdomain?"}
    TQ -- "no / plain localhost" --> FB["Use fallback tenant"]
    TQ -- yes --> TK["Tenant = acme"]
    FB --> STRIP
    TK --> STRIP["Remove client X-Tenant-Id<br/>set real X-Tenant-Id"]
    STRIP --> IQ{"Internal-only path?<br/>e.g. /api/v1/bookings/admin"}
    IQ -- yes --> X403A["Blocked (403/404)"]
    IQ -- no --> RQ{"Which route<br/>matches path?"}
    RQ -- "none" --> X404["404"]
    RQ -- "public route<br/>auth, geo, catalogue,<br/>content, webhooks,<br/>tenant-resolution" --> FWD
    RQ -- "protected route" --> JQ{"Bearer token present<br/>and signature valid<br/>and not expired?"}
    JQ -- no --> X401["401 Unauthorized"]
    JQ -- yes --> CQ{"JWT tenant claim<br/>equals resolved tenant?"}
    CQ -- no --> X403B["403 cross-tenant token"]
    CQ -- yes --> ADD["Add X-User-Id, X-User-Email,<br/>X-User-Role, X-User-Name"]
    ADD --> RL{"Rate limit OK?<br/>(Redis)"}
    RL -- no --> X429["429 Too Many Requests"]
    RL -- yes --> FWD["Eureka lookup lb://service"]
    FWD --> SQ{"Service<br/>registered?"}
    SQ -- no --> X503["503 / fallback"]
    SQ -- yes --> SVC(["Service handles request<br/>in tenant schema"])
```

## 5. Login (sequence)

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant UI as React UI
    participant NX as Frontend nginx :3000
    participant GW as API Gateway :8080
    participant EU as Eureka
    participant AU as auth-service
    participant DB as MySQL (auth_tenant schema)
    participant AD as admin-service

    User->>UI: Enter email + password
    UI->>NX: POST /api/v1/auth/login
    NX->>GW: proxy (Host header kept)
    GW->>GW: Resolve tenant from Host, set X-Tenant-Id
    GW->>EU: Where is auth-service?
    EU-->>GW: auth-service:8081
    GW->>AU: forward (public route, no JWT check)
    AU->>DB: Load user in tenant schema
    DB-->>AU: user row
    AU->>AU: Check password, lockout
    AU-->>GW: accessToken (JWT) + refreshToken
    GW-->>NX: 200 tokens
    NX-->>UI: 200 tokens
    UI->>UI: Save token in Redux store
    UI->>NX: GET /api/v1/ui-config/me (Bearer JWT)
    NX->>GW: proxy
    GW->>GW: Validate JWT, add X-User-* headers
    GW->>AD: forward
    AD-->>UI: theme + UI config
    UI-->>User: Dashboard (themed)
```

## 6. OTP login (sequence)

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant UI as React UI
    participant GW as API Gateway
    participant AU as auth-service
    participant RD as Redis
    participant KF as Kafka
    participant NO as notification-service

    User->>UI: Enter phone / email
    UI->>GW: POST /api/v1/auth/otp/send
    GW->>AU: forward
    AU->>RD: Store OTP with expiry
    AU->>KF: publish OTP event
    KF->>NO: consume
    alt provider = log
        NO->>NO: Write OTP to service log
    else real provider
        NO-->>User: Email / SMS / WhatsApp
    end
    User->>UI: Enter OTP
    UI->>GW: POST /api/v1/auth/otp/verify
    GW->>AU: forward
    AU->>RD: Compare OTP
    AU-->>UI: JWT tokens
```

## 7. Authenticated call: create a booking (sequence)

```mermaid
sequenceDiagram
    autonumber
    participant UI as React UI
    participant GW as API Gateway
    participant BK as booking-service
    participant DB as MySQL (bookings_tenant)
    participant KF as Kafka
    participant NO as notification-service

    UI->>GW: POST /api/v1/bookings (Bearer JWT)
    GW->>GW: tenant + JWT check, add X-User-* / X-Tenant-Id
    GW->>BK: forward via Eureka
    BK->>BK: Read X-User-Id header (no JWT re-check)
    BK->>DB: INSERT booking (tenant schema)
    BK->>KF: publish booking event
    BK-->>GW: 201 booking
    GW-->>UI: 201 booking
    KF->>NO: consume event
    NO->>NO: Create in-app notification
```

## 8. Kafka event flows

```mermaid
flowchart LR
    subgraph PROD["Producers"]
        AU["auth-service"]
        BK["booking-service"]
        MS["messaging-service"]
        TS["tenant-service"]
        AP["audit-common inside:<br/>user, payment, project,<br/>notification, support, admin"]
    end

    subgraph TOP["Kafka topics"]
        T1{{"OTP / auth events"}}
        T2{{"booking events"}}
        T3{{"message.sent"}}
        T4{{"tenant.events"}}
        T5{{"audit events"}}
    end

    subgraph CONS["Consumers"]
        NO["notification-service<br/>email / SMS / WhatsApp / in-app"]
        AD["audit-service<br/>hash-chained log"]
        ALL["every tenanted service<br/>create schema + Flyway"]
    end

    AU --> T1 --> NO
    BK --> T2 --> NO
    MS --> T3 --> NO
    TS --> T4 --> ALL
    AP --> T5 --> AD
```

## 9. Service-to-service call (review needs a completed booking)

```mermaid
flowchart TB
    A(["POST /api/v1/bookings/42/reviews"]) --> GW["Gateway route booking-reviews<br/>JWT check"]
    GW --> RV["review-service"]
    RV --> F["Feign call lb://booking-service<br/>get booking 42"]
    F --> Q1{"Booking<br/>COMPLETED?"}
    Q1 -- no --> E1["400 not allowed yet"]
    Q1 -- yes --> Q2{"Caller is customer<br/>or worker of it?"}
    Q2 -- no --> E2["403 not a party"]
    Q2 -- yes --> Q3{"Already reviewed<br/>by this user?"}
    Q3 -- yes --> E3["400 duplicate"]
    Q3 -- no --> SAVE["Save review"]
    SAVE --> AVG["Recompute RatingSummary average"]
    AVG --> OK(["201 Created"])
```

## 10. Escrow payment states

```mermaid
stateDiagram-v2
    [*] --> PENDING_FUNDING: POST /api/v1/escrow
    PENDING_FUNDING --> HELD: Razorpay payment COMPLETED
    PENDING_FUNDING --> CANCELLED: cancel
    HELD --> RELEASED: payer releases
    HELD --> RELEASED: auto-release after 7 days
    HELD --> REFUNDED: refund
    HELD --> DISPUTED: payer or payee disputes
    DISPUTED --> RELEASED: admin resolves RELEASE
    DISPUTED --> REFUNDED: admin resolves REFUND
    DISPUTED --> HELD: admin resolves HOLD
    RELEASED --> [*]: 5% commission, payee wallet credited
    REFUNDED --> [*]
    CANCELLED --> [*]
```

## 11. Support ticket states

```mermaid
stateDiagram-v2
    [*] --> OPEN: reporter creates ticket
    OPEN --> IN_PROGRESS: admin assigns
    IN_PROGRESS --> RESOLVED: admin resolves
    IN_PROGRESS --> CLOSED: admin closes
    OPEN --> CLOSED: admin closes
    RESOLVED --> [*]: no new replies
    CLOSED --> [*]: no new replies
```

## 12. Multi-tenancy data path

```mermaid
flowchart LR
    B1["acme.localhost:3000"] --> N["Frontend nginx<br/>Host kept"]
    B2["hostelfee.localhost:3000"] --> N
    N --> G["Gateway<br/>X-Tenant-Id = acme / hostelfee"]
    G --> S["Any service<br/>tenant-common"]
    S -->|"acme"| D1[("civil_engineer_bookings_acme")]
    S -->|"hostelfee"| D2[("civil_engineer_bookings_hostelfee")]
```

## 13. UI: dev mode vs Docker mode

```mermaid
flowchart TB
    subgraph DEV["Dev mode: npm run dev"]
        D1["Browser :5173"] --> D2["Vite dev server<br/>hot reload"]
        D2 -->|"proxy /api, /ws"| D3["localhost:8080 gateway"]
    end
    subgraph DOCK["Docker mode"]
        K1["docker compose build frontend"] --> K2["Stage 1 node:20<br/>npm ci, npm run build"]
        K2 --> K3["Stage 2 nginx<br/>copy dist/"]
        K3 --> K4["Browser :3000"]
        K4 -->|"/api/ proxy"| K5["api-gateway:8080<br/>(inside Docker network)"]
    end
```

## 14. How to start: which option?

```mermaid
flowchart TB
    Q0(["What are you working on?"]) --> Q1{"Just run / demo<br/>the whole app?"}
    Q1 -- yes --> O1["Option 1: all in Docker"]
    Q1 -- no --> Q2{"Changing UI code?"}
    Q2 -- yes --> O2["Option 2: backend in Docker<br/>+ npm run dev"]
    Q2 -- no --> Q3{"Debugging one<br/>Java service?"}
    Q3 -- yes --> O3["Option 3: stop that container<br/>mvn spring-boot:run locally"]
    Q3 -- no --> O1

    O1 --> B1["mvn clean package -DskipTests<br/>(JDK 21)"]
    B1 --> B2["docker compose -f docker-compose.yml<br/>-f docker-compose.lean.yml up -d --build"]
    B2 --> B3["docker compose ps<br/>check Eureka :8761"]
    B3 --> B4(["http://localhost:3000<br/>superadmin@civileng.test"])

    O2 --> C1["docker compose up -d<br/>infra + backend services"]
    C1 --> C2["cd frontend; npm install; npm run dev"]
    C2 --> C3(["http://localhost:5173"])

    O3 --> E1["docker compose stop svc"]
    E1 --> E2["mvn -pl svc -am spring-boot:run<br/>use host ports 8889, 8761, 3309, 29093"]
```

## 15. Code change: what to rebuild

```mermaid
flowchart TB
    CH(["I changed..."]) --> Q{"What?"}
    Q -- "Java in one service" --> J1["mvn -pl svc -am package -DskipTests"] --> J2["docker compose up -d --build svc"]
    Q -- "shared lib<br/>audit/tenant/web-common" --> L1["mvn -pl svc1,svc2 -am package"] --> L2["docker compose up -d --build svc1 svc2"]
    Q -- "config-repo YAML" --> Y1["docker compose up -d --build config-server"] --> Y2["docker compose restart affected svc"]
    Q -- "frontend code" --> F1{"Dev mode?"}
    F1 -- yes --> F2["Nothing: Vite hot reloads"]
    F1 -- no --> F3["docker compose up -d --build frontend"]
    Q -- "VITE_* variable" --> V1["Rebuild frontend image<br/>or restart npm run dev"]
    Q -- "docker/.env" --> E1["docker compose up -d<br/>(recreates changed containers)"]
    Q -- "Flyway migration" --> M1["Add new V_n file, never edit old"] --> J1
```
