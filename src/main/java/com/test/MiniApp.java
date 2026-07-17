package com.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Properties;

/**
 * Mini Java Application with intentional containerization blockers for testing.
 *
 * <p>Java 21 upgrade notes:
 * <ul>
 *   <li>{@link Thread#sleep(long)} now throws {@link InterruptedException}; the catch block
 *       has been updated to restore the interrupt flag via
 *       {@code Thread.currentThread().interrupt()} as required by Java concurrency best
 *       practices (unchanged behaviour, but now correctly handled).</li>
 *   <li>FileInputStream wrapped in try-with-resources to prevent resource leaks (Java 7+
 *       best practice, enforced more strictly by modern static analysis on Java 21).</li>
 *   <li>ServerSocket wrapped in try-with-resources for proper resource management.</li>
 * </ul>
 */
public class MiniApp {

    // BLOCKER: Hardcoded port number
    private static final int SERVER_PORT = 8080;

    // BLOCKER: Hardcoded absolute file path
    private static final String CONFIG_FILE_PATH = "/opt/app/config/app.properties";
    private static final String LOG_FILE_PATH = "/var/log/mini-app.log";

    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");

        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }

    private void initializeApplication() {
        // BLOCKER: Reading from hardcoded absolute path
        loadConfiguration();

        // BLOCKER: Writing to hardcoded absolute path
        initializeLogging();

        // Initialize database connection with hardcoded values
        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }

    private void loadConfiguration() {
        // Java 21 best practice: use try-with-resources for FileInputStream
        File configFile = new File(CONFIG_FILE_PATH);
        if (configFile.exists()) {
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
                System.out.println("Configuration loaded from: " + CONFIG_FILE_PATH);
            } catch (IOException e) {
                System.err.println("Failed to load configuration: " + e.getMessage());
            }
        } else {
            System.out.println("Warning: Configuration file not found at: " + CONFIG_FILE_PATH);
        }
    }

    private void initializeLogging() {
        try {
            // BLOCKER: Hardcoded absolute path for log file
            File logDir = new File("/var/log");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }

            File logFile = new File(LOG_FILE_PATH);
            if (!logFile.exists()) {
                logFile.createNewFile();
            }

            System.out.println("Logging initialized at: " + LOG_FILE_PATH);
        } catch (IOException e) {
            System.err.println("Failed to initialize logging: " + e.getMessage());
        }
    }

    private void startServer() {
        // Java 21 best practice: use try-with-resources for ServerSocket
        try (ServerSocket serverSocket = new ServerSocket(SERVER_PORT)) {
            System.out.println("Server started on port: " + SERVER_PORT);
            System.out.println("Server ready to accept connections...");

            // Simulate server running
            Thread.sleep(1000);

        } catch (InterruptedException e) {
            // Java 21 best practice: restore interrupt flag when catching InterruptedException
            Thread.currentThread().interrupt();
            System.err.println("Server interrupted: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Failed to start server: " + e.getMessage());
        }
    }
}
