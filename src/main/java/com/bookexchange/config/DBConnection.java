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
    private static void loadProperties() {
        try (InputStream input = DBConnection.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
            if (input == null) {
                System.err.println("[DBConnection] WARNING: Unable to find " + PROPERTIES_FILE + ". Using default fallback settings.");
                driver = "com.mysql.cj.jdbc.Driver";
                url = "jdbc:mysql://localhost:3306/book_exchange_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
                username = "root";
                password = "";
            } else {
                properties.load(input);
                driver = properties.getProperty("db.driver", "com.mysql.cj.jdbc.Driver");
                url = properties.getProperty("db.url", "jdbc:mysql://localhost:3306/book_exchange_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
                username = properties.getProperty("db.username", "root");
                password = properties.getProperty("db.password", "");
            }

            // Allow environment variable overrides if provided
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

            System.out.println("[DBConnection] Configuration loaded successfully. Target URL: " + url + " | User: " + username);

        } catch (IOException e) {
            System.err.println("[DBConnection] ERROR: Failed to load properties file: " + e.getMessage());
            e.printStackTrace();
        }
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
