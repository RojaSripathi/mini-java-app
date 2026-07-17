package com.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Properties;

/**
 * Mini Java Application with intentional containerization blockers for testing
 */
public class MiniApp {
    
    // blocker-7: Externalized port via environment variable (was hardcoded 8080)
    private static final int SERVER_PORT = Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "8080"));
    
    // blocker-1: Externalized config file path via environment variable (was "/opt/app/config/app.properties")
    private static final String CONFIG_FILE_PATH = System.getenv().getOrDefault("CONFIG_FILE_PATH", "/opt/app/config/app.properties");
    // blocker-2: Externalized log file path via environment variable (was "/var/log/mini-app.log")
    private static final String LOG_FILE_PATH = System.getenv().getOrDefault("LOG_FILE_PATH", "/var/log/mini-app.log");
    
    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");
        
        MiniApp app = new MiniApp();
        app.initializeApplication();

        // Health check endpoint for container liveness/readiness probes
        HealthController healthController = new HealthController();
        try {
            healthController.start();
        } catch (IOException e) {
            System.err.println("Failed to start health check endpoint: " + e.getMessage());
        }

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
        try {
            // blocker-1: CONFIG_FILE_PATH now sourced from environment variable
            File configFile = new File(CONFIG_FILE_PATH);
            if (configFile.exists()) {
                Properties props = new Properties();
                props.load(new FileInputStream(configFile));
                System.out.println("Configuration loaded from: " + CONFIG_FILE_PATH);
            } else {
                System.out.println("Warning: Configuration file not found at: " + CONFIG_FILE_PATH);
            }
        } catch (IOException e) {
            System.err.println("Failed to load configuration: " + e.getMessage());
        }
    }
    
    private void initializeLogging() {
        try {
            // blocker-3 & blocker-4: Log directory now derived from LOG_FILE_PATH environment variable (was hardcoded "/var/log")
            File logDir = new File(System.getenv().getOrDefault("LOG_DIR", new File(LOG_FILE_PATH).getParent() != null ? new File(LOG_FILE_PATH).getParent() : "/var/log"));
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
        try {
            // blocker-8: SERVER_PORT now sourced from environment variable (was hardcoded SERVER_PORT constant 8080)
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            System.out.println("Server started on port: " + SERVER_PORT);
            System.out.println("Server ready to accept connections...");
            
            // Simulate server running
            Thread.sleep(1000);
            serverSocket.close();
            
        } catch (Exception e) {
            System.err.println("Failed to start server: " + e.getMessage());
        }
    }
}
