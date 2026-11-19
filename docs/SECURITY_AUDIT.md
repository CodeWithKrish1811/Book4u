# Book4u – Security Audit & Production Hardening Specification

This document details the production security architecture, session controls, and CSRF protection mechanisms implemented in the **Student Peer-to-Peer Book Exchange System (Book4u)**.

---

## 1. Database URL & Credential Protection

### Architecture & Precedence
`DBConnection.java` supports multi-format cloud and local connection resolution:
1. **Cloud Environment Variables:**
   - `DB_URL` (Custom application database connection string)
   - `MYSQL_URL` (Railway MySQL plugin connection URL)
   - `DATABASE_URL` (12-Factor standard database connection URL)
   - Railway variables: `MYSQLHOST`, `MYSQLPORT`, `MYSQLDATABASE`, `MYSQLUSER`, `MYSQLPASSWORD`
2. **Local Development Fallback:**
   - `src/main/resources/db.properties.local` / `db.properties`
   - Strictly disabled in production container environments (`RAILWAY_ENVIRONMENT`, `APP_ENV=production`).

### URL Parsing & Credential Sanitization
- **URI Safe Parsing (RFC 3986):** Percent-encoded credentials containing `@`, `:`, `/`, `?`, `#`, `%` are decoded via `safeUrlDecode()`.
- **Special Character Support:** Unencoded colons and `@` symbols in complex passwords are parsed via robust boundary markers.
- **Redaction:** `sanitizeUrl()` redacts credentials in authority (`***:***@host`) and query parameters (`?password=***`). Complete connection strings and passwords are never output in error logs or client responses.
- **Structured Error Logging:** Server logs record `[Context] SQLState=... | ErrorCode=... | Details=...` using `DBConnection.formatSqlException()`.

---

## 2. Session Management & Cookie Security

| Security Control | Implementation Mechanism | Protection Goal |
| :--- | :--- | :--- |
| **HttpOnly Cookies** | `web.xml` `<http-only>true</http-only>` & `SecurityConfigListener` | Prevents client-side JavaScript access to `JSESSIONID`, defeating XSS session theft. |
| **Secure Cookies** | Dynamically configured in `SecurityConfigListener` | Mandates transmission over TLS/HTTPS in cloud/production while allowing local HTTP testing. |
| **SameSite=Lax** | `SessionCookieConfig.setAttribute("SameSite", "Lax")` | Prevents the browser from attaching `JSESSIONID` to cross-site state-changing requests. |
| **Session Fixation Prevention** | `AuthServlet.handleLogin()` | Any existing session is explicitly invalidated before creating a new authenticated session ID. |
| **Session Inactivity Timeout** | `web.xml` `<session-timeout>30</session-timeout>` & `session.setMaxInactiveInterval(30 * 60)` | Inactivates abandoned sessions after 30 minutes. |
| **Session Invalidation & Cookie Eviction** | `AuthServlet.handleLogout()` | Clears session attributes, calls `session.invalidate()`, and evicts the cookie with `MaxAge=0`. |

---

## 3. Cross-Site Request Forgery (CSRF) Protection

Book4u employs a layered defense-in-depth CSRF mitigation strategy:

1. **SameSite Cookie Enforcement:**
   Session cookies are configured with `SameSite=Lax`. In all modern browsers, cookies are omitted from cross-site POST/PUT/DELETE requests initiated by third-party origins.
2. **Strict Origin & Host Header Verification:**
   `AuthenticationFilter.isAllowedOrigin()` intercepts state-changing HTTP methods (`POST`, `PUT`, `DELETE`). If the browser provides an `Origin` header that does not match the server host, reverse-proxy host, or trusted domain, the request is immediately rejected with `HTTP 403 Forbidden`.
3. **Application/JSON Content-Type Gate:**
   All authenticated API modifications require `Content-Type: application/json`. Standard HTML cross-origin form submissions cannot forge JSON payloads with custom headers without triggering CORS pre-flight (`OPTIONS`), preventing blind cross-site execution.
4. **Public Endpoint Exemption:**
   Public authentication and discovery endpoints (`/api/auth/login`, `/api/auth/register`, `/api/health/*`) remain unencumbered by CSRF token pre-flights, ensuring registration and login flows never break.

---

## 4. Health & Diagnostic Probes

- **Liveness Probe (`GET /api/health/live`):**
  - Verifies that Apache Tomcat and the Book4u servlet container are running and responsive.
  - Returns `HTTP 200 OK` regardless of external dependency state, preventing orchestrator container crash loops.
- **Readiness Probe (`GET /api/health/ready`):**
  - Probes live MySQL database connectivity.
  - Returns `HTTP 200 OK` (`UP`, `CONNECTED`) when database is accessible.
  - Returns `HTTP 503 Service Unavailable` (`DEGRADED`, `DISCONNECTED`) when MySQL is unreachable.
- **Legacy Health Endpoint (`GET /api/health`):**
  - Fully backward-compatible; reports composite application and database status with HTTP 200/503.

---

## 5. Thread Isolation in Automated Testing

- `DBConnection.setTestConnectionOverride()` utilizes a `ThreadLocal<Boolean>` storage mechanism.
- Concurrent and parallel test workers in Maven Surefire execute in complete isolation without cross-test state leakage.
- Unit tests run deterministically without requiring a live MySQL server.
