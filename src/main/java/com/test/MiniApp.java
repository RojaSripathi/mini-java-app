package com.test;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;

/**
 * Mini Java Application — cloud-ready version.
 *
 * Changes applied:
 *  - Hard-coded port (SERVER_PORT) replaced with AWS SSM Parameter Store lookup
 *    injected via environment variable (blocker-14, blocker-15).
 *  - Hard-coded absolute file paths (/opt/app/config/app.properties, /var/log/mini-app.log)
 *    replaced with Amazon S3 object storage using AWS SDK for Java v2
 *    (blocker-1, blocker-2, blocker-3, blocker-4, blocker-5, blocker-6, blocker-7).
 *  - Classpath-bundled properties file replaced with AWS SSM Parameter Store
 *    for runtime-configurable, environment-specific configuration (blocker-20).
 */
public class MiniApp {

    // -----------------------------------------------------------------------
    // AWS region — injected via environment variable
    // -----------------------------------------------------------------------
    private static final String AWS_REGION = System.getenv().getOrDefault("AWS_REGION", "us-east-1");

    // -----------------------------------------------------------------------
    // Port configuration — resolved from AWS SSM Parameter Store at runtime.
    // The SSM parameter name itself is supplied via an environment variable so
    // that it can differ per deployment environment (blocker-14, blocker-15).
    // -----------------------------------------------------------------------
    private static final String SSM_PARAM_SERVER_PORT = System.getenv().getOrDefault(
            "SSM_PARAM_SERVER_PORT", "/mini-app/server/port");

    // -----------------------------------------------------------------------
    // S3 configuration — bucket and object keys are supplied via environment
    // variables so they can differ per deployment (blocker-1 through blocker-7).
    // -----------------------------------------------------------------------
    private static final String S3_BUCKET = System.getenv().getOrDefault(
            "S3_BUCKET_NAME", "mini-app-storage");

    /** S3 object key for the application configuration (replaces /opt/app/config/app.properties) */
    private static final String S3_CONFIG_KEY = System.getenv().getOrDefault(
            "S3_CONFIG_KEY", "config/app.properties");

    /** S3 object key prefix for log entries (replaces /var/log/mini-app.log) */
    private static final String S3_LOG_KEY = System.getenv().getOrDefault(
            "S3_LOG_KEY", "logs/mini-app.log");

    // -----------------------------------------------------------------------
    // SSM parameter names for application configuration (blocker-20)
    // -----------------------------------------------------------------------
    private static final String SSM_PARAM_APP_ENV = System.getenv().getOrDefault(
            "SSM_PARAM_APP_ENV", "/mini-app/config/environment");

    private static final String SSM_PARAM_LOG_LEVEL = System.getenv().getOrDefault(
            "SSM_PARAM_LOG_LEVEL", "/mini-app/config/log-level");

    // -----------------------------------------------------------------------
    // AWS clients
    // -----------------------------------------------------------------------
    private final S3Client s3Client;
    private final SsmClient ssmClient;

