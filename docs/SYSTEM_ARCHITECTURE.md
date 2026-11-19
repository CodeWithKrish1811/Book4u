# Book4u — System Architecture Document

## 1. Architectural Philosophy

Book4u is built on a clean, decoupled, layered architecture prioritizing **transparency, testability, predictable performance, and strict separation of concerns**.

Unlike conventional enterprise systems that obscure SQL through Object-Relational Mapping (ORM) frameworks like Hibernate or JPA, Book4u intentionally utilizes **Spring JDBC** (`JdbcTemplate` / parameterized SQL). This design decision provides:

1. **Explicit Query Control:** Direct, hand-tuned, index-optimized SQL queries without hidden N+1 queries.
2. **Pedagogical Clarity:** Demonstrates relational database concepts, PreparedStatements, explicit RowMappers, and transaction boundaries.
3. **Low Memory Footprint:** Eliminates first-level/second-level entity caches, dirty-checking overhead, and proxy session leaks.
4. **Resilient Data Contracts:** Clear boundaries between relational tables and API response DTOs.

---

## 2. Layered Architecture Overview

```mermaid
flowchart TD
    subgraph Client ["Frontend (React + Vite)"]
        UI["React Components & Pages"]
        State["React Context & Hooks"]
        APIClient["Axios API Services"]
    end

    subgraph Network ["HTTP / REST Layer"]
        CORS["CORS Filter / Security"]
        Gateway["REST Endpoints (/api/*)"]
    end

    subgraph Server ["Backend (Spring Boot 3.x / Java 21)"]
        Controllers["Controllers (@RestController)"]
        DTOs["DTO Validation & Mapping"]
        Services["Service Layer (Business Logic & Transactions)"]
        Repositories["Repository Layer (Spring JdbcTemplate)"]
    end

    subgraph Storage ["Database (MySQL 8.x)"]
        Pool["HikariCP Connection Pool"]
        MySQL[("MySQL 8.x Relational Engine")]
    end

    UI --> State
    State --> APIClient
    APIClient -->|JSON over HTTP| CORS
    CORS --> Gateway
    Gateway --> Controllers
    Controllers --> DTOs
    Controllers --> Services
    Services --> Repositories
    Repositories --> Pool
    Pool --> MySQL
```

---

## 3. Component Breakdown & Responsibilities

### 3.1 Client Layer (`frontend/`)
- **Technology:** React 18+, Vite, React Router, Axios, CSS Modules / modern CSS.
- **Role:** Pure client-side presentation and interaction. Communicates with the backend exclusively via RESTful JSON payloads.
- **Key Tenet:** Never stores or expects backend secrets. Uses `VITE_API_BASE_URL` to route requests.

### 3.2 Presentation / Controller Layer (`com.book4u.controller`)
- **Role:** Exposes RESTful endpoints, handles HTTP routing, and consumes/produces JSON.
- **Invariants:**
  - Controllers **never** interact with the database directly.
  - Controllers **never** contain business rules.
  - All incoming payload validation occurs via `@Valid` and Bean Validation annotations.

### 3.3 Service Layer (`com.book4u.service`)
- **Role:** Implements domain rules, orchestrates transactions, enforces invariants, and coordinates cross-repository operations.
- **Transaction Management:** Uses Spring's declarative `@Transactional` at service boundaries to ensure atomicity across JDBC statements.

### 3.4 Data Access / Repository Layer (`com.book4u.repository`)
- **Role:** Encapsulates all relational database operations.
- **Implementation:**
  - Employs Spring's `JdbcTemplate` and `NamedParameterJdbcTemplate`.
  - All statements use parameterized SQL to prevent SQL Injection (`PreparedStatement`).
  - Implements custom `RowMapper<T>` classes to map SQL `ResultSet` to clean domain models.

### 3.5 Transfer Objects & Exception Handling (`com.book4u.dto` & `com.book4u.exception`)
- **DTOs:** Separate request and response payloads from internal storage models to prevent over-posting and leakage of internal database structures.
- **Global Exception Handling:** `@RestControllerAdvice` centralizes error response construction, mapping domain exceptions to HTTP 400, 404, 409, and 500 status codes with clean JSON envelopes.

---

## 4. Security & Environment Architecture

1. **Environment Decoupling:** Database credentials, secrets, and environment-specific endpoints are injected at runtime via system environment variables or `.env` configurations.
2. **CORS Governance:** Strict origin whitelisting configured via `CorsConfig` allowing local Vite development while blocking untrusted origins.
3. **No Credential Exposure:** Neither database connection strings nor backend credentials are ever bundled or exposed in client bundles.
