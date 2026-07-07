# Core Banking & Transfer System

A modular core banking and money transfer system built with **Spring Boot 3.5.x** and **Java 21**, strictly adhering to **Hexagonal Architecture (Ports and Adapters)** and **Clean Architecture** principles.

---

## 🛠️ Tech Stack & Prerequisites
### 🚀 Core Framework & Language
- **Java 21:** Utilizes modern LTS features (e.g., Records, Pattern Matching).
- **Spring Boot 3.5.16:** Application backbone (Spring Web, AOP, Scheduling, Actuator).
- **Spring Security & JWT:** Stateless authentication and token blacklisting.

### 💾 Data & Caching
- **PostgreSQL 15:** Relational database with full transaction isolation and optimistic locking.
- **Flyway:** Automated database migration and schema version control. Migrations run on startup (`spring.jpa.hibernate.ddl-auto=validate`) to guarantee schema consistency with JPA entities.
- **Redis 7:** Caching, login attempts storage, and sliding window rate limiting (via Lua scripts).
- **Caffeine Cache:** In-memory caching fallback for development without Redis.

### 🧪 Quality Assurance & Testing
- **JUnit 5 & Mockito:** Standard test suites and mocking.
- **Testcontainers:** Integration testing with real PostgreSQL containers.
- **ArchUnit:** Architecture verification to enforce Hexagonal boundary rules.
- **JaCoCo:** Quality gates enforcing $\ge 85\%$ Line and $\ge 80\%$ Branch coverage.
- **Pitest:** Mutation testing to verify test assertion strength.

### 🐳 DevOps & Prerequisites
- **Docker & Docker Compose:** Multi-container orchestration.
- **Maven 3.9.x:** Recommended build system (Maven 3.9.16 wrapper configuration is pre-configured).
- **Prerequisites:** Java 21 JDK, Docker installed.

---

## 📐 Architecture & Module Dependency

The project follows Hexagonal Architecture rules where the domain core remains isolated from framework dependencies, and adapters depend strictly on ports.

### Module Breakdown:
- **`app`**: Bootstraps the application (`BankApplication`) and wires all modules together.
- **`common`**: Framework-independent shared domain models, value objects, exceptions, and annotations.
- **`infrastructure`**: Security, JWT validation, outbox poller/processor, and Redis/Caffeine adapters.
- **`user`**: User registration, authentication, token blacklisting, and brute-force lockout.
- **`account`**: Bank account lifecycle, balance mutations, and details.
- **`transfer`**: Fund transfers, cancellations (24-hour window), and reporting.
- **`audit`**: Asynchronous transaction logging listening to commit-phase domain events.

```mermaid
graph TD
    classDef appClass fill:#2B6CB0,stroke:#2B6CB0,color:#fff,stroke-width:2px;
    classDef domainClass fill:#2F855A,stroke:#2F855A,color:#fff,stroke-width:2px;
    classDef infraClass fill:#D69E2E,stroke:#D69E2E,color:#fff,stroke-width:2px;
    classDef commonClass fill:#4A5568,stroke:#4A5568,color:#fff,stroke-width:2px;

    app[app module]:::appClass
    transfer[transfer module]:::domainClass
    account[account module]:::domainClass
    audit[audit module]:::domainClass
    user[user module]:::domainClass
    infra[infrastructure module]:::infraClass
    common[common module]:::commonClass

    app --> transfer & account & audit & user & infra & common
    transfer --> infra & common
    account --> infra & common
    user --> infra & common
    audit --> infra & common
    infra --> common
```

---

## 🔌 REST API Endpoints (v1)

All request paths are prefixed with `/api/v1`. Endpoints below require a JWT bearer token except registration and login.

| Module | Endpoint | Method | Description | Special Headers / Notes |
| :--- | :--- | :--- | :--- | :--- |
| **User** | `/auth/register` | `POST` | Register a new user | |
| **User** | `/auth/login` | `POST` | Log in and obtain JWT | Brute-force & Rate-limited |
| **User** | `/auth/logout` | `POST` | Log out and blacklist token | `Authorization: Bearer <token>` |
| **Account** | `/accounts` | `POST` | Create a new bank account | `Authorization: Bearer <token>` |
| **Account** | `/accounts` | `GET` | List all accounts (Paged) | `Authorization: Bearer <token>` |
| **Account** | `/accounts/{id}` | `GET` | Query account details by ID | `Authorization: Bearer <token>` |
| **Account** | `/accounts/iban/{iban}` | `GET` | Query account details by IBAN | `Authorization: Bearer <token>` |
| **Transfer** | `/transfers` | `POST` | Execute a money transfer | `Idempotency-Key` (Required) |
| **Transfer** | `/transfers/{id}` | `GET` | Query transfer details by ID | `Authorization: Bearer <token>` |
| **Transfer** | `/transfers/{id}/cancel` | `POST` | Cancel a transfer (< 24 hours) | `Idempotency-Key` (Required) |
| **Transfer** | `/transfers/history/{accountId}`| `GET` | Fetch transfer history (Paged) | `Authorization: Bearer <token>` |
| **Transfer** | `/transfers/report` | `GET` | Export date-range report | `Authorization: Bearer <token>` |

