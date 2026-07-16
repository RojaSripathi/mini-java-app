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
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;

/**
 * Mini Java Application – cloud-ready version.
 *
 * Fixes applied:
 *   blocker-1,2,3   (cr-java-0061) – Hard-coded file paths replaced with Amazon S3
 *   blocker-4,5,6,7 (cr-java-0063) – java.io.File usage replaced with Amazon S3 SDK v2
 *   blocker-14,15   (cr-java-0077) – Hard-coded ports replaced with env var / SSM Parameter Store
 *   blocker-20      (cr-java-0070) – Classpath properties file replaced with SSM Parameter Store
 */
public class MiniApp {

    // -----------------------------------------------------------------------
    // AWS configuration – resolved from environment variables (12-factor app)
    // -----------------------------------------------------------------------
    private static final String AWS_REGION =
            System.getenv().getOrDefault("AWS_REGION", "us-east-1");

    // -----------------------------------------------------------------------
    // S3 configuration for config and log objects
    // (blocker-1, blocker-2, blocker-3, blocker-4, blocker-5, blocker-6, blocker-7)
    // -----------------------------------------------------------------------
    private static final String S3_BUCKET =
            System.getenv().getOrDefault("APP_S3_BUCKET", "mini-app-storage");
    private static final String S3_CONFIG_KEY =
            System.getenv().getOrDefault("APP_S3_CONFIG_KEY", "config/app.properties");
    private static final String S3_LOG_KEY =
            System.getenv().getOrDefault("APP_S3_LOG_KEY", "logs/mini-app.log");

    // -----------------------------------------------------------------------
    // Server port – resolved from environment variable or SSM Parameter Store
    // (blocker-14, blocker-15 – hard-coded port 8080)
    // -----------------------------------------------------------------------
    private static final String SSM_SERVER_PORT_PARAM =
            System.getenv().getOrDefault("SSM_SERVER_PORT_PARAM", "/mini-app/server/port");

    // -----------------------------------------------------------------------
    // AWS clients
    // -----------------------------------------------------------------------
    private final S3Client s3Client;
    private final SsmClient ssmClient;

    public MiniApp() {
        Region region = Region.of(AWS_REGION);

        this.s3Client = S3Client.builder()
                .region(region)
                .build();

        this.ssmClient = SsmClient.builder()
                .region(region)
                .overrideConfiguration(c -> c
                        .apiCallTimeout(Duration.ofSeconds(10))
                        .apiCallAttemptTimeout(Duration.ofSeconds(5)))
                .build();
    }

    public static void main(String[] args) {
        System.out.println("Starting Mini Java Application...");

        MiniApp app = new MiniApp();
        app.initializeApplication();
        app.startServer();
    }

    private void initializeApplication() {
        loadConfiguration();
        initializeLogging();

        DatabaseService dbService = new DatabaseService();
        dbService.connect();
    }

    // -----------------------------------------------------------------------
    // Configuration loading from Amazon S3
    // (blocker-1, blocker-4 – replaced /opt/app/config/app.properties with S3)
    // (blocker-20 – classpath properties file replaced with SSM Parameter Store)
    // -----------------------------------------------------------------------
    private void loadConfiguration() {
        try {
            System.out.println("Loading configuration from S3: s3://" + S3_BUCKET + "/" + S3_CONFIG_KEY);

            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(S3_BUCKET)
                    .key(S3_CONFIG_KEY)
                    .build();

            try (ResponseInputStream<GetObjectResponse> s3Object =
                         s3Client.getObject(getObjectRequest)) {

                Properties props = new Properties();
                props.load(s3Object);
                System.out.println("Configuration loaded from S3: s3://"
                        + S3_BUCKET + "/" + S3_CONFIG_KEY);

                // Additional runtime parameters can be fetched from SSM Parameter Store
                // (blocker-20 – externalized configuration via AWS SSM)
                loadParameterStoreConfig();
            }

        } catch (IOException e) {
            System.err.println("Failed to load configuration from S3: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Warning: Could not reach S3 for configuration, "
                    + "falling back to environment variables. Reason: " + e.getMessage());
            loadParameterStoreConfig();
        }
    }

