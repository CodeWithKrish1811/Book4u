# Book4u

> **Your Books. Your Campus. Your Community.**

[![Project Status](https://img.shields.io/badge/status-active%20development-blue.svg)](#)
[![Java Version](https://img.shields.io/badge/Java-21-orange.svg)](#)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen.svg)](#)
[![Data Access](https://img.shields.io/badge/Data%20Access-Spring%20JDBC%20(No%20ORM)-green.svg)](#)
[![MySQL](https://img.shields.io/badge/MySQL-8.x-blue.svg)](#)
[![Frontend](https://img.shields.io/badge/Frontend-React%20%2B%20Vite-61dafb.svg)](#)
[![License](https://img.shields.io/badge/License-MIT-purple.svg)](#)

---

## 1. Project Introduction

**Book4u** is a university campus peer-to-peer book exchange platform designed to make academic textbooks accessible, affordable, and sustainable. University students spend substantial sums each semester purchasing textbooks that sit idle after final examinations.

Book4u connects students within the same university campus so they can exchange, give away, or temporarily lend textbooks directly to fellow students.

### Supported Book-Sharing Modes (Zero Commercial Buying/Selling)

In strict accordance with the initial platform design, **there is NO buying or selling functionality**. Book4u supports exactly three peer-to-peer sharing workflows:

1. 🔄 **SWAP** — Exchange one book for another. Student A trades a book they no longer need for a textbook required for their current semester.
2. 🎁 **GIVEAWAY** — Altruistic gifting. Senior or graduating students pass down foundational books to juniors with zero payment or reciprocal obligation.
3. ⏳ **LENDING** — Temporarily lend an academic reference book with an agreed return calendar date and duration.

---

## 2. Core Objectives

- **Reduce Student Financial Burden:** Eliminate repetitive textbook expenditures each academic semester.
- **Promote Environmental Sustainability:** Extend the physical lifecycle of print books across multiple student generations.
- **Campus Community & Lineage:** Foster trust through verified student identity, peer ratings, and the digital **Book Passport** tracking every book's journey through student hands.
- **Educational Architecture:** Demonstrate production-grade software engineering patterns and pure **Spring JDBC** principles (PreparedStatements, parameterized queries, and custom RowMappers) without the overhead or magic of Object-Relational Mapping (ORM) frameworks like Hibernate or JPA.

---

## 3. Technology Stack

### Frontend
- **Framework:** [React.js](https://react.dev/) (v18.x) initialized with [Vite](https://vitejs.dev/)
- **Language:** Modern JavaScript (ES Modules)
- **Routing:** [React Router](https://reactrouter.com/) (v6.x)
- **HTTP Client:** [Axios](https://axios-http.com/) with configured interceptors
- **Styling:** Modular CSS3 with responsive layouts and custom CSS design tokens
- **Icons:** [Lucide React](https://lucide.dev/)

### Backend
- **Language:** Java 21 (LTS)
- **Framework:** [Spring Boot 3.3.x](https://spring.io/projects/spring-boot)
- **Build System:** Apache Maven (includes Maven Wrapper `mvnw`)
- **Web Layer:** Spring Web (`spring-boot-starter-web`)
- **Data Access Layer:** **Spring JDBC** (`spring-boot-starter-jdbc` with `JdbcTemplate` & `NamedParameterJdbcTemplate`)
- **Validation:** Bean Validation (`spring-boot-starter-validation`)
- **Security:** Spring Security & JWT (structure established, configuration scheduled for Phase 3)
- **Connection Pool:** HikariCP

> **Architectural Note:** Hibernate, JPA, and Spring Data JPA are intentionally excluded. All persistence logic is written using parameterized SQL with `JdbcTemplate` to guarantee complete query control, zero N+1 surprises, and explicit demonstration of relational database and JDBC concepts.

### Database
- **Database Engine:** MySQL 8.x
- **Storage Engine:** InnoDB (ACID compliant, foreign keys)
- **Encoding:** `utf8mb4` with `utf8mb4_unicode_ci` collation
- **Driver:** MySQL Connector/J (`com.mysql:mysql-connector-j`)

---

## 4. Project Monorepo Structure

```
Book4u/
│
├── frontend/                          # React + Vite client application
│   ├── src/
│   │   ├── assets/                    # Static media, icons, and illustrations
│   │   ├── components/                # Reusable UI components (Navbar, Footer, HealthBadge)
│   │   ├── pages/                     # Routed pages (HomePage, NotFoundPage)
│   │   ├── layouts/                   # Page layouts (MainLayout)
│   │   ├── services/                  # Axios HTTP client & API services
│   │   ├── hooks/                     # Custom hooks (useHealthCheck)
│   │   ├── context/                   # React context providers (AppContext)
│   │   ├── utils/                     # Global constants and client helpers
│   │   ├── styles/                    # Global tokens & page styles (index.css, home.css)
│   │   ├── App.jsx                    # Application routing root
│   │   └── main.jsx                   # React DOM entry point
│   ├── index.html                     # HTML5 root template
│   ├── package.json                   # Frontend npm manifest
│   └── vite.config.js                 # Vite build & proxy configuration
│
├── backend/                           # Spring Boot 3.x Java 21 REST API
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/book4u/
│   │   │   │   ├── Book4uApplication.java   # Spring Boot entry point
│   │   │   │   ├── config/                  # CORS and DataSource configurations
│   │   │   │   ├── controller/              # REST controllers (HealthController)
│   │   │   │   ├── service/                 # Domain logic interfaces & implementations
│   │   │   │   ├── repository/              # Spring JDBC repositories (PreparedStatement)
│   │   │   │   ├── model/                   # Domain entities and status enums
│   │   │   │   ├── dto/                     # API request and response DTOs
│   │   │   │   ├── exception/               # GlobalExceptionHandler & custom exceptions
│   │   │   │   ├── security/                # Spring Security & JWT foundation
│   │   │   │   └── util/                    # DateTime and global constant utilities
│   │   │   └── resources/
│   │   │       └── application.properties   # Environment-driven application config
│   │   └── test/                            # Unit and integration test suite
│   ├── mvnw / mvnw.cmd                      # Cross-platform Maven wrapper
│   └── pom.xml                              # Maven build configuration
│
├── database/
│   └── schema.sql                     # Complete MySQL 8.x schema DDL & seed data
│
├── docs/                              # Formal architectural documentation
│   ├── PROJECT_OVERVIEW.md            # Vision, stakeholders, and sharing workflows
│   ├── SYSTEM_ARCHITECTURE.md         # N-tier architecture & data flow diagrams
│   ├── DATABASE_DESIGN.md             # ER diagrams, 3NF schema, and indexing strategy
│   └── DEVELOPMENT_ROADMAP.md         # Phase 1 through Phase 8 roadmap
│
├── .env.example                       # Template for environment-specific secrets
├── .gitignore                         # Comprehensive Git exclusion rules
└── README.md                          # Project documentation
```

---

## 5. Architectural Principles

1. **Separation of Concerns:**
   - Controllers handle HTTP routing, serialization, and input validation. They **never** contain SQL.
   - Business logic resides strictly in Service classes.
   - Database operations are encapsulated exclusively within Repository classes.
2. **Spring JDBC Parameterization:**
   - All queries use parameterized SQL (`PreparedStatement`) through `JdbcTemplate` to prevent SQL Injection.
3. **Data Transfer Objects (DTOs):**
   - API endpoints consume and produce dedicated DTOs to avoid leaking internal storage models.
4. **Zero Hardcoded Secrets:**
   - Database credentials, port numbers, and secrets are dynamically read from system environment variables or `.env` files.
5. **CORS Governance:**
   - Explicitly configured for local frontend development (`http://localhost:5173`, `http://localhost:3000`).

---

## 6. Local Setup Instructions

### Prerequisites
- **Java:** JDK 21 or higher installed (`java -version`)
- **Node.js:** Node.js v18+ and npm installed (`node -v`, `npm -v`)
- **MySQL:** MySQL Server 8.x installed and running on port 3306
- **Git:** Git installed (`git --version`)

---

### Step 1: Clone and Configure Environment

```bash
# Clone the repository
git clone https://github.com/<your-username>/Book4u.git
cd Book4u

# Create your local environment file from template
cp .env.example .env
```

Open `.env` and configure your local MySQL credentials:

```ini
SERVER_PORT=8080
DB_URL=jdbc:mysql://localhost:3306/book4u_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
DB_USERNAME=root
DB_PASSWORD=your_local_mysql_password
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000
VITE_API_BASE_URL=http://localhost:8080/api
```

---

### Step 2: Initialize MySQL Database

Open a terminal or MySQL Workbench and execute the schema script:

```bash
# Using MySQL CLI
mysql -u root -p < database/schema.sql
```

This creates the `book4u_db` database, all required tables, indexes, and initial seed data.

---

### Step 3: Run the Spring Boot Backend

Navigate to `backend/` and start the server using the Maven wrapper:

```bash
cd backend

# On Windows PowerShell / Command Prompt:
.\mvnw.cmd spring-boot:run

# On Linux / macOS:
./mvnw spring-boot:run
```

The backend starts at `http://localhost:8080`.

#### Test Health Endpoint
```bash
curl http://localhost:8080/api/health
```

Expected Response:
```json
{
  "status": "UP",
  "application": "Book4u"
}
```

#### Test Database Diagnostics Endpoint
```bash
curl http://localhost:8080/api/health/db
```

Expected Response:
```json
{
  "status": "CONNECTED",
  "databaseEngine": "MySQL Relational Database",
  "databaseVersion": "8.0.x",
  "responseTimeMs": 12,
  "message": "Database connection established successfully via Spring JDBC."
}
```

---

### Step 4: Run the React Frontend

Open a new terminal tab and navigate to `frontend/`:

```bash
cd frontend

# Install dependencies
npm install

# Start development server
npm run dev
```

Open your browser at `http://localhost:5173` to view the Book4u landing page and live health indicator.

---

## 7. Development Roadmap

| Phase | Milestone | Status |
| :--- | :--- | :---: |
| **Phase 1** | **Project Initialization & Architectural Foundation** | **Completed** |
| **Phase 2** | Database DDL Execution & Spring JDBC Repositories | Next |
| **Phase 3** | Student Registration, Campus Verification & Spring Security | Planned |
| **Phase 4** | Book Catalog Management & Multi-Mode Sharing (SWAP, GIVEAWAY, LENDING) | Planned |
| **Phase 5** | Exchange Request State Machine & Handover Confirmation | Planned |
| **Phase 6** | Smart Match Algorithm & Student Reputation System | Planned |
| **Phase 7** | Digital "Book Passport" & Reader Journey Chronicles | Planned |
| **Phase 8** | Admin Oversight Dashboard, Analytics & Campus Launch | Planned |

For in-depth architectural specifications, see the [`docs/`](docs/) directory:
- [Project Overview](docs/PROJECT_OVERVIEW.md)
- [System Architecture](docs/SYSTEM_ARCHITECTURE.md)
- [Database Design](docs/DATABASE_DESIGN.md)
- [Development Roadmap](docs/DEVELOPMENT_ROADMAP.md)

---

## 8. License

This project is licensed under the MIT License - see the LICENSE file for details.
