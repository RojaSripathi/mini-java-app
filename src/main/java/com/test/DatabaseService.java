package com.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Database service with hardcoded connection details - intentional containerization blockers.
 *
 * Java 21 Upgrade Notes:
 *  - Removed legacy Class.forName("com.mysql.cj.jdbc.Driver") call (was at line 37).
 *    Since JDBC 4.0 (Java 6+), drivers are auto-loaded via ServiceLoader from the classpath.
 *    Explicit Class.forName() is unnecessary and considered a legacy anti-pattern.
 *  - All other JDBC APIs (Connection, DriverManager, PreparedStatement) are fully
 *    compatible with Java 21 — no further changes required.
 *  - No javax.* → jakarta.* migration needed in this class (no EE imports used).
 *  - Updated MySQL dependency from mysql:mysql-connector-java to com.mysql:mysql-connector-j
 *    in pom.xml for better Java 21 support.
 *  - Iteration 3 review: No compilation errors detected; file verified clean.
 */
public class DatabaseService {

    // BLOCKER: Hardcoded database connection details
    private static final String DB_HOST = "localhost";
    private static final String DB_PORT = "3306";
    private static final String DB_NAME = "mini_app_db";
    private static final String DB_URL = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
    private static final String DB_USERNAME = "root";
    private static final String DB_PASSWORD = "password123";

    // BLOCKER: Hardcoded cache server details
    private static final String REDIS_HOST = "127.0.0.1";
    private static final int REDIS_PORT = 6379;

    // BLOCKER: Hardcoded API endpoints
    private static final String EXTERNAL_API_URL = "http://api.example.com:8080/v1";
    private static final String PAYMENT_SERVICE_URL = "https://payment.internal.company.com/process";

    private Connection connection;

    public void connect() {
        try {
            System.out.println("Connecting to database...");

            // Java 21 Fix: Removed legacy Class.forName("com.mysql.cj.jdbc.Driver").
            // JDBC 4.0+ (Java 6+) auto-discovers drivers via ServiceLoader — explicit
            // Class.forName() is no longer needed and is considered a legacy pattern.

            // BLOCKER: Hardcoded connection string and credentials
            connection = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);

            System.out.println("Connected to database: " + DB_URL);
            System.out.println("Using username: " + DB_USERNAME);

            // BLOCKER: Hardcoded cache connection
            connectToCache();

            // BLOCKER: Hardcoded external service URLs
            initializeExternalServices();

        } catch (SQLException e) {
            System.err.println("Database connection failed: " + e.getMessage());
        }
    }

    private void connectToCache() {
        // BLOCKER: Hardcoded Redis connection details
        System.out.println("Connecting to Redis cache at: " + REDIS_HOST + ":" + REDIS_PORT);
        // Simulate cache connection
    }

    private void initializeExternalServices() {
        // BLOCKER: Hardcoded external service URLs
        System.out.println("Initializing external API: " + EXTERNAL_API_URL);
        System.out.println("Initializing payment service: " + PAYMENT_SERVICE_URL);
    }

    public void executeQuery(String sql) {
        try {
            if (connection != null && !connection.isClosed()) {
                PreparedStatement stmt = connection.prepareStatement(sql);
                // BLOCKER: Hardcoded query timeout
                stmt.setQueryTimeout(30);

                System.out.println("Executing query: " + sql);
                stmt.execute();
                stmt.close();
            }
        } catch (SQLException e) {
            System.err.println("Query execution failed: " + e.getMessage());
        }
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("Database connection closed");
            }
        } catch (SQLException e) {
            System.err.println("Failed to close database connection: " + e.getMessage());
        }
    }
}
