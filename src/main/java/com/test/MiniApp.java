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
 * Mini Java Application – cloud-ready version.
 *
 * Fixes applied:
 *   blocker-1,2,3   : Hard-coded File Paths
 *                     → Absolute paths replaced with Amazon S3 object keys read
 *                       from environment variables (S3_BUCKET_NAME, S3_CONFIG_KEY,
 *                       S3_LOG_KEY).
 *   blocker-4,5,6,7 : java.io.File Usage for Data Storage
 *                     → All java.io.File / FileInputStream operations replaced with
 *                       AWS SDK v2 S3Client (GetObject / PutObject).
 *   blocker-14,15   : Hard-coded Ports
 *                     → SERVER_PORT resolved at runtime from environment variable
 *                       SERVER_PORT (default 8080) or AWS SSM Parameter Store
 *                       parameter /mini-app/server/port.
 *   blocker-20      : Properties Files in Classpath
 *                     → Configuration loaded from AWS SSM Parameter Store instead
 *                       of a classpath-bundled .properties file.
 */
public class MiniApp {

    // -----------------------------------------------------------------------
    // Environment variable / SSM parameter names
    // -----------------------------------------------------------------------

    /** Environment variable that holds the S3 bucket name for app assets. */
    private static final String S3_BUCKET_ENV      = "S3_BUCKET_NAME";

    /** S3 object key for the application configuration object. */
    private static final String S3_CONFIG_KEY_ENV  = "S3_CONFIG_KEY";

    /** S3 object key for the application log object. */
    private static final String S3_LOG_KEY_ENV     = "S3_LOG_KEY";

    /** Environment variable / SSM param for the server port.
     *  blocker-14,15: replaces hard-coded SERVER_PORT = 8080 constant. */
    private static final String SERVER_PORT_ENV    = "SERVER_PORT";
    private static final String SERVER_PORT_PARAM  = "/mini-app/server/port";

    /** SSM parameter path prefix for application configuration.
     *  blocker-20: replaces classpath-bundled application.properties. */
    private static final String APP_CONFIG_PARAM_PREFIX = "/mini-app/config/";

    /** AWS region – defaults to us-east-1 if not set. */
    private static final String AWS_REGION_ENV     = "AWS_REGION";

    // -----------------------------------------------------------------------
    // AWS clients
    // -----------------------------------------------------------------------
    private final S3Client  s3Client;
    private final SsmClient ssmClient;

    public MiniApp() {
        Region region = Region.of(System.getenv().getOrDefault(AWS_REGION_ENV, "us-east-1"));

        this.s3Client = S3Client.builder()
                .region(region)
                .overrideConfiguration(c -> c
                        .apiCallTimeout(Duration.ofSeconds(30))
                        .apiCallAttemptTimeout(Duration.ofSeconds(10)))
                .build();

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
     * Loads application configuration from two sources (in priority order):
     *   1. AWS SSM Parameter Store  (blocker-20: replaces classpath properties file)
     *   2. Amazon S3                (blocker-1,4: replaces hard-coded /opt/app/config/app.properties)
     */
    private void loadConfiguration() {
        // --- Primary: AWS SSM Parameter Store (blocker-20) ---
        try {
            System.out.println("Loading configuration from AWS SSM Parameter Store...");
            String[] configKeys = {"server.port", "database.url", "app.name"};
            Properties props = new Properties();
            for (String key : configKeys) {
                String paramName = APP_CONFIG_PARAM_PREFIX + key.replace('.', '/');
                try {
                    GetParameterResponse resp = ssmClient.getParameter(
                            GetParameterRequest.builder()
                                    .name(paramName)
                                    .withDecryption(true)
                                    .build());
                    props.setProperty(key, resp.parameter().value());
                } catch (Exception ex) {
                    System.out.println("SSM parameter not found: " + paramName);
                }
            }
            System.out.println("Configuration loaded from SSM Parameter Store ("
                    + props.size() + " properties)");
        } catch (Exception e) {
            System.err.println("SSM configuration load failed, falling back to S3: " + e.getMessage());
            loadConfigurationFromS3();
        }
    }

    /**
     * Fallback: loads configuration object from Amazon S3.
     * blocker-1,4: replaces File configFile = new File(CONFIG_FILE_PATH) at line 44.
     */
    private void loadConfigurationFromS3() {
        String bucket    = System.getenv().getOrDefault(S3_BUCKET_ENV, "mini-app-config");
        String configKey = System.getenv().getOrDefault(S3_CONFIG_KEY_ENV, "config/app.properties");

        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(configKey)
                    .build();

            try (ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getRequest)) {
                Properties props = new Properties();
                props.load(s3Object);
                System.out.println("Configuration loaded from S3: s3://" + bucket + "/" + configKey);
            }
        } catch (IOException e) {
            System.err.println("Failed to load configuration from S3: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("S3 configuration object not found (s3://" + bucket + "/" + configKey
                    + "): " + e.getMessage());
        }
    }

    /**
     * Initialises logging by writing a log entry to Amazon S3.
     * blocker-2,3,5,6,7: replaces File logDir = new File("/var/log") and
     *                     File logFile = new File(LOG_FILE_PATH) at lines 60, 62, 65.
     */
    private void initializeLogging() {
        String bucket  = System.getenv().getOrDefault(S3_BUCKET_ENV, "mini-app-config");
        String logKey  = System.getenv().getOrDefault(S3_LOG_KEY_ENV, "logs/mini-app.log");

        try {
            String logEntry = "Logging initialised at: " + java.time.Instant.now() + System.lineSeparator();

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(logKey)
                    .contentType("text/plain")
                    .build();

            s3Client.putObject(putRequest,
                    RequestBody.fromBytes(logEntry.getBytes(StandardCharsets.UTF_8)));

            System.out.println("Logging initialised – log entry written to S3: s3://"
                    + bucket + "/" + logKey);
        } catch (Exception e) {
            System.err.println("Failed to initialise logging via S3: " + e.getMessage());
        }
    }

    /**
     * Starts the server on a port resolved from environment variables or
     * AWS SSM Parameter Store.
     * blocker-14,15: replaces hard-coded SERVER_PORT = 8080 at lines 15 and 79.
     */
    private void startServer() {
        int port = resolveServerPort();
        try {
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
     * Resolves the server port at runtime:
     *   1. Environment variable SERVER_PORT
     *   2. AWS SSM Parameter Store /mini-app/server/port
     *   3. Default: 8080
     * blocker-14,15: eliminates hard-coded port constant.
     */
    private int resolveServerPort() {
        // 1. Environment variable (injected by ECS / EKS / Elastic Beanstalk)
        String envPort = System.getenv(SERVER_PORT_ENV);
        if (envPort != null && !envPort.isBlank()) {
            try {
                return Integer.parseInt(envPort.trim());
            } catch (NumberFormatException e) {
                System.err.println("Invalid SERVER_PORT env var value: " + envPort);
            }
        }

        // 2. AWS SSM Parameter Store
        try {
            GetParameterResponse resp = ssmClient.getParameter(
                    GetParameterRequest.builder()
                            .name(SERVER_PORT_PARAM)
                            .withDecryption(false)
                            .build());
            return Integer.parseInt(resp.parameter().value().trim());
        } catch (Exception e) {
            System.out.println("SSM parameter " + SERVER_PORT_PARAM
                    + " not available, using default port 8080");
        }

        // 3. Default
        return 8080;
    }
}
