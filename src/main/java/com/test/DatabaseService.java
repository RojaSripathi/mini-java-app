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
 * Database service — cloud-ready version.
 *
 * Credentials (username / password) are retrieved from AWS Secrets Manager.
 * Port and host configuration are retrieved from AWS Systems Manager Parameter Store.
 * Raw JDBC DriverManager is replaced with HikariCP connection pool (with explicit timeouts).
 */
public class DatabaseService {

    // -----------------------------------------------------------------------
    // Configuration keys — resolved at runtime from AWS SSM Parameter Store
    // and AWS Secrets Manager instead of being hard-coded.
    // -----------------------------------------------------------------------

    /** SSM parameter name that holds the DB host */
    private static final String SSM_DB_HOST   = System.getenv().getOrDefault(
            "SSM_PARAM_DB_HOST",   "/mini-app/db/host");

    /** SSM parameter name that holds the DB port (blocker-11, blocker-12, blocker-13) */
    private static final String SSM_DB_PORT   = System.getenv().getOrDefault(
            "SSM_PARAM_DB_PORT",   "/mini-app/db/port");

    /** SSM parameter name that holds the DB name */
    private static final String SSM_DB_NAME   = System.getenv().getOrDefault(
            "SSM_PARAM_DB_NAME",   "/mini-app/db/name");

    /** Secrets Manager secret name that holds DB credentials (blocker-8, blocker-9, blocker-10, blocker-16) */
    private static final String SECRET_DB_CREDS = System.getenv().getOrDefault(
            "SECRET_DB_CREDENTIALS", "mini-app/db/credentials");

    /** SSM parameter name that holds the Redis host */
    private static final String SSM_REDIS_HOST = System.getenv().getOrDefault(
            "SSM_PARAM_REDIS_HOST", "/mini-app/cache/host");

    /** SSM parameter name that holds the Redis port (blocker-11, blocker-12, blocker-13) */
    private static final String SSM_REDIS_PORT = System.getenv().getOrDefault(
            "SSM_PARAM_REDIS_PORT", "/mini-app/cache/port");

    /** SSM parameter name that holds the external API URL */
    private static final String SSM_EXTERNAL_API_URL = System.getenv().getOrDefault(
            "SSM_PARAM_EXTERNAL_API_URL", "/mini-app/external/api-url");

    /** SSM parameter name that holds the payment service URL */
    private static final String SSM_PAYMENT_SERVICE_URL = System.getenv().getOrDefault(
            "SSM_PARAM_PAYMENT_SERVICE_URL", "/mini-app/external/payment-url");

    // AWS region — injected via environment variable
    private static final String AWS_REGION = System.getenv().getOrDefault("AWS_REGION", "us-east-1");

    // -----------------------------------------------------------------------
    // HikariCP data source (replaces raw DriverManager — blocker-17, blocker-18)
    // -----------------------------------------------------------------------
    private HikariDataSource dataSource;

