package com.test;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Mini Java Application – cloud-ready version.
 *
 * Fixes applied:
 *  - cr-java-0061 (blockers 1,2,3):  Hard-coded absolute file paths removed; S3 used instead.
 *  - cr-java-0063 (blockers 4,5,6,7): java.io.File operations replaced with Amazon S3 (AWS SDK v2).
 *  - cr-java-0077 (blockers 14,15):  Hard-coded port replaced with environment variable / SSM Parameter Store.
 *  - cr-java-0070 (blocker 20):      Classpath properties file replaced with AWS SSM Parameter Store.
 */
public class MiniApp {

    // -----------------------------------------------------------------------
    // AWS region – injected via environment variable
    // -----------------------------------------------------------------------
    private static final String AWS_REGION =
            System.getenv().getOrDefault("AWS_REGION", "us-east-1");

    // -----------------------------------------------------------------------
    // Server port – injected via environment variable (cr-java-0077, blocker 14)
    // Falls back to SSM Parameter Store; final fallback is 8080.
    // -----------------------------------------------------------------------
    private static final String SERVER_PORT_ENV = System.getenv("SERVER_PORT");
    private static final String SERVER_PORT_PARAM =
            System.getenv().getOrDefault("SERVER_PORT_PARAM", "/mini-app/server/port");

    // -----------------------------------------------------------------------
    // S3 configuration – injected via environment variables (cr-java-0061 / cr-java-0063)
    // Replaces hard-coded paths: /opt/app/config/app.properties and /var/log/mini-app.log
    // -----------------------------------------------------------------------
    private static final String S3_BUCKET =
            System.getenv().getOrDefault("APP_S3_BUCKET", "mini-app-storage");
    private static final String S3_CONFIG_KEY =
            System.getenv().getOrDefault("APP_CONFIG_S3_KEY", "config/app.properties");
    private static final String S3_LOG_KEY =
            System.getenv().getOrDefault("APP_LOG_S3_KEY", "logs/mini-app.log");

    // -----------------------------------------------------------------------
    // SSM Parameter Store prefix for application configuration (cr-java-0070, blocker 20)
    // -----------------------------------------------------------------------
    private static final String SSM_CONFIG_PREFIX =
            System.getenv().getOrDefault("SSM_CONFIG_PREFIX", "/mini-app/config");

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
    // Configuration loading from AWS SSM Parameter Store (cr-java-0070, blocker 20)
    // and Amazon S3 (cr-java-0061 / cr-java-0063, blockers 1,4)
    // -----------------------------------------------------------------------
    private void loadConfiguration() {
        // Primary: load configuration from AWS SSM Parameter Store
        try {
            Properties props = loadPropertiesFromSsm();
            System.out.println("Configuration loaded from AWS SSM Parameter Store prefix: "
                    + SSM_CONFIG_PREFIX);

            // Secondary: also attempt to load supplementary config object from S3
            // (replaces hard-coded path /opt/app/config/app.properties)
            loadConfigFromS3(props);

        } catch (Exception e) {
            System.err.println("Failed to load configuration: " + e.getMessage());
        }
    }

