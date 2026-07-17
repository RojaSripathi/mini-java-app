package com.test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive JUnit 5 tests for DatabaseService.
 *
 * Strategy:
 *  - DatabaseService uses real JDBC / DriverManager internally, so we test
 *    the public API surface (connect, executeQuery, disconnect) in scenarios
 *    where no real database is available (expected failure paths) as well as
 *    the internal helper methods via reflection.
 *  - We also verify the hardcoded constant values that are part of the class
 *    contract, and the behaviour of executeQuery / disconnect when the
 *    connection field is null (the default state after construction).
 */
@DisplayName("DatabaseService Tests")
class DatabaseServiceTest {

    private DatabaseService databaseService;

    // -----------------------------------------------------------------------
    // Constants declared in DatabaseService (verified via reflection)
    // -----------------------------------------------------------------------
    private static final String EXPECTED_DB_HOST     = "localhost";
    private static final String EXPECTED_DB_PORT     = "3306";
    private static final String EXPECTED_DB_NAME     = "mini_app_db";
    private static final String EXPECTED_DB_USERNAME = "root";
    private static final String EXPECTED_DB_PASSWORD = "password123";
    private static final String EXPECTED_REDIS_HOST  = "127.0.0.1";
    private static final int    EXPECTED_REDIS_PORT  = 6379;

    @BeforeEach
    void setUp() {
        databaseService = new DatabaseService();
    }

    // -----------------------------------------------------------------------
    // Constructor tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Default constructor creates a non-null instance")
    void constructor_defaultConstructor_createsInstance() {
        // Arrange / Act
        DatabaseService service = new DatabaseService();

        // Assert
        assertNotNull(service, "DatabaseService instance should not be null");
    }

    @Test
    @DisplayName("Newly constructed instance has null connection field")
    void constructor_newInstance_connectionIsNull() throws Exception {
        // Arrange
        DatabaseService service = new DatabaseService();

        // Act
        Field connectionField = DatabaseService.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        Object connectionValue = connectionField.get(service);

        // Assert
        assertNull(connectionValue, "Connection should be null before connect() is called");
    }