    // -----------------------------------------------------------------------
    // AWS clients
    // -----------------------------------------------------------------------
    private final SsmClient ssmClient;
    private final SecretsManagerClient secretsManagerClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DatabaseService() {
        Region region = Region.of(AWS_REGION);

        // Build SSM client with explicit connection/API-call timeouts (blocker-19)
        this.ssmClient = SsmClient.builder()
                .region(region)
                .overrideConfiguration(c -> c
                        .apiCallTimeout(Duration.ofSeconds(10))
                        .apiCallAttemptTimeout(Duration.ofSeconds(5)))
                .build();

        // Build Secrets Manager client with explicit timeouts (blocker-19)
        this.secretsManagerClient = SecretsManagerClient.builder()
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
            System.out.println("Retrieving database configuration from AWS SSM Parameter Store and Secrets Manager...");

            // Resolve host, port, and DB name from SSM Parameter Store
            String dbHost = getSsmParameter(SSM_DB_HOST);
            String dbPort = getSsmParameter(SSM_DB_PORT);   // blocker-11, blocker-12, blocker-13
            String dbName = getSsmParameter(SSM_DB_NAME);

            // Resolve credentials from AWS Secrets Manager (blocker-8, blocker-9, blocker-10, blocker-16)
            String[] credentials = getDbCredentials(SECRET_DB_CREDS);
            String dbUsername = credentials[0];
            String dbPassword = credentials[1];

            String dbUrl = "jdbc:mysql://" + dbHost + ":" + dbPort + "/" + dbName;

            // Build HikariCP pool — replaces raw DriverManager (blocker-17, blocker-18)
            // Explicit connection/socket timeouts address blocker-19
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(dbUrl);
            hikariConfig.setUsername(dbUsername);
            hikariConfig.setPassword(dbPassword);
            hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");

            // Connection pool sizing
            hikariConfig.setMaximumPoolSize(10);
            hikariConfig.setMinimumIdle(2);

            // Timeout configuration (blocker-19)
            hikariConfig.setConnectionTimeout(30_000);      // 30 s — max wait for a connection from pool
            hikariConfig.setIdleTimeout(600_000);           // 10 min — idle connection eviction
            hikariConfig.setMaxLifetime(1_800_000);         // 30 min — max connection lifetime
            hikariConfig.setKeepaliveTime(60_000);          // 1 min — keepalive ping
            hikariConfig.addDataSourceProperty("connectTimeout", "10000");   // 10 s TCP connect
            hikariConfig.addDataSourceProperty("socketTimeout", "30000");    // 30 s socket read

            this.dataSource = new HikariDataSource(hikariConfig);

            System.out.println("HikariCP connection pool initialised for: " + dbHost + ":" + dbPort + "/" + dbName);

            // Resolve cache and external service config from SSM
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
            System.out.println("HikariCP connection pool closed");
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Retrieve a plain-text parameter value from AWS SSM Parameter Store.
     * Port numbers and non-sensitive configuration are stored here
     * (blocker-11, blocker-12, blocker-13, blocker-14, blocker-15).
     */
    private String getSsmParameter(String parameterName) {
        GetParameterRequest request = GetParameterRequest.builder()
                .name(parameterName)
                .withDecryption(false)
                .build();
        GetParameterResponse response = ssmClient.getParameter(request);
        return response.parameter().value();
    }

    /**
     * Retrieve database credentials from AWS Secrets Manager.
     * The secret is expected to be a JSON object with "username" and "password" keys.
     * (blocker-8, blocker-9, blocker-10, blocker-16)
     *
     * @return String array where [0] = username, [1] = password
     */
    private String[] getDbCredentials(String secretName) {
        try {
            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build();
            GetSecretValueResponse response = secretsManagerClient.getSecretValue(request);
            String secretJson = response.secretString();

            JsonNode secretNode = objectMapper.readTree(secretJson);
            String username = secretNode.get("username").asText();
            String password = secretNode.get("password").asText();
            return new String[]{username, password};

        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve DB credentials from Secrets Manager: " + e.getMessage(), e);
        }
    }

    private void connectToCache() {
        // Redis host and port resolved from SSM Parameter Store (blocker-11, blocker-12, blocker-13)
        String redisHost = getSsmParameter(SSM_REDIS_HOST);
        String redisPort = getSsmParameter(SSM_REDIS_PORT);
        System.out.println("Connecting to Redis cache at: " + redisHost + ":" + redisPort);
        // Actual Redis client initialisation would go here
    }

    private void initializeExternalServices() {
        // External service URLs resolved from SSM Parameter Store
        String externalApiUrl     = getSsmParameter(SSM_EXTERNAL_API_URL);
        String paymentServiceUrl  = getSsmParameter(SSM_PAYMENT_SERVICE_URL);
        System.out.println("Initializing external API: " + externalApiUrl);
        System.out.println("Initializing payment service: " + paymentServiceUrl);
    }
}
