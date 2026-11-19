package com.bookexchange.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * ============================================================================
 * DBConnection - JDBC Database Connection Factory
 * ============================================================================
 * University Academic Project: Student Peer-to-Peer Book Exchange System (Book4u)
 * Technology: Core Java, JDBC (Java Database Connectivity), MySQL 8.x
 *
 * Production Hardening:
 * 1. Safe URI & RFC 3986 URL parsing with decoding for @, :, /, ?, #, and %.
 * 2. Fallback parser for non-standard / unencoded special characters.
 * 3. Consistent credential resolution handling conflicting and missing variables.
 * 4. Strict validation: Rejects insecure localhost in production environments.
 * 5. Complete credential masking in logs and exception messages.
 * 6. Thread-isolated test connection override mechanism.
 * ============================================================================
 */
public class DBConnection {

    private static final String PROPERTIES_FILE = "db.properties";
    private static final Properties properties = new Properties();

    private static String url;
    private static String username;
    private static String password;
    private static String driver = "com.mysql.cj.jdbc.Driver";

    private static boolean configured = false;
    private static String configurationError = null;

    // Test hook for simulating connection state in automated tests with complete thread isolation
    private static final ThreadLocal<Boolean> testConnectionOverride = new ThreadLocal<>();

    // Static initialization block
    static {
        loadProperties();
        loadDriver();
    }

    // Private constructor prevents direct instantiation of utility class
    private DBConnection() {
    }

    /**
     * Data transfer object representing a parsed database URL and any extracted credentials.
     */
    public static class ParsedDatabaseUrl {
        private final String cleanJdbcUrl;
        private final String username;
        private final String password;

        public ParsedDatabaseUrl(String cleanJdbcUrl, String username, String password) {
            this.cleanJdbcUrl = cleanJdbcUrl;
            this.username = username;
            this.password = password;
        }

        public String getCleanJdbcUrl() {
            return cleanJdbcUrl;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }
    }

    /**
     * Data transfer object representing the outcome of database configuration validation.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String error;

        public ValidationResult(boolean valid, String error) {
            this.valid = valid;
            this.error = error;
        }

        public boolean isValid() {
            return valid;
        }

        public String getError() {
            return error;
        }
    }

    /**
     * Checks if current runtime is a production environment (Railway / Cloud container).
     */
    public static boolean isProductionEnvironment() {
        return System.getenv("RAILWAY_ENVIRONMENT") != null
                || System.getenv("RAILWAY_PROJECT_ID") != null
                || "production".equalsIgnoreCase(System.getenv("APP_ENV"))
                || "production".equalsIgnoreCase(System.getenv("ENVIRONMENT"));
    }

