package com.bookexchange.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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
 * 1. Prioritizes cloud environment variables (Railway: MYSQLHOST, MYSQLPORT, MYSQLDATABASE, etc.).
 * 2. Strict validation: Rejects insecure localhost fallback in production environments.
 * 3. Credential masking: Never logs passwords, credential lengths, or unmasked JDBC URLs.
 * 4. Preserves backward-compatible local development configuration via db.properties / db.properties.local.
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

    // Static initialization block
    static {
        loadProperties();
        loadDriver();
    }

    // Private constructor prevents direct instantiation of utility class
    private DBConnection() {
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
     * Returns a safe, redacted version of the active JDBC URL with any credentials stripped.
     */
    public static String getSanitizedUrl() {
        if (url == null || url.isBlank()) {
            return "[NOT CONFIGURED]";
        }
        // Redacts user:password@ if embedded inside connection string
        return url.replaceAll("(?i)(://)([^:@/]+):([^@/]+)@", "$1***:***@");
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

        String railwayHost = System.getenv("MYSQLHOST");
        String railwayPort = System.getenv("MYSQLPORT");
        String railwayDb = System.getenv("MYSQLDATABASE");
        String railwayUser = System.getenv("MYSQLUSER");
        String railwayPass = System.getenv("MYSQLPASSWORD");
        String railwayUrl = System.getenv("MYSQL_URL");
        String databaseUrl = System.getenv("DATABASE_URL");

        if (envUrl != null && !envUrl.isBlank()) {
            url = envUrl;
            username = (envUser != null) ? envUser : "";
            password = (envPass != null) ? envPass : "";
        } else if (railwayHost != null && !railwayHost.isBlank()) {
            String port = (railwayPort != null && !railwayPort.isBlank()) ? railwayPort : "3306";
            String db = (railwayDb != null && !railwayDb.isBlank()) ? railwayDb : "railway";
            url = "jdbc:mysql://" + railwayHost + ":" + port + "/" + db + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
            username = (railwayUser != null && !railwayUser.isBlank()) ? railwayUser : "root";
            password = (railwayPass != null) ? railwayPass : "";
        } else if (railwayUrl != null && !railwayUrl.isBlank()) {
            url = railwayUrl.startsWith("jdbc:") ? railwayUrl : "jdbc:" + railwayUrl;
            username = (railwayUser != null) ? railwayUser : "";
            password = (railwayPass != null) ? railwayPass : "";
        } else if (databaseUrl != null && !databaseUrl.isBlank()) {
            url = databaseUrl.startsWith("jdbc:") ? databaseUrl : "jdbc:" + databaseUrl;
            username = (railwayUser != null) ? railwayUser : "";
            password = (railwayPass != null) ? railwayPass : "";
        } else {
            // ================================================================
            // Priority 2: Local Development Files (Localhost only)
            // ================================================================
            if (isProductionEnvironment()) {
                configurationError = "Missing required Railway database environment variables (MYSQLHOST, MYSQLPORT, MYSQLDATABASE, etc.). Insecure localhost fallback is disabled in production.";
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
                url = properties.getProperty("db.url", "");
                username = properties.getProperty("db.username", "");
                password = properties.getProperty("db.password", "");
            } else {
                configurationError = "No database configuration found (neither environment variables nor db.properties exist).";
                System.err.println("[DBConnection] ERROR: " + configurationError);
                return;
            }
        }

        // ====================================================================
        // Priority 3: Configuration Validation
        // ====================================================================
        if (url == null || url.isBlank()) {
            configurationError = "Database JDBC URL is missing or blank.";
            System.err.println("[DBConnection] ERROR: " + configurationError);
            return;
        }

        if (username == null || username.isBlank()) {
            configurationError = "Database username is missing or blank.";
            System.err.println("[DBConnection] ERROR: " + configurationError);
            return;
        }

        if (isProductionEnvironment()) {
            String lowerUrl = url.toLowerCase();
            if (lowerUrl.contains("localhost") || lowerUrl.contains("127.0.0.1")) {
                configurationError = "Insecure localhost database URL detected in production environment.";
                System.err.println("[DBConnection] CRITICAL: " + configurationError);
                return;
            }
            if (password == null || password.isBlank()) {
                configurationError = "Database password is required for production database connectivity.";
                System.err.println("[DBConnection] CRITICAL: " + configurationError);
                return;
            }
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
                + " | User=" + (username != null ? username : "[NOT SET]")
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
            System.err.println("[DBConnection] Connection failed to " + getSanitizedUrl() + ": " + e.getMessage());
            throw e;
        }
    }

    /**
     * Diagnostic helper method to test whether MySQL server is currently reachable.
     *
     * @return true if connection was successfully opened and verified, false otherwise
     */
    public static boolean testConnection() {
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
                System.err.println("[DBConnection] Error closing connection: " + e.getMessage());
            }
        }
    }
}