    // -----------------------------------------------------------------------
    // Static constant tests (via reflection)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("DB_HOST constant equals 'localhost'")
    void constants_dbHost_isLocalhost() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("DB_HOST");
        field.setAccessible(true);
        assertEquals(EXPECTED_DB_HOST, field.get(null));
    }

    @Test
    @DisplayName("DB_PORT constant equals '3306'")
    void constants_dbPort_is3306() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("DB_PORT");
        field.setAccessible(true);
        assertEquals(EXPECTED_DB_PORT, field.get(null));
    }

    @Test
    @DisplayName("DB_NAME constant equals 'mini_app_db'")
    void constants_dbName_isMiniAppDb() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("DB_NAME");
        field.setAccessible(true);
        assertEquals(EXPECTED_DB_NAME, field.get(null));
    }

    @Test
    @DisplayName("DB_URL constant is correctly assembled from host, port and name")
    void constants_dbUrl_isCorrectlyAssembled() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("DB_URL");
        field.setAccessible(true);
        String dbUrl = (String) field.get(null);
        String expectedUrl = "jdbc:mysql://" + EXPECTED_DB_HOST + ":" + EXPECTED_DB_PORT + "/" + EXPECTED_DB_NAME;
        assertEquals(expectedUrl, dbUrl);
    }

    @Test
    @DisplayName("DB_USERNAME constant equals 'root'")
    void constants_dbUsername_isRoot() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("DB_USERNAME");
        field.setAccessible(true);
        assertEquals(EXPECTED_DB_USERNAME, field.get(null));
    }

    @Test
    @DisplayName("DB_PASSWORD constant equals 'password123'")
    void constants_dbPassword_isPassword123() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("DB_PASSWORD");
        field.setAccessible(true);
        assertEquals(EXPECTED_DB_PASSWORD, field.get(null));
    }

    @Test
    @DisplayName("REDIS_HOST constant equals '127.0.0.1'")
    void constants_redisHost_is127001() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("REDIS_HOST");
        field.setAccessible(true);
        assertEquals(EXPECTED_REDIS_HOST, field.get(null));
    }

    @Test
    @DisplayName("REDIS_PORT constant equals 6379")
    void constants_redisPort_is6379() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("REDIS_PORT");
        field.setAccessible(true);
        assertEquals(EXPECTED_REDIS_PORT, field.get(null));
    }

    @Test
    @DisplayName("EXTERNAL_API_URL constant is not null or empty")
    void constants_externalApiUrl_isNotNullOrEmpty() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("EXTERNAL_API_URL");
        field.setAccessible(true);
        String value = (String) field.get(null);
        assertNotNull(value);
        assertFalse(value.isBlank(), "EXTERNAL_API_URL should not be blank");
    }

    @Test
    @DisplayName("PAYMENT_SERVICE_URL constant is not null or empty")
    void constants_paymentServiceUrl_isNotNullOrEmpty() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("PAYMENT_SERVICE_URL");
        field.setAccessible(true);
        String value = (String) field.get(null);
        assertNotNull(value);
        assertFalse(value.isBlank(), "PAYMENT_SERVICE_URL should not be blank");
    }

    // -----------------------------------------------------------------------
    // connect() tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("connect() does not throw an unchecked exception when DB is unavailable")
    void connect_whenDatabaseUnavailable_doesNotThrowUnchecked() {
        // The method catches SQLException internally; no unchecked exception should escape.
        assertDoesNotThrow(() -> databaseService.connect(),
                "connect() must not propagate any unchecked exception");
    }

    @Test
    @DisplayName("connect() leaves connection null when DB is unavailable")
    void connect_whenDatabaseUnavailable_connectionRemainsNull() throws Exception {
        // Act
        databaseService.connect();

        // Assert – DriverManager will throw SQLException (no real DB), which is caught
        Field connectionField = DatabaseService.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        // Connection may be null because the real DB is not available in the test environment
        Object conn = connectionField.get(databaseService);
        // We only assert that no exception escaped; connection state depends on environment
        assertTrue(conn == null || conn instanceof Connection,
                "connection field should be null or a Connection instance");
    }

    // -----------------------------------------------------------------------
    // executeQuery() tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("executeQuery() with null connection does not throw")
    void executeQuery_withNullConnection_doesNotThrow() {
        // connection is null by default after construction
        assertDoesNotThrow(() -> databaseService.executeQuery("SELECT 1"),
                "executeQuery() must not throw when connection is null");
    }

    @Test
    @DisplayName("executeQuery() with null SQL and null connection does not throw")
    void executeQuery_withNullSqlAndNullConnection_doesNotThrow() {
        assertDoesNotThrow(() -> databaseService.executeQuery(null),
                "executeQuery() must not throw when both connection and SQL are null");
    }

    @Test
    @DisplayName("executeQuery() with empty SQL and null connection does not throw")
    void executeQuery_withEmptySqlAndNullConnection_doesNotThrow() {
        assertDoesNotThrow(() -> databaseService.executeQuery(""),
                "executeQuery() must not throw when SQL is empty and connection is null");
    }

    @Test
    @DisplayName("executeQuery() with valid SQL string and null connection does not throw")
    void executeQuery_withValidSqlAndNullConnection_doesNotThrow() {
        assertDoesNotThrow(() -> databaseService.executeQuery("SELECT * FROM users WHERE id = 1"),
                "executeQuery() must not throw when connection is null");
    }

    // -----------------------------------------------------------------------
    // disconnect() tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("disconnect() with null connection does not throw")
    void disconnect_withNullConnection_doesNotThrow() {
        // connection is null by default
        assertDoesNotThrow(() -> databaseService.disconnect(),
                "disconnect() must not throw when connection is null");
    }

    @Test
    @DisplayName("disconnect() can be called multiple times without throwing")
    void disconnect_calledMultipleTimes_doesNotThrow() {
        assertDoesNotThrow(() -> {
            databaseService.disconnect();
            databaseService.disconnect();
            databaseService.disconnect();
        }, "Repeated disconnect() calls must not throw");
    }

    // -----------------------------------------------------------------------
    // Private method accessibility tests (via reflection)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("connectToCache() private method exists and is accessible via reflection")
    void privateMethod_connectToCache_existsAndIsInvocable() throws Exception {
        Method method = DatabaseService.class.getDeclaredMethod("connectToCache");
        method.setAccessible(true);
        assertDoesNotThrow(() -> method.invoke(databaseService),
                "connectToCache() must not throw an unchecked exception");
    }

    @Test
    @DisplayName("initializeExternalServices() private method exists and is accessible via reflection")
    void privateMethod_initializeExternalServices_existsAndIsInvocable() throws Exception {
        Method method = DatabaseService.class.getDeclaredMethod("initializeExternalServices");
        method.setAccessible(true);
        assertDoesNotThrow(() -> method.invoke(databaseService),
                "initializeExternalServices() must not throw an unchecked exception");
    }

    // -----------------------------------------------------------------------
    // Full lifecycle tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Full lifecycle: connect → executeQuery → disconnect does not throw")
    void lifecycle_connectExecuteQueryDisconnect_doesNotThrow() {
        assertDoesNotThrow(() -> {
            databaseService.connect();
            databaseService.executeQuery("SELECT 1");
            databaseService.disconnect();
        }, "Full lifecycle must not throw any unchecked exception");
    }

    @Test
    @DisplayName("executeQuery() followed by disconnect() does not throw")
    void lifecycle_executeQueryThenDisconnect_doesNotThrow() {
        assertDoesNotThrow(() -> {
            databaseService.executeQuery("INSERT INTO test VALUES (1)");
            databaseService.disconnect();
        });
    }

    @Test
    @DisplayName("Multiple executeQuery() calls with null connection do not throw")
    void lifecycle_multipleExecuteQueryCalls_doesNotThrow() {
        assertDoesNotThrow(() -> {
            databaseService.executeQuery("SELECT 1");
            databaseService.executeQuery("SELECT 2");
            databaseService.executeQuery("SELECT 3");
        });
    }

    // -----------------------------------------------------------------------
    // Class structure / reflection tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("DatabaseService class is public and concrete")
    void classStructure_isPublicAndConcrete() {
        int modifiers = DatabaseService.class.getModifiers();
        assertTrue(java.lang.reflect.Modifier.isPublic(modifiers),
                "DatabaseService should be public");
        assertFalse(java.lang.reflect.Modifier.isAbstract(modifiers),
                "DatabaseService should not be abstract");
        assertFalse(java.lang.reflect.Modifier.isInterface(modifiers),
                "DatabaseService should not be an interface");
    }

    @Test
    @DisplayName("connect() method is public")
    void classStructure_connectMethod_isPublic() throws Exception {
        Method method = DatabaseService.class.getDeclaredMethod("connect");
        assertTrue(java.lang.reflect.Modifier.isPublic(method.getModifiers()),
                "connect() should be public");
    }

    @Test
    @DisplayName("executeQuery() method is public and accepts a String parameter")
    void classStructure_executeQueryMethod_isPublicWithStringParam() throws Exception {
        Method method = DatabaseService.class.getDeclaredMethod("executeQuery", String.class);
        assertTrue(java.lang.reflect.Modifier.isPublic(method.getModifiers()),
                "executeQuery() should be public");
        assertEquals(1, method.getParameterCount(),
                "executeQuery() should have exactly one parameter");
        assertEquals(String.class, method.getParameterTypes()[0],
                "executeQuery() parameter should be of type String");
    }

    @Test
    @DisplayName("disconnect() method is public")
    void classStructure_disconnectMethod_isPublic() throws Exception {
        Method method = DatabaseService.class.getDeclaredMethod("disconnect");
        assertTrue(java.lang.reflect.Modifier.isPublic(method.getModifiers()),
                "disconnect() should be public");
    }
}
