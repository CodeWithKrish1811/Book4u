# Student Peer-to-Peer Book Exchange System (Book4u)

> **Your Books. Your Campus. Your Community.**

[![Java](https://img.shields.io/badge/Java-21%20LTS-orange.svg)](#)
[![Servlet](https://img.shields.io/badge/Servlet%20API-Jakarta%206.0-blue.svg)](#)
[![Server](https://img.shields.io/badge/Server-Apache%20Tomcat%2010.1+-yellow.svg)](#)
[![Database](https://img.shields.io/badge/Database-MySQL%208.x-blue.svg)](#)
[![Connectivity](https://img.shields.io/badge/Connectivity-Core%20JDBC-green.svg)](#)
[![Frontend](https://img.shields.io/badge/Frontend-HTML5%20%7C%20CSS3%20%7C%20JS-red.svg)](#)
[![Build](https://img.shields.io/badge/Build-Maven%203.x-purple.svg)](#)

---

## 1. Project Overview

The **Student Peer-to-Peer Book Exchange System** is a university campus web platform designed to solve the problem of textbook affordability and physical waste in higher education. Students often purchase expensive academic textbooks that sit idle after end-of-semester exams.

This application directly connects university students so they can share textbooks with verified peers on campus.

### Supported Sharing Modes (Strictly Zero Commercial Buying/Selling)

In strict compliance with academic requirements, **there is NO payment or selling functionality**:

1. 🔄 **SWAP** — Bilateral book exchange. Student A trades a book they no longer need for a textbook required for their current semester courses.
2. 🎁 **GIVEAWAY** — Altruistic gifting. Senior or graduating students pass down foundational textbooks to juniors with zero cost or return obligation.
3. ⏳ **LENDING** — Time-bound loan. A student temporarily lends a reference book or lab manual with an agreed calendar return date.

---

## 2. Technology Stack & Architecture

This application is built with clean **Core Java, OOP principles, Jakarta Servlets, JDBC, and MySQL**, intentionally avoiding heavy frameworks (no Spring Boot, no Hibernate, no React) to clearly demonstrate fundamental web and database concepts.

- **Language:** Java 21 (LTS)
- **Web Layer:** Jakarta Servlet API 6.0 (`jakarta.servlet.*`)
- **Server:** Apache Tomcat 10.1.x
- **Data Access:** Java Database Connectivity (JDBC) via `java.sql.PreparedStatement`
- **Database Engine:** MySQL 8.x (`InnoDB`, `utf8mb4`)
- **Frontend:** HTML5, CSS3, Vanilla JavaScript (Fetch API, DOM manipulation)
- **JSON Processing:** Google Gson 2.11.0
- **Build Tool:** Apache Maven 3.x (with Maven Wrapper `mvnw`)

---

## 3. Project Folder Structure

```
Book4u/
│
├── pom.xml                                  # Maven project configuration (WAR packaging)
├── mvnw / mvnw.cmd                          # Cross-platform Maven wrapper
│
├── database/
│   └── book_exchange_db.sql                 # Complete MySQL schema DDL & sample data
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── bookexchange/
│   │   │           ├── config/              # Database connection factory
│   │   │           │   └── DBConnection.java
│   │   │           ├── model/               # Java Bean / POJO domain entities
│   │   │           │   ├── User.java
│   │   │           │   └── Book.java
│   │   │           ├── dao/                 # Raw JDBC data access objects
│   │   │           │   ├── UserDAO.java
│   │   │           │   └── BookDAO.java
│   │   │           ├── service/             # Business logic & validation
│   │   │           │   ├── UserService.java
│   │   │           │   └── BookService.java
│   │   │           ├── servlet/             # Jakarta HTTP Servlets (Controllers)
│   │   │           │   ├── HealthCheckServlet.java
│   │   │           │   ├── BookServlet.java
│   │   │           │   └── UserServlet.java
│   │   │           ├── filter/              # Servlet filters (Encoding & Logging)
│   │   │           │   ├── CharacterEncodingFilter.java
│   │   │           │   └── LoggingFilter.java
│   │   │           └── util/                # Utilities (Password hashing, Validation)
│   │   │               ├── PasswordUtil.java
│   │   │               └── ValidationUtil.java
│   │   │
│   │   ├── resources/
│   │   │   └── db.properties                # JDBC connection parameters
│   │   │
│   │   └── webapp/                          # Web presentation layer
│   │       ├── WEB-INF/
│   │       │   └── web.xml                  # Jakarta EE 10 deployment descriptor
│   │       ├── css/
│   │       │   └── style.css                # Responsive stylesheet
│   │       ├── js/
│   │       │   └── app.js                   # Client Fetch API interactions
│   │       └── index.html                   # Main portal UI
│
├── .gitignore                               # Git exclusion rules
└── README.md                                # Project documentation
```

---

## 4. Local Setup Instructions

### Prerequisites
- **JDK:** Java 21 or higher installed (`java -version`)
- **Server:** Apache Tomcat 10.1.x downloaded and extracted
- **Database:** MySQL Server 8.x running on port `3306`
- **IDE:** Visual Studio Code (or any preferred Java IDE)
- **Build Tool:** Maven (or use included `mvnw.cmd`)

---

### Step 1: Initialize the MySQL Database

Open a terminal or command prompt and execute the provided database script:

```bash
mysql -u root -p < database/book_exchange_db.sql
```

This creates the `book_exchange_db` database, 5 relational tables (`students`, `books`, `exchange_requests`, `exchanges`, `reviews`), and initial sample textbooks (Java, DBMS, DSA, Networks).

---

### Step 2: Configure Database Credentials

Open `src/main/resources/db.properties` and enter your local MySQL password:

```properties
db.driver=com.mysql.cj.jdbc.Driver
db.url=jdbc:mysql://localhost:3306/book_exchange_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
db.username=root
db.password=your_mysql_password_here
```

---

### Step 3: Compile and Package the Application

From the root project directory (`Book4u/`), execute the Maven wrapper to build the WAR archive:

**On Windows PowerShell:**
```powershell
.\mvnw.cmd clean package
```

**On Windows Command Prompt (CMD):**
```cmd
mvnw.cmd clean package
```

**On Linux / macOS:**
```bash
./mvnw clean package
```

This compiles all Java classes with the **Java 21** compiler target and outputs:
```
target/Book4u.war
```

---

### Step 4: Run on Apache Tomcat 10.1

1. Copy the generated `target/Book4u.war` file into Apache Tomcat's `webapps/` directory:
   ```powershell
   Copy-Item target\Book4u.war "C:\path\to\apache-tomcat-10.1.x\webapps\"
   ```
2. Start Apache Tomcat:
   ```powershell
   & "C:\path\to\apache-tomcat-10.1.x\bin\startup.bat"
   ```
3. Open your browser and navigate to:
   ```
   http://localhost:8080/Book4u/
   ```

#### Health Check Diagnostic Endpoint:
```
http://localhost:8080/Book4u/api/health
```

Expected JSON Response:
```json
{
  "status": "UP",
  "project": "Student Peer-to-Peer Book Exchange System (Book4u)",
  "server": "Apache Tomcat 10.1+ (Jakarta Servlet 6.0)",
  "javaVersion": "21",
  "databaseStatus": "CONNECTED",
  "timestamp": "2026-11-19T13:20:00"
}
```

---

## 5. Visual Studio Code Setup Guide

To work with this project comfortably in VS Code:

### Recommended VS Code Extensions:
1. **Extension Pack for Java** (by Microsoft) — provides Java language server, syntax checking, and Maven integration.
2. **Community Server Connectors** or **Tomcat for Java** — provides one-click server deployment directly inside VS Code.

### How to Import into VS Code:
1. Open Visual Studio Code.
2. Click **File &rarr; Open Folder...** and select `C:\Users\rajee\OneDrive\Desktop\Book4u`.
3. VS Code will automatically detect `pom.xml` and synchronize the Java project dependencies.
4. In the **Maven** sidebar view, you can run build targets (`clean`, `compile`, `package`) with a single click.

---

## 6. Architecture Explanation for Academic Evaluation

During project defense or viva, you can explain the architecture as follows:

1. **Separation of Concerns:**
   - **Model (`com.bookexchange.model`):** Pure POJO classes encapsulating table entities (`User`, `Book`).
   - **DAO (`com.bookexchange.dao`):** Contains raw SQL queries using `PreparedStatement`. No business logic.
   - **Service (`com.bookexchange.service`):** Contains business logic (validating email, checking if lending duration is valid, password hashing).
   - **Servlet (`com.bookexchange.servlet`):** Controllers that receive HTTP GET/POST requests, invoke services, and return JSON.
   - **Filter (`com.bookexchange.filter`):** Interceptors ensuring UTF-8 encoding and logging request timing.
   - **Config (`com.bookexchange.config`):** Centralized `DBConnection` factory reading `db.properties`.
2. **Security & Integrity:**
   - Passwords are never stored as plaintext (hashed using SHA-256 with salt in `PasswordUtil`).
   - Parameterized SQL (`?` placeholders) prevents SQL injection attacks across all database interactions.
   - No buying, selling, or commercial transactions.

---

## 7. License

Academic Coursework Project - University Campus Peer-to-Peer Book Exchange Platform.
