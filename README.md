# 🏗️ Civil Engineering Marketplace

[![Java](https://img.shields.io/badge/Java-21-red)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2-green)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-blue)](https://react.dev/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-Proprietary-orange)](LICENSE)

**India's #1 Civil Engineering Service Marketplace Platform** — an enterprise-grade, multi-tenant platform connecting customers with civil engineering professionals, contractors, surveyors, architects, and construction services.

> Like Uber/Rapido for finding professionals + Urban Company for booking services + Swiggy/Zomato for tracking orders — all built specifically for the civil engineering industry.

---

## 📋 Table of Contents

- [Architecture](#-architecture)
- [Tech Stack](#-tech-stack)
- [Microservices](#-microservices)
- [Getting Started](#-getting-started)
- [Development](#-development)
- [API Documentation](#-api-documentation)
- [Deployment](#-deployment)
- [Project Structure](#-project-structure)
- [Contributing](#-contributing)

---

## 🏛️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Nginx (Reverse Proxy)                │
├─────────────────────────────────────────────────────────────┤
│                    React SPA (Frontend)                     │
├─────────────────────────────────────────────────────────────┤
│              Spring Cloud API Gateway (Port 8080)           │
├─────────────────────────────────────────────────────────────┤
│   Auth    │   User   │  Booking  │  Payment  │ Notification │
│  Service  │ Service  │  Service  │  Service  │   Service    │
│  :8081    │  :8082   │  :8083    │  :8084    │   :8085      │
├─────────────────────────────────────────────────────────────┤
│              Service Registry (Eureka :8761)                │
│              Config Server (Spring Cloud :8888)             │
├─────────────────────────────────────────────────────────────┤
│  MySQL  │  Redis  │  Kafka  │  RabbitMQ  │  Elasticsearch  │
├─────────────────────────────────────────────────────────────┤
│                  Prometheus │ Grafana │ Zipkin               │
└─────────────────────────────────────────────────────────────┘
```

### Key Design Decisions

- **Microservices Architecture**: Each service is independently deployable, scalable, and has its own database
- **API Gateway**: Single entry point with JWT validation, rate limiting, and circuit breaker
- **Event-Driven**: Kafka for async communication between services (payment events, notifications)
- **Centralized Config**: Spring Cloud Config Server for managing all service configurations
- **Distributed Tracing**: Zipkin for request tracing across services
- **Monitoring**: Prometheus + Grafana for metrics and dashboards

---

## 🛠️ Tech Stack

### Backend
| Technology | Purpose |
|---|---|
| Java 21 | Runtime |
| Spring Boot 3.2 | Application framework |
| Spring Cloud | Microservices (Gateway, Config, Eureka) |
| Spring Security + JWT | Authentication & Authorization |
| Spring Data JPA + Hibernate | ORM & database access |
| MySQL 8.0 | Primary database |
| Redis 7 | Caching, OTP storage, sessions |
| Apache Kafka | Event streaming |
| RabbitMQ | Message queuing |
| Elasticsearch | Full-text search |
| Resilience4J | Circuit breaker, retry, rate limiter |
| Flyway | Database migrations |
| MapStruct | Object mapping |
| Swagger/OpenAPI | API documentation |
| Razorpay | Payment gateway |

### Frontend
| Technology | Purpose |
|---|---|
| React 18 | UI framework |
| TypeScript | Type safety |
| Vite | Build tool |
| Material UI (MUI) | Component library |
| Tailwind CSS | Utility CSS framework |
| Redux Toolkit | State management |
| React Query (TanStack) | Server state & caching |
| React Router 6 | Routing |
| Framer Motion | Animations |
| React Hook Form + Yup | Form validation |
| Axios | HTTP client |
| PWA | Progressive Web App support |

### DevOps
| Technology | Purpose |
|---|---|
| Docker | Containerization |
| Docker Compose | Local orchestration |
| Nginx | Reverse proxy |
| Prometheus | Metrics collection |
| Grafana | Dashboards & visualization |
| Zipkin | Distributed tracing |

---

## 🧩 Microservices

| Service | Port | Description |
|---|---|---|
| **Config Server** | 8888 | Centralized configuration management |
| **Service Registry** | 8761 | Eureka service discovery |
| **API Gateway** | 8080 | Single entry point with JWT auth, rate limiting |
| **Auth Service** | 8081 | Registration, login, OTP, OAuth2, JWT management |
| **User Service** | 8082 | Profile management, addresses, worker portfolios |
| **Booking Service** | 8083 | Booking lifecycle, quotations, milestones |
| **Payment Service** | 8084 | Razorpay integration, wallets, payouts |
| **Notification Service** | 8085 | Email, SMS, push notifications |

### Planned Services (Roadmap)
Admin Service, Chat Service, Location Service, Search Service, Review Service, Recommendation Service, Analytics Service, Invoice Service, SMS Service, CMS Service, Support Service

---

## 🚀 Getting Started

### Prerequisites
- Java 21 (JDK)
- Node.js 18+
- Docker & Docker Compose
- Maven 3.9+

### Quick Start (Docker)

```bash
# 1. Clone the repository
git clone https://github.com/your-org/civil-engineering-marketplace.git
cd civil-engineering-marketplace

# 2. Copy environment configuration
cp .env.example .env
# Edit .env with your credentials

# 3. Start all services
docker-compose up -d

# 4. Access the application
# Frontend: http://localhost:3000
# API Gateway: http://localhost:8080
# Eureka Dashboard: http://localhost:8761
# Grafana: http://localhost:3001 (admin/admin)
```

### Local Development

#### Backend
```bash
# Build all services
mvn clean install -DskipTests

# Start infrastructure
docker-compose up -d mysql redis kafka

# Start services individually (each in a separate terminal)
cd auth-service && mvn spring-boot:run
cd user-service && mvn spring-boot:run
cd booking-service && mvn spring-boot:run
cd payment-service && mvn spring-boot:run
cd notification-service && mvn spring-boot:run
```

#### Frontend
```bash
cd frontend
npm install
npm run dev
```

---

## 🔧 Environment Variables

Key environment variables (see `.env.example` for full list):

| Variable | Description |
|---|---|
| `JWT_SECRET` | Base64-encoded JWT signing key (min 256 bits) |
| `RAZORPAY_KEY_ID` | Razorpay API key |
| `RAZORPAY_KEY_SECRET` | Razorpay API secret |
| `SMTP_USERNAME` | Email server username |
| `SMTP_PASSWORD` | Email server password |
| `MYSQL_ROOT_PASSWORD` | MySQL root password |

---

## 📚 API Documentation

Once running, API documentation is available via Swagger UI:

| Service | URL |
|---|---|
| Auth Service | http://localhost:8081/swagger-ui.html |
| User Service | http://localhost:8082/swagger-ui.html |
| Booking Service | http://localhost:8083/swagger-ui.html |
| Payment Service | http://localhost:8084/swagger-ui.html |
| Notification Service | http://localhost:8085/swagger-ui.html |

### Auth Endpoints

```
POST /api/v1/auth/register    - Register new user
POST /api/v1/auth/login        - Login with email/password
POST /api/v1/auth/otp/send     - Send OTP
POST /api/v1/auth/otp/verify   - Verify OTP & login
POST /api/v1/auth/refresh      - Refresh access token
POST /api/v1/auth/logout       - Logout
```

### Booking Endpoints
```
POST   /api/v1/bookings                - Create booking
GET    /api/v1/bookings/{id}           - Get booking by ID
GET    /api/v1/bookings/customer       - Get customer bookings (paginated)
GET    /api/v1/bookings/worker         - Get worker bookings (paginated)
POST   /api/v1/bookings/{id}/assign/{workerId} - Assign worker
PUT    /api/v1/bookings/{id}/status/{status}    - Update status
POST   /api/v1/bookings/{id}/cancel    - Cancel booking
POST   /api/v1/bookings/{id}/complete  - Complete booking
```

### Payment Endpoints
```
POST /api/v1/payments/create-order   - Create Razorpay order
POST /api/v1/payments/verify         - Verify payment
POST /api/v1/payments/{id}/refund    - Process refund
POST /api/v1/payments/webhook        - Razorpay webhook handler
```

---

## 📁 Project Structure

```
civil-engineering-marketplace/
├── config-server/           # Spring Cloud Config (port 8888)
├── service-registry/        # Eureka Discovery (port 8761)
├── api-gateway/            # Spring Cloud Gateway (port 8080)
├── auth-service/           # Auth & JWT (port 8081)
├── user-service/           # User management (port 8082)
├── booking-service/        # Booking & orders (port 8083)
├── payment-service/        # Payments & Razorpay (port 8084)
├── notification-service/   # Email, SMS, Push (port 8085)
├── database/
│   └── migrations/         # Flyway SQL migrations
├── frontend/               # React SPA
│   ├── src/
│   │   ├── components/     # Reusable components
│   │   ├── pages/          # Page components
│   │   ├── layouts/        # Layout components
│   │   ├── store/          # Redux store & slices
│   │   ├── services/       # API & external services
│   │   ├── hooks/          # Custom hooks
│   │   └── styles/         # Global styles
│   └── ...
├── monitoring/             # Prometheus & Grafana config
├── nginx/                  # Nginx configuration
├── docker-compose.yml      # Service orchestration
├── pom.xml                 # Parent Maven POM
└── README.md
```

---

## 🧪 Testing

```bash
# Run all unit tests
mvn test

# Run integration tests
mvn verify -Pintegration

# Run frontend tests
cd frontend && npm test
```

---

## 📊 Monitoring

- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3001 (admin/admin)
- **Zipkin**: http://localhost:9411
- **Eureka**: http://localhost:8761

---

## 🛡️ Security

- JWT-based authentication with refresh tokens
- Role-based access control (RBAC) for all endpoints
- OTP verification for phone/email
- Account lockout after failed login attempts
- Rate limiting on API Gateway
- Circuit breaker for fault tolerance
- Encrypted sensitive data
- SQL injection protection via JPA/Hibernate
- CORS configuration for frontend domains

---

## 👥 User Roles

- **Super Admin** — Full system access
- **Admin** — Platform administration
- **City Manager** — City-level operations
- **Customer** — Book services
- **Worker** — Service provider (engineer, architect, plumber, etc.)
- **Contractor** — Team management, project allocation
- **Material Supplier** — Supply construction materials
- **Equipment Rental** — Provide construction equipment

---

## 🔮 Roadmap

- [ ] Admin Panel & Dashboard
- [ ] Worker/Contractor Portal
- [ ] Real-time Chat (Socket.IO)
- [ ] Live GPS Tracking
- [ ] AI-Powered Cost Estimation
- [ ] Mobile Apps (React Native)
- [ ] Elasticsearch Integration
- [ ] WhatsApp Notifications
- [ ] Multi-language Support
- [ ] Advanced Analytics & Reporting

---

## 📄 License

Proprietary. All rights reserved.

---

## 🤝 Support

- Email: support@civilengineer.com
- Documentation: [docs.civilengineer.com](https://docs.civilengineer.com)
- Issues: [GitHub Issues](https://github.com/your-org/civil-engineering-marketplace/issues)