    public MiniApp() {
        Region region = Region.of(AWS_REGION);

        // S3 client for file-path-based operations (blocker-1 through blocker-7)
        this.s3Client = S3Client.builder()
                .region(region)
                .build();

        // SSM client with explicit timeouts for port and config lookups
        this.ssmClient = SsmClient.builder()
                .region(region)
                .overrideConfiguration(c -> c
                        .apiCallTimeout(Duration.ofSeconds(10))
                        .apiCallAttemptTimeout(Duration.ofSeconds(5)))
                .build();
    }

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");

        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }

    // -----------------------------------------------------------------------
    // Initialisation
    // -----------------------------------------------------------------------

    private void initializeApplication() {
        loadConfiguration();
        initializeLogging();

        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }

    /**
     * Load application configuration from Amazon S3 (replaces hard-coded
     * /opt/app/config/app.properties — blocker-1, blocker-4) and supplement
     * with values from AWS SSM Parameter Store (blocker-20).
     */
    private void loadConfiguration() {
        // --- S3-based configuration (blocker-1, blocker-4, blocker-20) ---
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(S3_BUCKET)
                    .key(S3_CONFIG_KEY)
                    .build();

            ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getObjectRequest);
            Properties props = new Properties();
            props.load(s3Object);
            System.out.println("Configuration loaded from S3: s3://" + S3_BUCKET + "/" + S3_CONFIG_KEY);

        } catch (IOException e) {
            System.err.println("Failed to load configuration from S3: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("S3 configuration object not found, falling back to SSM Parameter Store: " + e.getMessage());
        }

        // --- SSM Parameter Store configuration (blocker-20) ---
        try {
            String environment = getSsmParameter(SSM_PARAM_APP_ENV);
            String logLevel    = getSsmParameter(SSM_PARAM_LOG_LEVEL);
            System.out.println("Runtime configuration from SSM — environment: " + environment
                    + ", log-level: " + logLevel);
        } catch (Exception e) {
            System.err.println("Failed to load configuration from SSM Parameter Store: " + e.getMessage());
        }
    }

    /**
     * Initialise logging by writing a startup entry to Amazon S3 (replaces
     * hard-coded /var/log/mini-app.log — blocker-2, blocker-3, blocker-5,
     * blocker-6, blocker-7).
     */
    private void initializeLogging() {
        try {
            String logEntry = "Application started at: " + java.time.Instant.now().toString() + "\n";

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(S3_BUCKET)
                    .key(S3_LOG_KEY)
                    .contentType("text/plain")
                    .build();

            s3Client.putObject(putObjectRequest,
                    RequestBody.fromBytes(logEntry.getBytes(StandardCharsets.UTF_8)));

            System.out.println("Logging initialised — log entry written to S3: s3://"
                    + S3_BUCKET + "/" + S3_LOG_KEY);

        } catch (Exception e) {
            System.err.println("Failed to initialise logging in S3: " + e.getMessage());
        }
    }

    /**
     * Start the server on a port resolved from AWS SSM Parameter Store
     * (replaces hard-coded 8080 — blocker-14, blocker-15).
     */
    private void startServer() {
        int serverPort = resolveServerPort();
        try (ServerSocket serverSocket = new ServerSocket(serverPort)) {
            System.out.println("Server started on port: " + serverPort);
            System.out.println("Server ready to accept connections...");

            // Simulate server running
            Thread.sleep(1000);

        } catch (Exception e) {
            System.err.println("Failed to start server: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Resolve the server port from AWS SSM Parameter Store.
     * Falls back to the PORT environment variable, then to 8080 as a last resort.
     * (blocker-14, blocker-15)
     */
    private int resolveServerPort() {
        // 1. Try SSM Parameter Store
        try {
            String portValue = getSsmParameter(SSM_PARAM_SERVER_PORT);
            return Integer.parseInt(portValue.trim());
        } catch (Exception e) {
            System.err.println("Could not resolve port from SSM, checking environment variable: " + e.getMessage());
        }

        // 2. Fall back to PORT environment variable
        String portEnv = System.getenv("PORT");
        if (portEnv != null && !portEnv.isEmpty()) {
            try {
                return Integer.parseInt(portEnv.trim());
            } catch (NumberFormatException nfe) {
                System.err.println("Invalid PORT environment variable value: " + portEnv);
            }
        }

        // 3. Last resort default
        System.err.println("Warning: using default port 8080 — set SSM_PARAM_SERVER_PORT or PORT env var");
        return 8080;
    }

    /**
     * Retrieve a plain-text parameter value from AWS SSM Parameter Store.
     */
    private String getSsmParameter(String parameterName) {
        GetParameterRequest request = GetParameterRequest.builder()
                .name(parameterName)
                .withDecryption(false)
                .build();
        GetParameterResponse response = ssmClient.getParameter(request);
        return response.parameter().value();
    }
}