    /**
     * Loads runtime configuration from AWS Systems Manager Parameter Store.
     * (blocker-20 – cr-java-0070: classpath properties replaced with SSM Parameter Store)
     */
    private void loadParameterStoreConfig() {
        try {
            String serverPort = getSsmParameter(SSM_SERVER_PORT_PARAM);
            System.out.println("Server port resolved from SSM Parameter Store: " + serverPort);
        } catch (Exception e) {
            System.out.println("SSM Parameter Store unavailable, using environment variables: "
                    + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Logging initialisation using Amazon S3
    // (blocker-2, blocker-3, blocker-5, blocker-6, blocker-7
    //  – replaced /var/log/mini-app.log with S3 object)
    // -----------------------------------------------------------------------
    private void initializeLogging() {
        try {
            String logInitEntry = "Log initialised at: " + java.time.Instant.now() + "\n";

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(S3_BUCKET)
                    .key(S3_LOG_KEY)
                    .contentType("text/plain")
                    .build();

            s3Client.putObject(putObjectRequest,
                    RequestBody.fromBytes(logInitEntry.getBytes(StandardCharsets.UTF_8)));

            System.out.println("Logging initialised in S3: s3://" + S3_BUCKET + "/" + S3_LOG_KEY);

        } catch (Exception e) {
            System.err.println("Warning: Could not initialise S3 log object: " + e.getMessage());
            System.out.println("Falling back to stdout logging.");
        }
    }

    // -----------------------------------------------------------------------
    // Server startup – port resolved from SSM Parameter Store / env var
    // (blocker-14, blocker-15 – hard-coded port 8080 replaced)
    // -----------------------------------------------------------------------
    private void startServer() {
        int serverPort = resolveServerPort();
        try {
            ServerSocket serverSocket = new ServerSocket(serverPort);
            System.out.println("Server started on port: " + serverPort);
            System.out.println("Server ready to accept connections...");

            // Simulate server running
            Thread.sleep(1000);
            serverSocket.close();

        } catch (Exception e) {
            System.err.println("Failed to start server: " + e.getMessage());
        }
    }

    /**
     * Resolves the server port using the following priority:
     * 1. AWS SSM Parameter Store (preferred in cloud)
     * 2. SERVER_PORT environment variable
     * 3. Default value 8080
     *
     * (blocker-14, blocker-15 – cr-java-0077: hard-coded port replaced with SSM / env var)
     */
    private int resolveServerPort() {
        // 1. Try SSM Parameter Store
        try {
            String portStr = getSsmParameter(SSM_SERVER_PORT_PARAM);
            int port = Integer.parseInt(portStr.trim());
            System.out.println("Server port resolved from SSM Parameter Store: " + port);
            return port;
        } catch (Exception e) {
            System.out.println("SSM Parameter Store unavailable for port resolution: " + e.getMessage());
        }

        // 2. Fall back to environment variable
        String envPort = System.getenv("SERVER_PORT");
        if (envPort != null && !envPort.isEmpty()) {
            try {
                int port = Integer.parseInt(envPort.trim());
                System.out.println("Server port resolved from environment variable SERVER_PORT: " + port);
                return port;
            } catch (NumberFormatException e) {
                System.err.println("Invalid SERVER_PORT env var value: " + envPort);
            }
        }

        // 3. Default
        System.out.println("Using default server port: 8080");
        return 8080;
    }

    // -----------------------------------------------------------------------
    // Helper – SSM Parameter Store lookup
    // -----------------------------------------------------------------------
    private String getSsmParameter(String paramName) {
        GetParameterRequest request = GetParameterRequest.builder()
                .name(paramName)
                .withDecryption(true)
                .build();
        GetParameterResponse response = ssmClient.getParameter(request);
        return response.parameter().value();
    }
}