---

## 🔑 Key Features & Design Decisions

- **AOP Programmatic Transactions (`UseCaseTransactionAspect`):** Isolates transaction management from business use cases. Auditing use cases utilize `PROPAGATION_REQUIRES_NEW` to guarantee logging persistence even on parent transaction rollbacks.
- **Transactional Outbox:** Reliably publishes domain events via the `outbox_events` database table. Each partition is polled independently on its own thread (`ScheduledExecutorService`, configurable `partitionCount`, default: 0). Uses `SELECT ... FOR UPDATE SKIP LOCKED` (via Hibernate `@QueryHint`) to prevent duplicate processing under concurrent polling.
- **Optimistic Concurrency Control (OCC):** Prevents lost updates and double-spending on `Account` and `Transfer` entities via Hibernate `@Version`.
- **Sorted Resource Locking (Deadlock Prevention):** Acquires database locks in a consistent, sorted order of account IDs (via `OrderedPair`) during debit/credit operations to prevent deadlocks under high-concurrency transfers.
- **Bounded Context Decoupling (Anti-Corruption Layer - ACL):** The `transfer` and `account` modules are strictly decoupled at compile-time. Inter-context communication is mediated by the `AccountAclPort` contract (placed in `common`) and implemented via `AccountAclAdapter` (in `account`), protecting the transfer domain from database or structure changes inside the account module.
- **AOP Idempotency Guard:** Protects write endpoints against duplicate submissions using a unique composite key (`username_idempotencyKey`) stored in the `idempotency_keys` table.
- **Dynamic Security Backends:** Abstracts Rate Limiting (sliding window via Lua script), Token Blacklisting, and Brute-Force lockout, supporting both Redis (production) and Caffeine (local dev).

---

## 🚀 Getting Started

You can run the entire system (databases, cache, and the application itself) with a single command, or build and run it locally.

### Option A: Run Everything via Docker Compose (Recommended)
To build and spin up PostgreSQL, Redis, and the Spring Boot application together:
```bash
docker-compose up --build
```

### Option B: Build & Run Locally
1. Start PostgreSQL and Redis containers:
   ```bash
   docker-compose up -d postgres redis
   ```
2. Compile and run the application using the Maven Wrapper:
   - **Linux/macOS:**
     ```bash
     ./mvnw clean package
     ./mvnw spring-boot:run -pl app
     ```
   - **Windows (PowerShell):**
     ```powershell
     .\mvnw.cmd clean package
     .\mvnw.cmd spring-boot:run -pl app
     ```
   *(Note: Testcontainers will spin up a PostgreSQL instance automatically during the test lifecycle)*

- **Swagger UI:** `http://localhost:8080/swagger-ui/index.html`
- **Actuator Health:** `http://localhost:8080/actuator/health`

---

## 🧪 Testing & Quality Gates

- **Verify Architecture Boundaries (ArchUnit):** Verified automatically via `ArchitectureTest.java`. This enforces:
  - **Hexagonal Architecture Guard:** Checks that domain layer does not import Spring or framework classes.
  - **Dependency Flow Validation:** Enforces that dependency always flows from adapters to ports, never the reverse.
  - **Cycle Prevention:** Guarantees no cyclic dependencies exist between Maven modules (e.g., compile-time decoupling of `transfer` and `account`).
- **Integration Testing with Testcontainers & Flyway:** Integration tests spin up real PostgreSQL instances via Testcontainers and apply Flyway schema migrations (`V1__init_schema.sql` in `common` module) inside the static initializer block before the Spring context boots. Both `@DataJpaTest`-slice (`AbstractIntegrationTest`) and full-context (`AbstractSpringBootIntegrationTest`) base classes use Flyway with `ddl-auto=validate` to guarantee schema consistency.
- **Generate Coverage Report (JaCoCo):** `./mvnw jacoco:report` (or `.\mvnw.cmd jacoco:report`)
- **Run Mutation Testing (Pitest):** `./mvnw pitest:mutationCoverage` (or `.\mvnw.cmd pitest:mutationCoverage`)
