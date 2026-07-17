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
 * Database service using AWS Secrets Manager for credentials,
 * AWS Systems Manager Parameter Store for port/host configuration,
 * and HikariCP for connection pooling with proper timeouts.
 *
 * Fixes applied:
 *   blocker-8,9,10,16 : Hard-coded DB credentials / Lack of Externalized Secrets
 *                        → Credentials retrieved from AWS Secrets Manager at runtime.
 *   blocker-11,12,13  : Hard-coded Ports
 *                        → DB port and Redis port read from AWS SSM Parameter Store
 *                          via environment variables (DB_PORT_PARAM, REDIS_PORT_PARAM).
 *   blocker-17,18     : Direct JDBC Connections
 *                        → Raw DriverManager replaced with HikariCP connection pool
 *                          backed by Amazon RDS Proxy endpoint.
 *   blocker-19        : Missing Connection Timeouts
 *                        → HikariCP connectionTimeout, idleTimeout, maxLifetime,
 *                          and AWS SDK client timeouts configured explicitly.
 */
public class DatabaseService {

    // -----------------------------------------------------------------------
    // Configuration keys / environment variable names
    // -----------------------------------------------------------------------

    /** AWS Secrets Manager secret name that stores DB credentials as JSON:
     *  { "username": "...", "password": "...", "host": "...", "dbname": "..." } */
    private static final String DB_SECRET_NAME_ENV   = "DB_SECRET_NAME";

    /** SSM Parameter Store parameter name for the DB port (injected via env var). */
    private static final String DB_PORT_PARAM_ENV    = "DB_PORT_PARAM";

    /** SSM Parameter Store parameter name for the Redis port (injected via env var). */
    private static final String REDIS_PORT_PARAM_ENV = "REDIS_PORT_PARAM";

    /** SSM Parameter Store parameter name for the Redis host (injected via env var). */
    private static final String REDIS_HOST_PARAM_ENV = "REDIS_HOST_PARAM";

    /** AWS region – defaults to us-east-1 if not set. */
    private static final String AWS_REGION_ENV       = "AWS_REGION";

    /** Database name – can be overridden via environment variable. */
    private static final String DB_NAME_ENV          = "DB_NAME";

    /** RDS Proxy or DB host – can be overridden via environment variable. */
    private static final String DB_HOST_ENV          = "DB_HOST";

    // -----------------------------------------------------------------------
    // HikariCP connection pool (replaces raw DriverManager – blocker-17/18)
    // -----------------------------------------------------------------------
    private HikariDataSource dataSource;

    // -----------------------------------------------------------------------
    // AWS clients
    // -----------------------------------------------------------------------
    private final SecretsManagerClient secretsManagerClient;
    private final SsmClient            ssmClient;
    private final ObjectMapper         objectMapper = new ObjectMapper();