    /**
     * Safely percent-decodes a URL token without crashing on invalid or incomplete escape sequences (e.g. lone '%').
     */
    public static String safeUrlDecode(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // Fallback: manually decode valid %XX hexadecimal sequences while preserving literal characters
            StringBuilder sb = new StringBuilder();
            int i = 0;
            int len = value.length();
            while (i < len) {
                char c = value.charAt(i);
                if (c == '%' && i + 2 < len) {
                    try {
                        int code = Integer.parseInt(value.substring(i + 1, i + 3), 16);
                        sb.append((char) code);
                        i += 3;
                        continue;
                    } catch (NumberFormatException ignored) {
                    }
                }
                sb.append(c);
                i++;
            }
            return sb.toString();
        }
    }

    /**
     * Sanitizes any database connection URL by redacting embedded passwords and query credentials.
     */
    public static String sanitizeUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return "[NOT CONFIGURED]";
        }
        String sanitized = rawUrl;

        // Mask query string password parameter: ?password=... or &pwd=...
        sanitized = sanitized.replaceAll("(?i)([?&](?:password|pwd)=)[^&]*", "$1***");

        // Handle authority userInfo: between :// and the last @ before the first / or ?
        int schemeEnd = sanitized.indexOf("://");
        if (schemeEnd != -1) {
            int authStart = schemeEnd + 3;
            int pathStart = sanitized.indexOf('/', authStart);
            int queryStart = sanitized.indexOf('?', authStart);
            int limit = sanitized.length();
            if (pathStart != -1 && pathStart < limit) {
                limit = pathStart;
            }
            if (queryStart != -1 && queryStart < limit) {
                limit = queryStart;
            }

            String authority = sanitized.substring(authStart, limit);
            int atIndex = authority.lastIndexOf('@');
            if (atIndex != -1) {
                String userInfo = authority.substring(0, atIndex);
                String hostPart = authority.substring(atIndex + 1);
                String maskedUserInfo = userInfo.contains(":") ? "***:***" : "***";
                sanitized = sanitized.substring(0, authStart) + maskedUserInfo + "@" + hostPart + sanitized.substring(limit);
            }
        }

        return sanitized;
    }

    /**
     * Returns a safe, redacted version of the active JDBC URL with any credentials stripped.
     */
    public static String getSanitizedUrl() {
        return sanitizeUrl(url);
    }

    /**
     * Parses a database URL (e.g. mysql://user:pass@host:port/db or jdbc:mysql://...)
     * and extracts embedded credentials, returning a clean JDBC URL.
     * Supports percent-encoded credentials, special characters (@, :, /, ?, #, %), and Railway URLs.
     */
    public static ParsedDatabaseUrl parseDatabaseUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return new ParsedDatabaseUrl(rawUrl, null, null);
        }

        String urlStr = rawUrl.trim();
        String uriCandidate = urlStr;

        // Strip leading "jdbc:" if present to allow standard URI parsing
        if (urlStr.startsWith("jdbc:")) {
            uriCandidate = urlStr.substring(5);
        }
        if (uriCandidate.startsWith("//")) {
            uriCandidate = "mysql:" + uriCandidate;
        }

        String user = null;
        String pass = null;
        String hostPart = null;
        String pathAndQuery = "";

        // Strategy 1: Safe standard URI parsing (handles RFC 2396/3986 percent-encoded credentials)
        boolean uriParsed = false;
        try {
            URI uri = new URI(uriCandidate);
            if (uri.getHost() != null) {
                hostPart = uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : ":3306");
                pathAndQuery = (uri.getPath() != null ? uri.getPath() : "")
                        + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "");

                String rawUserInfo = uri.getRawUserInfo();
                if (rawUserInfo != null && !rawUserInfo.isBlank()) {
                    int colon = rawUserInfo.indexOf(':');
                    if (colon != -1) {
                        user = safeUrlDecode(rawUserInfo.substring(0, colon));
                        pass = safeUrlDecode(rawUserInfo.substring(colon + 1));
                    } else {
                        user = safeUrlDecode(rawUserInfo);
                        pass = null;
                    }
                }
                uriParsed = true;
            }
        } catch (URISyntaxException ignored) {
            // Standard URI parsing failed due to unencoded special characters; proceed to fallback parser
        }

        // Strategy 2: Robust fallback parser for non-standard or unencoded special characters
        if (!uriParsed) {
            int schemeEnd = urlStr.indexOf("://");
            if (schemeEnd == -1) {
                String clean = urlStr.startsWith("jdbc:") ? urlStr : "jdbc:" + urlStr;
                return new ParsedDatabaseUrl(clean, null, null);
            }

            int authStart = schemeEnd + 3;
            int pathStart = urlStr.indexOf('/', authStart);
            int queryStart = urlStr.indexOf('?', authStart);

            int limit = urlStr.length();
            if (pathStart != -1 && pathStart < limit) {
                limit = pathStart;
            }
            if (queryStart != -1 && queryStart < limit) {
                limit = queryStart;
            }

            String authority = urlStr.substring(authStart, limit);
            pathAndQuery = urlStr.substring(limit);

            hostPart = authority;
            int atIndex = authority.lastIndexOf('@');
            if (atIndex != -1) {
                String userInfo = authority.substring(0, atIndex);
                hostPart = authority.substring(atIndex + 1);

                int colonIndex = userInfo.indexOf(':');
                if (colonIndex != -1) {
                    user = safeUrlDecode(userInfo.substring(0, colonIndex));
                    pass = safeUrlDecode(userInfo.substring(colonIndex + 1));
                } else {
                    user = safeUrlDecode(userInfo);
                    pass = null;
                }
            }
        }

        // Reconstruct clean JDBC URL without plaintext credentials
        if (hostPart == null || hostPart.isBlank()) {
            hostPart = "localhost:3306";
        } else if (!hostPart.contains(":")) {
            hostPart += ":3306";
        }

        String cleanJdbcUrl = "jdbc:mysql://" + hostPart + pathAndQuery;
        return new ParsedDatabaseUrl(cleanJdbcUrl, user, pass);
    }

    /**
     * Resolves the first non-null, non-blank credential candidate.
     */
    public static String resolveCredential(String... candidates) {
        if (candidates == null) {
            return "";
        }
        for (String c : candidates) {
            if (c != null && !c.isBlank()) {
                return c.trim();
            }
        }
        return "";
    }

    /**
     * Validates database configuration values independently of a live MySQL connection.
     * Does not expose passwords or complete connection strings in error messages.
     */
    public static ValidationResult validateConfig(String checkUrl, String checkUser, String checkPass, boolean checkProd) {
        if (checkUrl == null || checkUrl.isBlank()) {
            return new ValidationResult(false, "Database JDBC URL is missing or blank.");
        }
        if (checkUser == null || checkUser.isBlank()) {
            return new ValidationResult(false, "Database username is missing or blank.");
        }
        if (checkProd) {
            String lowerUrl = checkUrl.toLowerCase();
            if (lowerUrl.contains("localhost") || lowerUrl.contains("127.0.0.1") || lowerUrl.contains("::1")) {
                return new ValidationResult(false, "Insecure localhost database URL detected in production environment.");
            }
            if (checkPass == null || checkPass.isBlank()) {
                return new ValidationResult(false, "Database password is required for production database connectivity.");
            }
        }
        return new ValidationResult(true, null);
    }

    /**
     * Formats an SQL exception safely for server logs without leaking credentials or complete sensitive URLs.
     */
    public static String formatSqlException(String context, SQLException e) {
        if (e == null) {
            return "[" + (context != null ? context : "Database") + "] Database error occurred.";
        }
        StringBuilder sb = new StringBuilder("[");
        sb.append(context != null ? context : "Database").append("] ");
        sb.append("SQLState=").append(e.getSQLState() != null ? e.getSQLState() : "N/A");
        sb.append(" | ErrorCode=").append(e.getErrorCode());
        sb.append(" | Details=").append(sanitizeUrl(e.getMessage()));
        return sb.toString();
    }

    /**
     * Loads and validates database configuration.
     * Prioritizes Railway cloud environment variables over static local property files.
     * Insecure localhost fallbacks are strictly rejected in production.
     */
    public static synchronized void loadProperties() {
        properties.clear();
        configured = false;
        configurationError = null;
        driver = "com.mysql.cj.jdbc.Driver";
        url = null;
        username = null;
        password = null;

        // ====================================================================
        // Priority 1: Cloud & Container Environment Variables (Railway / Cloud)
        // ====================================================================
        String envUrl = System.getenv("DB_URL");
        String envUser = System.getenv("DB_USERNAME");
        String envPass = System.getenv("DB_PASSWORD");

        String railwayUrl = System.getenv("MYSQL_URL");
        String databaseUrl = System.getenv("DATABASE_URL");
        String railwayHost = System.getenv("MYSQLHOST");
        String railwayPort = System.getenv("MYSQLPORT");
        String railwayDb = System.getenv("MYSQLDATABASE");
        String railwayUser = System.getenv("MYSQLUSER");
        String railwayPass = System.getenv("MYSQLPASSWORD");

        if (envUrl != null && !envUrl.isBlank()) {
            ParsedDatabaseUrl parsed = parseDatabaseUrl(envUrl);
            url = parsed.getCleanJdbcUrl();
            username = resolveCredential(envUser, parsed.getUsername(), railwayUser);
            password = resolveCredential(envPass, parsed.getPassword(), railwayPass);
        } else if (railwayUrl != null && !railwayUrl.isBlank()) {
            ParsedDatabaseUrl parsed = parseDatabaseUrl(railwayUrl);
            url = parsed.getCleanJdbcUrl();
            username = resolveCredential(envUser, railwayUser, parsed.getUsername());
            password = resolveCredential(envPass, railwayPass, parsed.getPassword());
        } else if (databaseUrl != null && !databaseUrl.isBlank()) {
            ParsedDatabaseUrl parsed = parseDatabaseUrl(databaseUrl);
            url = parsed.getCleanJdbcUrl();
            username = resolveCredential(envUser, railwayUser, parsed.getUsername());
            password = resolveCredential(envPass, railwayPass, parsed.getPassword());
        } else if (railwayHost != null && !railwayHost.isBlank()) {
            String port = (railwayPort != null && !railwayPort.isBlank()) ? railwayPort : "3306";
            String db = (railwayDb != null && !railwayDb.isBlank()) ? railwayDb : "railway";
            url = "jdbc:mysql://" + railwayHost + ":" + port + "/" + db + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
            username = resolveCredential(envUser, railwayUser, "root");
            password = resolveCredential(envPass, railwayPass);
        } else {
            // ================================================================
            // Priority 2: Local Development Files (Localhost only)
            // ================================================================
            if (isProductionEnvironment()) {
                configurationError = "Missing required Railway database environment variables (MYSQLHOST, MYSQL_URL, DATABASE_URL, etc.). Insecure localhost fallback is disabled in production.";
                System.err.println("[DBConnection] CRITICAL: " + configurationError);
                return;
            }

            boolean fileLoaded = false;
            String[] fallbackPaths = {
                "src/main/resources/db.properties.local",
                "db.properties.local",
                "src/main/resources/" + PROPERTIES_FILE,
                "target/classes/" + PROPERTIES_FILE,
                PROPERTIES_FILE
            };

            for (String path : fallbackPaths) {
                File file = new File(path);
                if (file.exists() && file.isFile()) {
                    try (InputStream fis = new FileInputStream(file)) {
                        properties.load(fis);
                        fileLoaded = true;
                        break;
                    } catch (IOException ignored) {
                    }
                }
            }

            if (!fileLoaded) {
                try (InputStream input = DBConnection.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
                    if (input != null) {
                        properties.load(input);
                        fileLoaded = true;
                    }
                } catch (IOException ignored) {
                }
            }

            if (fileLoaded) {
                driver = properties.getProperty("db.driver", "com.mysql.cj.jdbc.Driver");
                String rawLocalUrl = properties.getProperty("db.url", "");
                ParsedDatabaseUrl parsed = parseDatabaseUrl(rawLocalUrl);
                url = parsed.getCleanJdbcUrl();
                username = resolveCredential(properties.getProperty("db.username", ""), parsed.getUsername());
                password = resolveCredential(properties.getProperty("db.password", ""), parsed.getPassword());
            } else {
                configurationError = "No database configuration found (neither environment variables nor db.properties exist).";
                System.err.println("[DBConnection] ERROR: " + configurationError);
                return;
            }
        }

        // Ensure cloud URL has necessary MySQL connection flags if query is empty
        if (url != null && !url.isBlank() && !url.contains("?")) {
            url += "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
        }

        // ====================================================================
        // Priority 3: Configuration Validation
        // ====================================================================
        ValidationResult validation = validateConfig(url, username, password, isProductionEnvironment());
        if (!validation.isValid()) {
            configurationError = validation.getError();
            System.err.println("[DBConnection] " + (isProductionEnvironment() ? "CRITICAL: " : "ERROR: ") + configurationError);
            return;
        }

        configured = true;
        logConfigurationSummary();
    }

    /**
     * Safely logs configuration without exposing passwords or credentials.
     */
    private static void logConfigurationSummary() {
        String passStatus = (password != null && !password.isEmpty()) ? "[CONFIGURED]" : "[NOT SET]";
        System.out.println("[DBConnection] Database active: URL=" + getSanitizedUrl()
                + " | User=" + (username != null && !username.isBlank() ? username : "[NOT SET]")
                + " | Password=" + passStatus
                + " | Mode=" + (isProductionEnvironment() ? "PRODUCTION" : "LOCAL"));
    }

    /**
     * Public method to allow hot-reloading credentials without restarting JVM.
     */
    public static synchronized void reloadProperties() {
        loadProperties();
    }

    /**
     * Returns true if database configuration passed validation.
     */
    public static boolean isConfigured() {
        return configured;
    }

    /**
     * Returns the configuration error reason if validation failed.
     */
    public static String getConfigurationError() {
        return configurationError;
    }

    /**
     * Set a thread-isolated override value for testConnection() during unit tests.
     */
    public static void setTestConnectionOverride(Boolean override) {
        if (override == null) {
            testConnectionOverride.remove();
        } else {
            testConnectionOverride.set(override);
        }
    }

    /**
     * Reset the thread-isolated override value for testConnection().
     */
    public static void resetTestConnectionOverride() {
        testConnectionOverride.remove();
    }

    /**
     * Loads the MySQL JDBC Driver class into the JVM.
     */
    private static void loadDriver() {
        try {
            Class.forName(driver);
            System.out.println("[DBConnection] MySQL JDBC Driver registered successfully: " + driver);
        } catch (ClassNotFoundException e) {
            System.err.println("[DBConnection] CRITICAL: MySQL Driver class not found in classpath: " + driver);
            System.err.println("[DBConnection] Ensure mysql-connector-j dependency is included in pom.xml");
            e.printStackTrace();
        }
    }

    /**
     * Establishes and returns a new active Connection to the MySQL database.
     *
     * @return Active java.sql.Connection object
     * @throws SQLException If database access error occurs, credentials fail, or configuration is invalid
     */
    public static Connection getConnection() throws SQLException {
        if (!configured) {
            loadProperties();
            if (!configured) {
                throw new SQLException("Database is not properly configured: "
                        + (configurationError != null ? configurationError : "Validation failed."));
            }
        }
        try {
            return DriverManager.getConnection(url, username, password);
        } catch (SQLException e) {
            System.err.println(formatSqlException("DBConnection.getConnection to " + getSanitizedUrl(), e));
            throw e;
        }
    }

    /**
     * Diagnostic helper method to test whether MySQL server is currently reachable.
     * Checks thread-isolated test override before attempting live connection.
     *
     * @return true if connection was successfully opened and verified, false otherwise
     */
    public static boolean testConnection() {
        Boolean override = testConnectionOverride.get();
        if (override != null) {
            return override;
        }
        if (!configured) {
            loadProperties();
            if (!configured) {
                return false;
            }
        }
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Safely closes a database connection if open.
     *
     * @param conn The connection to close
     */
    public static void closeConnection(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                System.err.println(formatSqlException("DBConnection.closeConnection", e));
            }
        }
    }
}