    /**
     * Loads application configuration from AWS Systems Manager Parameter Store.
     * Replaces classpath-bundled properties file (cr-java-0070).
     */
    private Properties loadPropertiesFromSsm() {
        Properties props = new Properties();
        try {
            // Example: retrieve individual parameters under the config prefix.
            // In production, use GetParametersByPath for bulk retrieval.
            String[] paramNames = {
                "server.port", "database.url", "cache.redis.host"
            };
            for (String paramName : paramNames) {
                try {
                    GetParameterRequest request = GetParameterRequest.builder()
                            .name(SSM_CONFIG_PREFIX + "/" + paramName)
                            .withDecryption(true)
                            .build();
                    GetParameterResponse response = ssmClient.getParameter(request);
                    props.setProperty(paramName, response.parameter().value());
                } catch (Exception ignored) {
                    // Parameter may not exist for every key; continue gracefully
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not load SSM parameters: " + e.getMessage());
        }
        return props;
    }

    /**
     * Loads supplementary configuration from Amazon S3.
     * Replaces java.io.File read from hard-coded path /opt/app/config/app.properties
     * (cr-java-0061 blocker 1, cr-java-0063 blocker 4).
     */
    private void loadConfigFromS3(Properties existingProps) {
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(S3_BUCKET)
                    .key(S3_CONFIG_KEY)
                    .build();

            try (ResponseInputStream<GetObjectResponse> s3Object =
                         s3Client.getObject(getRequest)) {
                Properties s3Props = new Properties();
                s3Props.load(s3Object);
                // Merge S3 properties (SSM values take precedence)
                s3Props.forEach((k, v) -> existingProps.putIfAbsent(k, v));
                System.out.println("Supplementary configuration loaded from S3: s3://"
                        + S3_BUCKET + "/" + S3_CONFIG_KEY);
            }
        } catch (NoSuchKeyException e) {
            System.out.println("No supplementary config object found in S3 at key: "
                    + S3_CONFIG_KEY);
        } catch (IOException e) {
            System.err.println("Failed to parse S3 config object: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Warning: Could not load config from S3: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Logging initialisation using Amazon S3 (cr-java-0061 blockers 2,3 /
    // cr-java-0063 blockers 5,6,7)
    // Replaces java.io.File operations on /var/log and /var/log/mini-app.log
    // -----------------------------------------------------------------------
    private void initializeLogging() {
        try {
            // Write an initialisation marker object to S3 instead of creating
            // a local log file at a hard-coded path.
            String logInitContent = "Log initialised at: "
                    + java.time.Instant.now().toString() + "\n";

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(S3_BUCKET)
                    .key(S3_LOG_KEY)
                    .contentType("text/plain")
                    .build();

            s3Client.putObject(putRequest,
                    RequestBody.fromString(logInitContent, StandardCharsets.UTF_8));

            System.out.println("Logging initialised. Log object stored at: s3://"
                    + S3_BUCKET + "/" + S3_LOG_KEY);

        } catch (Exception e) {
            System.err.println("Failed to initialise logging in S3: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Server startup – port resolved from environment variable or SSM
    // (cr-java-0077, blockers 14 and 15)
    // -----------------------------------------------------------------------
    private void startServer() {
        int port = resolveServerPort();
        try {
            // BLOCKER 15 (line 79): port now resolved dynamically, not hard-coded
            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("Server started on port: " + port);
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
     * 1. SERVER_PORT environment variable (injected by ECS/EKS/Elastic Beanstalk)
     * 2. AWS SSM Parameter Store value at SERVER_PORT_PARAM
     * 3. Default value 8080
     *
     * Replaces hard-coded SERVER_PORT = 8080 (cr-java-0077, blocker 14 line 15).
     */
    private int resolveServerPort() {
        // 1. Environment variable
        if (SERVER_PORT_ENV != null && !SERVER_PORT_ENV.isEmpty()) {
            try {
                return Integer.parseInt(SERVER_PORT_ENV);
            } catch (NumberFormatException e) {
                System.err.println("Invalid SERVER_PORT env var value: " + SERVER_PORT_ENV);
            }
        }
        // 2. SSM Parameter Store
        try {
            GetParameterRequest request = GetParameterRequest.builder()
                    .name(SERVER_PORT_PARAM)
                    .withDecryption(false)
                    .build();
            GetParameterResponse response = ssmClient.getParameter(request);
            return Integer.parseInt(response.parameter().value());
        } catch (Exception e) {
            System.out.println("Could not retrieve server port from SSM ("
                    + SERVER_PORT_PARAM + "), using default 8080.");
        }
        // 3. Default
        return 8080;
    }
}
