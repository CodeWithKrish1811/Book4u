package com.bookexchange.config;

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
 * University Academic Project: Student Peer-to-Peer Book Exchange System
 * Technology: Core Java, JDBC (Java Database Connectivity), MySQL 8.x
 *
 * Concepts Demonstrated:
 * 1. Singleton Design Pattern / Static Utility: Centralized connection creation.
 * 2. Separation of Concerns: Database credentials loaded from db.properties file.
 * 3. Driver Registration: Loads MySQL Connector/J driver dynamically via reflection.
 * 4. Exception Handling: Structured handling of ClassNotFoundException and SQLException.
 * ============================================================================
 */
public class DBConnection {

    private static final String PROPERTIES_FILE = "db.properties";
    private static Properties properties = new Properties();

    private static String url;
    private static String username;
    private static String password;
    private static String driver;

    // Static initialization block: Executes once when the class is loaded into memory
    static {
        loadProperties();
        loadDriver();
    }

    // Private constructor prevents direct instantiation of utility class
    private DBConnection() {
    }

    /**
     * Reads database configuration from the db.properties file on the classpath.
     * Also checks for system environment variables (DB_URL, DB_USERNAME, DB_PASSWORD)
     * as optional overrides.
     */
    public static synchronized void loadProperties() {
        properties.clear();
        boolean loaded = false;

        // 1. Attempt loading from ClassLoader classpath (standard in Tomcat WAR)
        try (InputStream input = DBConnection.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
            if (input != null) {
                properties.load(input);
                loaded = true;
            }
        } catch (IOException e) {
            System.err.println("[DBConnection] Note: Classpath resource read: " + e.getMessage());
        }

        // 2. Direct file fallback for local IDE / Maven embedded runtime (checks local overrides first)
        String[] fallbackPaths = {
            "src/main/resources/db.properties.local",
            "db.properties.local",
            "src/main/resources/" + PROPERTIES_FILE,
            "target/classes/" + PROPERTIES_FILE,
            PROPERTIES_FILE
        };

        for (String path : fallbackPaths) {
            java.io.File file = new java.io.File(path);
            if (file.exists() && file.isFile()) {
                try (InputStream fis = new java.io.FileInputStream(file)) {
                    Properties diskProps = new Properties();
                    diskProps.load(fis);
                    for (String key : diskProps.stringPropertyNames()) {
                        String val = diskProps.getProperty(key);
                        if (val != null && !val.isBlank()) {
                            properties.setProperty(key, val);
                        }
                    }
                    loaded = true;
                    break;
                } catch (IOException ignored) {
                }
            }
        }

        if (!loaded) {
            System.err.println("[DBConnection] WARNING: Unable to find " + PROPERTIES_FILE + ". Using default fallback settings.");
            driver = "com.mysql.cj.jdbc.Driver";
            url = "jdbc:mysql://localhost:3306/book_exchange_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
            username = "root";
            password = "";
        } else {
            driver = properties.getProperty("db.driver", "com.mysql.cj.jdbc.Driver");
            url = properties.getProperty("db.url", "jdbc:mysql://localhost:3306/book_exchange_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
            username = properties.getProperty("db.username", "root");
            password = properties.getProperty("db.password", "");
        }

        // 1. Cloud & Container Environment Variables (Railway, Render, Docker)
        String railwayHost = System.getenv("MYSQLHOST");
        String railwayPort = System.getenv("MYSQLPORT");
        String railwayDb = System.getenv("MYSQLDATABASE");
        String railwayUser = System.getenv("MYSQLUSER");
        String railwayPass = System.getenv("MYSQLPASSWORD");
        String railwayUrl = System.getenv("MYSQL_URL");
        String databaseUrl = System.getenv("DATABASE_URL");

        if (railwayHost != null && !railwayHost.isBlank()) {
            String port = (railwayPort != null && !railwayPort.isBlank()) ? railwayPort : "3306";
            String db = (railwayDb != null && !railwayDb.isBlank()) ? railwayDb : "railway";
            url = "jdbc:mysql://" + railwayHost + ":" + port + "/" + db + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
        } else if (railwayUrl != null && !railwayUrl.isBlank()) {
            url = railwayUrl.startsWith("jdbc:") ? railwayUrl : "jdbc:" + railwayUrl;
        } else if (databaseUrl != null && !databaseUrl.isBlank()) {
            url = databaseUrl.startsWith("jdbc:") ? databaseUrl : "jdbc:" + databaseUrl;
        }

        if (railwayUser != null && !railwayUser.isBlank()) {
            username = railwayUser;
        }
        if (railwayPass != null) {
            password = railwayPass;
        }

        // 2. Direct environment variable overrides (highest precedence)
        String envUrl = System.getenv("DB_URL");
        if (envUrl != null && !envUrl.isBlank()) {
            url = envUrl;
        }

        String envUser = System.getenv("DB_USERNAME");
        if (envUser != null && !envUser.isBlank()) {
            username = envUser;
        }

        String envPass = System.getenv("DB_PASSWORD");
        if (envPass != null) {
            password = envPass;
        }

        String passIndicator = (password == null || password.isEmpty()) ? "[EMPTY - No password configured]" : "[CONFIGURED - " + password.length() + " chars]";
        System.out.println("[DBConnection] Configuration loaded. Target URL: " + url + " | User: " + username + " | Password: " + passIndicator);
    }

    /**
     * Public method to allow hot-reloading credentials without restarting JVM.
     */
    public static synchronized void reloadProperties() {
        loadProperties();
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
     * @throws SQLException If database access error occurs or credentials fail
     */
    public static Connection getConnection() throws SQLException {
        try {
            return DriverManager.getConnection(url, username, password);
        } catch (SQLException e) {
            // If connection failed due to missing/empty password, reload properties once in case config file was updated
            if (password == null || password.isEmpty() || (e.getMessage() != null && e.getMessage().contains("using password: NO"))) {
                loadProperties();
                if (password != null && !password.isEmpty()) {
                    return DriverManager.getConnection(url, username, password);
                }
            }
            System.err.println("[DBConnection] Connection failed: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Diagnostic helper method to test whether MySQL server is currently reachable.
     *
     * @return true if connection was successfully opened and verified, false otherwise
     */
    public static boolean testConnection() {
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
