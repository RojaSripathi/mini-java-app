package com.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;

/**
 * Database service using HikariCP connection pooling, AWS Secrets Manager for
 * credentials, and AWS Systems Manager Parameter Store for port/host configuration.
 *
 * Fixes applied:
 *   blocker-8,9,10  (cr-java-0069) – Hard-coded DB credentials replaced with AWS Secrets Manager
 *   blocker-11,12,13 (cr-java-0077) – Hard-coded ports replaced with AWS SSM Parameter Store / env vars
 *   blocker-16      (cr-java-0113) – Lack of externalized secrets → AWS Secrets Manager
 *   blocker-17,18   (cr-java-0073) – Direct JDBC replaced with HikariCP connection pool
 *   blocker-19      (cr-java-0097) – Missing connection timeouts added to HikariCP config
 */
public class DatabaseService {

    // -----------------------------------------------------------------------
    // Configuration keys – resolved at runtime from environment variables,
    // AWS SSM Parameter Store, and AWS Secrets Manager.
    // -----------------------------------------------------------------------

    /** SSM parameter name that holds the DB host (injected via env or SSM). */
    private static final String SSM_DB_HOST_PARAM =
            System.getenv().getOrDefault("SSM_DB_HOST_PARAM", "/mini-app/db/host");

    /** SSM parameter name that holds the DB port (blocker-11, blocker-12, blocker-13). */
    private static final String SSM_DB_PORT_PARAM =
            System.getenv().getOrDefault("SSM_DB_PORT_PARAM", "/mini-app/db/port");

    /** SSM parameter name that holds the DB name. */
    private static final String SSM_DB_NAME_PARAM =
            System.getenv().getOrDefault("SSM_DB_NAME_PARAM", "/mini-app/db/name");

    /**
     * AWS Secrets Manager secret name that stores a JSON object with keys
     * "username" and "password" (blocker-8, blocker-9, blocker-10, blocker-16).
     */
    private static final String SECRET_NAME =
            System.getenv().getOrDefault("DB_SECRET_NAME", "mini-app/db/credentials");

    /** AWS region – injected via environment variable (12-factor). */
    private static final String AWS_REGION =
            System.getenv().getOrDefault("AWS_REGION", "us-east-1");

