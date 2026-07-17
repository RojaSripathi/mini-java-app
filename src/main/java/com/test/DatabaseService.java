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
 * and HikariCP for cloud-native connection pooling with RDS Proxy support.
 *
 * Fixes applied:
 *  - cr-java-0069 (blockers 8,9,10): Hard-coded DB credentials replaced with AWS Secrets Manager
 *  - cr-java-0113 (blocker 16):      Externalized secrets via AWS Secrets Manager
 *  - cr-java-0077 (blockers 11,12,13): Hard-coded ports replaced with AWS SSM Parameter Store / env vars
 *  - cr-java-0073 (blockers 17,18):  Direct JDBC replaced with HikariCP + RDS Proxy
 *  - cr-java-0097 (blocker 19):      Connection timeouts configured on HikariCP data source
 */
public class DatabaseService {

    // -----------------------------------------------------------------------
    // Configuration keys – values are resolved at runtime from environment
    // variables (injected by ECS/EKS/Elastic Beanstalk) or AWS SSM Parameter
    // Store, never hard-coded in source.
    // -----------------------------------------------------------------------

    /** AWS region – defaults to us-east-1 if not set */
    private static final String AWS_REGION =
            System.getenv().getOrDefault("AWS_REGION", "us-east-1");

    /** Name of the Secrets Manager secret that holds DB credentials JSON:
     *  { "username": "...", "password": "...", "host": "...", "port": "...", "dbname": "..." } */
    private static final String DB_SECRET_NAME =
            System.getenv().getOrDefault("DB_SECRET_NAME", "mini-app/db-credentials");

    /** SSM Parameter Store path for the DB port (fallback if not in secret) */
    private static final String DB_PORT_PARAM =
            System.getenv().getOrDefault("DB_PORT_PARAM", "/mini-app/db/port");

    /** SSM Parameter Store path for the Redis port */
    private static final String REDIS_PORT_PARAM =
            System.getenv().getOrDefault("REDIS_PORT_PARAM", "/mini-app/cache/redis/port");

    /** Redis host – injected via environment variable */
    private static final String REDIS_HOST =
            System.getenv().getOrDefault("REDIS_HOST", "");

    /** External API URL – injected via environment variable */
    private static final String EXTERNAL_API_URL =
            System.getenv().getOrDefault("EXTERNAL_API_URL", "");

    /** Payment service URL – injected via environment variable */
    private static final String PAYMENT_SERVICE_URL =
            System.getenv().getOrDefault("PAYMENT_SERVICE_URL", "");

    // -----------------------------------------------------------------------
    // HikariCP data source (replaces raw DriverManager / direct JDBC)
    // -----------------------------------------------------------------------
    private HikariDataSource dataSource;

    // -----------------------------------------------------------------------
    // AWS clients
    // -----------------------------------------------------------------------
    private final SecretsManagerClient secretsClient;
    private final SsmClient ssmClient;
    private final ObjectMapper objectMapper;

    public DatabaseService() {
        Region region = Region.of(AWS_REGION);
        this.secretsClient = SecretsManagerClient.builder()
                .region(region)
                .build();
        this.ssmClient = SsmClient.builder()
                .region(region)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    // -----------------------------------------------------------------------
    // Secret / parameter helpers
    // -----------------------------------------------------------------------

    /**
     * Retrieves the database credentials JSON from AWS Secrets Manager.
     * The secret is expected to be a JSON object with keys:
     * username, password, host, port, dbname.
     */
    private JsonNode getDbSecret() {
        try {
            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(DB_SECRET_NAME)
                    .build();
            GetSecretValueResponse response = secretsClient.getSecretValue(request);
            return objectMapper.readTree(response.secretString());
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve DB secret from AWS Secrets Manager: "
                    + e.getMessage(), e);
        }
    }

    /**
     * Retrieves a parameter value from AWS Systems Manager Parameter Store.
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
            throw new RuntimeException("Failed to retrieve SSM parameter '" + paramName
                    + "': " + e.getMessage(), e);
        }
    }

    // -----------------------------------------------------------------------
    // Connection setup
    // -----------------------------------------------------------------------

    public void connect() {
        System.out.println("Retrieving database credentials from AWS Secrets Manager...");

        // Fetch credentials and connection details from Secrets Manager
        JsonNode secret = getDbSecret();
        String dbUsername = secret.get("username").asText();
        String dbPassword = secret.get("password").asText();
        String dbHost     = secret.get("host").asText();
        // Port: prefer value from secret; fall back to SSM Parameter Store
        String dbPort = secret.has("port")
                ? secret.get("port").asText()
                : getSsmParameter(DB_PORT_PARAM);
        String dbName = secret.get("dbname").asText();

        String jdbcUrl = "jdbc:mysql://" + dbHost + ":" + dbPort + "/" + dbName;

        // -----------------------------------------------------------------------
        // HikariCP configuration with explicit timeouts (cr-java-0097)
        // -----------------------------------------------------------------------
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(dbUsername);
        hikariConfig.setPassword(dbPassword);
        hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");

        // Connection pool sizing
        hikariConfig.setMaximumPoolSize(20);
        hikariConfig.setMinimumIdle(5);

        // Timeout configuration – prevents indefinite hangs in cloud environments
        hikariConfig.setConnectionTimeout(Duration.ofSeconds(30).toMillis());   // max wait for pool connection
        hikariConfig.setIdleTimeout(Duration.ofMinutes(10).toMillis());          // idle connection eviction
        hikariConfig.setMaxLifetime(Duration.ofMinutes(30).toMillis());          // max connection lifetime
        hikariConfig.setKeepaliveTime(Duration.ofMinutes(5).toMillis());         // keepalive ping interval
        hikariConfig.setInitializationFailTimeout(Duration.ofSeconds(60).toMillis());

        // Health-check query
        hikariConfig.setConnectionTestQuery("SELECT 1");

        // Pool name for observability
        hikariConfig.setPoolName("MiniAppHikariPool");

        dataSource = new HikariDataSource(hikariConfig);

        System.out.println("HikariCP connection pool initialised for host: " + dbHost
                + " database: " + dbName);

        // Initialise ancillary services
        connectToCache();
        initializeExternalServices();
    }

    private void connectToCache() {
        // Redis host and port are injected via environment variables /
        // SSM Parameter Store – no hard-coded values.
        String redisPort = System.getenv("REDIS_PORT") != null
                ? System.getenv("REDIS_PORT")
                : getSsmParameter(REDIS_PORT_PARAM);
        System.out.println("Connecting to Redis cache at: " + REDIS_HOST + ":" + redisPort);
        // Actual Redis client initialisation would use the above values.
    }

    private void initializeExternalServices() {
        // URLs are injected via environment variables – no hard-coded endpoints.
        System.out.println("Initializing external API: " + EXTERNAL_API_URL);
        System.out.println("Initializing payment service: " + PAYMENT_SERVICE_URL);
    }

    // -----------------------------------------------------------------------
    // Query execution
    // -----------------------------------------------------------------------

    public void executeQuery(String sql) {
        if (dataSource == null) {
            System.err.println("DataSource not initialised – call connect() first.");
            return;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setQueryTimeout(30);
            System.out.println("Executing query: " + sql);
            stmt.execute();

        } catch (SQLException e) {
            System.err.println("Query execution failed: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("HikariCP connection pool closed.");
        }
        if (secretsClient != null) {
            secretsClient.close();
        }
        if (ssmClient != null) {
            ssmClient.close();
        }
    }
}
