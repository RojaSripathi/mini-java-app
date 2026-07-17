package com.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Database service with hardcoded connection details - intentional containerization blockers.
 *
 * <p>Java 21 upgrade notes:
 * <ul>
 *   <li>Migrated from deprecated {@code mysql:mysql-connector-java} to
 *       {@code com.mysql:mysql-connector-j} (groupId/artifactId change in 8.0.31+).</li>
 *   <li>Explicit {@code Class.forName("com.mysql.cj.jdbc.Driver")} is no longer required
 *       since JDBC 4.0 (Java 6+); the driver is auto-loaded via ServiceLoader. The call
 *       has been removed to avoid reliance on the deprecated manual registration pattern.</li>
 *   <li>PreparedStatement and Connection are now managed with try-with-resources to align
 *       with modern Java best practices and avoid resource leaks.</li>
 * </ul>
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

            // Java 21 / JDBC 4.0+: Driver is auto-registered via ServiceLoader;
            // explicit Class.forName() is no longer needed and has been removed.
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
        // Java 21 best practice: use try-with-resources for AutoCloseable JDBC objects
        try {
            if (connection != null && !connection.isClosed()) {
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    // BLOCKER: Hardcoded query timeout
                    stmt.setQueryTimeout(30);

                    System.out.println("Executing query: " + sql);
                    stmt.execute();
                }
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