    // -----------------------------------------------------------------------
    // Cache / external service configuration – read from environment variables
    // (blocker-11 / blocker-12 for REDIS_PORT).
    // -----------------------------------------------------------------------
    private static final String REDIS_HOST =
            System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1");
    private static final int REDIS_PORT =
            Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));

    private static final String EXTERNAL_API_URL =
            System.getenv().getOrDefault("EXTERNAL_API_URL", "http://api.example.com/v1");
    private static final String PAYMENT_SERVICE_URL =
            System.getenv().getOrDefault("PAYMENT_SERVICE_URL", "https://payment.internal.company.com/process");

    // -----------------------------------------------------------------------
    // HikariCP DataSource (replaces raw DriverManager – blocker-17, blocker-18)
    // -----------------------------------------------------------------------
    private HikariDataSource dataSource;

    // -----------------------------------------------------------------------
    // AWS clients
    // -----------------------------------------------------------------------
    private final SecretsManagerClient secretsManagerClient;
    private final SsmClient ssmClient;

    public DatabaseService() {
        Region region = Region.of(AWS_REGION);

        // Build Secrets Manager client with explicit connection/API-call timeouts
        // (blocker-19 – missing connection timeouts)
        this.secretsManagerClient = SecretsManagerClient.builder()
                .region(region)
                .overrideConfiguration(c -> c
                        .apiCallTimeout(Duration.ofSeconds(10))
                        .apiCallAttemptTimeout(Duration.ofSeconds(5)))
                .build();

        // Build SSM client with explicit timeouts (blocker-19)
        this.ssmClient = SsmClient.builder()
                .region(region)
                .overrideConfiguration(c -> c
                        .apiCallTimeout(Duration.ofSeconds(10))
                        .apiCallAttemptTimeout(Duration.ofSeconds(5)))
                .build();
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    public void connect() {
        try {
            System.out.println("Resolving database configuration from AWS Parameter Store and Secrets Manager...");

            // Resolve host, port, and DB name from SSM Parameter Store
            // (blocker-11, blocker-12, blocker-13 – hard-coded ports)
            String dbHost = getSsmParameter(SSM_DB_HOST_PARAM);
            String dbPort = getSsmParameter(SSM_DB_PORT_PARAM);
            String dbName = getSsmParameter(SSM_DB_NAME_PARAM);

            // Resolve credentials from Secrets Manager
            // (blocker-8, blocker-9, blocker-10, blocker-16)
            String[] credentials = getDbCredentials();
            String dbUsername = credentials[0];
            String dbPassword = credentials[1];

            String dbUrl = "jdbc:mysql://" + dbHost + ":" + dbPort + "/" + dbName;

            // Build HikariCP pool (blocker-17, blocker-18 – direct JDBC replaced)
            // with explicit timeouts (blocker-19 – missing connection timeouts)
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(dbUrl);
            hikariConfig.setUsername(dbUsername);
            hikariConfig.setPassword(dbPassword);
            hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");

            // Connection pool sizing
            hikariConfig.setMaximumPoolSize(20);
            hikariConfig.setMinimumIdle(5);

            // Timeout configuration (blocker-19 – missing connection timeouts)
            hikariConfig.setConnectionTimeout(30_000);      // 30 s – max wait for a connection from pool
            hikariConfig.setIdleTimeout(600_000);           // 10 min – idle connection eviction
            hikariConfig.setMaxLifetime(1_800_000);         // 30 min – max connection lifetime
            hikariConfig.setKeepaliveTime(60_000);          // 1 min – keepalive ping
            hikariConfig.setInitializationFailTimeout(10_000); // 10 s – fail fast on startup

            // Validation query with timeout
            hikariConfig.setConnectionTestQuery("SELECT 1");

            dataSource = new HikariDataSource(hikariConfig);

            System.out.println("HikariCP connection pool initialised for: " + dbUrl);
            System.out.println("Using username resolved from AWS Secrets Manager.");

            connectToCache();
            initializeExternalServices();

        } catch (Exception e) {
            System.err.println("Database initialisation failed: " + e.getMessage());
        }
    }

    public void executeQuery(String sql) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setQueryTimeout(30);
            System.out.println("Executing query: " + sql);
            stmt.execute();

        } catch (SQLException e) {
            System.err.println("Query execution failed: " + e.getMessage());
        }
    }

    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("HikariCP connection pool closed.");
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Retrieves a plain-text parameter value from AWS Systems Manager Parameter Store.
     * Falls back to an environment variable of the same name if SSM is unavailable
     * (useful for local development).
     */
    private String getSsmParameter(String paramName) {
        try {
            GetParameterRequest request = GetParameterRequest.builder()
                    .name(paramName)
                    .withDecryption(true)
                    .build();
            GetParameterResponse response = ssmClient.getParameter(request);
            return response.parameter().value();
        } catch (Exception e) {
            // Fallback: read from environment variable derived from the parameter name
            String envKey = paramName.replaceAll("[^A-Za-z0-9]", "_").toUpperCase();
            String envValue = System.getenv(envKey);
            if (envValue != null && !envValue.isEmpty()) {
                return envValue;
            }
            throw new RuntimeException("Cannot resolve SSM parameter '" + paramName
                    + "' and no fallback env var found: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves database credentials from AWS Secrets Manager.
     * The secret is expected to be a JSON object: {"username":"...","password":"..."}
     *
     * @return String array where [0] = username, [1] = password
     */
    private String[] getDbCredentials() {
        try {
            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(SECRET_NAME)
                    .build();
            GetSecretValueResponse response = secretsManagerClient.getSecretValue(request);
            String secretJson = response.secretString();

            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(secretJson);
            String username = node.get("username").asText();
            String password = node.get("password").asText();
            return new String[]{username, password};

        } catch (Exception e) {
            // Fallback to environment variables for local development
            String username = System.getenv("DB_USERNAME");
            String password = System.getenv("DB_PASSWORD");
            if (username != null && password != null) {
                System.out.println("Warning: Using DB credentials from environment variables (fallback).");
                return new String[]{username, password};
            }
            throw new RuntimeException("Cannot resolve DB credentials from Secrets Manager: " + e.getMessage(), e);
        }
    }

    private void connectToCache() {
        System.out.println("Connecting to Redis cache at: " + REDIS_HOST + ":" + REDIS_PORT);
        // Cache connection logic here
    }

    private void initializeExternalServices() {
        System.out.println("Initializing external API: " + EXTERNAL_API_URL);
        System.out.println("Initializing payment service: " + PAYMENT_SERVICE_URL);
    }
}