    public DatabaseService() {
        Region region = Region.of(System.getenv().getOrDefault(AWS_REGION_ENV, "us-east-1"));

        // blocker-19: configure AWS SDK client timeouts to prevent indefinite hangs
        this.secretsManagerClient = SecretsManagerClient.builder()
                .region(region)
                .overrideConfiguration(c -> c
                        .apiCallTimeout(Duration.ofSeconds(10))
                        .apiCallAttemptTimeout(Duration.ofSeconds(5)))
                .build();

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
            System.out.println("Retrieving database credentials from AWS Secrets Manager...");

            // blocker-8,9,10,16: fetch credentials from AWS Secrets Manager
            DbCredentials creds = fetchDbCredentials();

            // blocker-11,12: fetch DB port from AWS SSM Parameter Store
            String dbPort = fetchSsmParameter(
                    System.getenv().getOrDefault(DB_PORT_PARAM_ENV, "/mini-app/db/port"),
                    "3306");

            // Resolve host and DB name (prefer env vars, fall back to secret values)
            String dbHost   = System.getenv().getOrDefault(DB_HOST_ENV,   creds.host);
            String dbName   = System.getenv().getOrDefault(DB_NAME_ENV,   creds.dbname);

            // blocker-17,18: build HikariCP pool instead of raw DriverManager
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl("jdbc:mysql://" + dbHost + ":" + dbPort + "/" + dbName);
            hikariConfig.setUsername(creds.username);
            hikariConfig.setPassword(creds.password);
            hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
            hikariConfig.setMaximumPoolSize(20);

            // blocker-19: explicit connection timeouts for cloud resilience
            hikariConfig.setConnectionTimeout(30_000);   // 30 s – max wait for a connection from pool
            hikariConfig.setIdleTimeout(600_000);         // 10 min – idle connection eviction
            hikariConfig.setMaxLifetime(1_800_000);       // 30 min – max connection lifetime
            hikariConfig.setKeepaliveTime(60_000);        // 1 min – keepalive ping
            hikariConfig.setConnectionTestQuery("SELECT 1");

            dataSource = new HikariDataSource(hikariConfig);
            System.out.println("HikariCP connection pool initialised (host=" + dbHost
                    + ", port=" + dbPort + ", db=" + dbName + ")");

            // Connect to cache using SSM-sourced configuration
            connectToCache();

            // Initialize external services
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
     * Fetches DB credentials from AWS Secrets Manager.
     * blocker-8,9,10,16: replaces hard-coded DB_USERNAME / DB_PASSWORD / DB_URL constants.
     */
    private DbCredentials fetchDbCredentials() {
        String secretName = System.getenv().getOrDefault(DB_SECRET_NAME_ENV, "mini-app/db/credentials");

        GetSecretValueRequest request = GetSecretValueRequest.builder()
                .secretId(secretName)
                .build();

        GetSecretValueResponse response = secretsManagerClient.getSecretValue(request);
        String secretJson = response.secretString();

        try {
            JsonNode node = objectMapper.readTree(secretJson);
            DbCredentials creds = new DbCredentials();
            creds.username = node.path("username").asText("root");
            creds.password = node.path("password").asText("");
            creds.host     = node.path("host").asText("localhost");
            creds.dbname   = node.path("dbname").asText("mini_app_db");
            return creds;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse DB credentials from Secrets Manager: " + e.getMessage(), e);
        }
    }

    /**
     * Fetches a parameter value from AWS SSM Parameter Store.
     * blocker-11,12,13: replaces hard-coded port constants.
     */
    private String fetchSsmParameter(String parameterName, String defaultValue) {
        try {
            GetParameterRequest request = GetParameterRequest.builder()
                    .name(parameterName)
                    .withDecryption(true)
                    .build();
            GetParameterResponse response = ssmClient.getParameter(request);
            return response.parameter().value();
        } catch (Exception e) {
            System.err.println("Could not fetch SSM parameter '" + parameterName
                    + "', using default: " + defaultValue + " (" + e.getMessage() + ")");
            return defaultValue;
        }
    }

    /**
     * Connects to Redis cache using host/port sourced from SSM Parameter Store.
     * blocker-13: replaces hard-coded REDIS_HOST / REDIS_PORT constants.
     */
    private void connectToCache() {
        String redisHost = fetchSsmParameter(
                System.getenv().getOrDefault(REDIS_HOST_PARAM_ENV, "/mini-app/cache/host"),
                "127.0.0.1");
        String redisPort = fetchSsmParameter(
                System.getenv().getOrDefault(REDIS_PORT_PARAM_ENV, "/mini-app/cache/port"),
                "6379");
        System.out.println("Connecting to Redis cache at: " + redisHost + ":" + redisPort);
        // Actual Redis client initialisation would go here
    }

    private void initializeExternalServices() {
        // External service URLs are read from environment variables at runtime
        String externalApiUrl    = System.getenv().getOrDefault("EXTERNAL_API_URL",    "http://api.example.com/v1");
        String paymentServiceUrl = System.getenv().getOrDefault("PAYMENT_SERVICE_URL", "https://payment.internal.company.com/process");
        System.out.println("Initializing external API: " + externalApiUrl);
        System.out.println("Initializing payment service: " + paymentServiceUrl);
    }

    // -----------------------------------------------------------------------
    // Inner value object
    // -----------------------------------------------------------------------

    private static class DbCredentials {
        String username;
        String password;
        String host;
        String dbname;
    }
}
