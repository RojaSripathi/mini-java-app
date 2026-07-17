package com.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive JUnit 5 tests for DatabaseService class.
 * Tests cover: connect(), executeQuery(), disconnect(), connectToCache(), initializeExternalServices()
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DatabaseService Tests")
class DatabaseServiceTest {

    private DatabaseService databaseService;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeEach
    void setUp() {
        databaseService = new DatabaseService();
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Constructor Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Default constructor creates a non-null DatabaseService instance")
    void constructor_defaultConstructor_createsInstance() {
        DatabaseService service = new DatabaseService();
        assertNotNull(service, "DatabaseService instance should not be null");
    }

    @Test
    @DisplayName("New instance has null connection field by default")
    void constructor_newInstance_connectionIsNull() throws Exception {
        DatabaseService service = new DatabaseService();
        Field connectionField = DatabaseService.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        Object connectionValue = connectionField.get(service);
        assertNull(connectionValue, "Connection should be null before connect() is called");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // connect() Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("connect() prints connecting message to stdout")
    void connect_whenCalled_printsConnectingMessage() {
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConn = mock(Connection.class);
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConn);

            databaseService.connect();

            String output = outContent.toString();
            assertTrue(output.contains("Connecting to database"),
                    "Should print 'Connecting to database' message");
        }
    }

    @Test
    @DisplayName("connect() prints connected message with DB URL on success")
    void connect_onSuccess_printsConnectedMessage() {
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConn = mock(Connection.class);
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConn);

            databaseService.connect();

            String output = outContent.toString();
            assertTrue(output.contains("Connected to database"),
                    "Should print 'Connected to database' message");
        }
    }

    @Test
    @DisplayName("connect() prints Redis cache connection message")
    void connect_onSuccess_printsRedisCacheMessage() {
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConn = mock(Connection.class);
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConn);

            databaseService.connect();

            String output = outContent.toString();
            assertTrue(output.contains("Redis") || output.contains("cache"),
                    "Should print Redis/cache connection message");
        }
    }

    @Test
    @DisplayName("connect() prints external API initialization message")
    void connect_onSuccess_printsExternalApiMessage() {
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConn = mock(Connection.class);
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConn);

            databaseService.connect();

            String output = outContent.toString();
            assertTrue(output.contains("API") || output.contains("external") || output.contains("payment"),
                    "Should print external service initialization messages");
        }
    }

    @Test
    @DisplayName("connect() handles SQLException gracefully and prints error to stderr")
    void connect_whenSQLExceptionThrown_printsErrorMessage() {
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            SQLException sqlEx = new SQLException("Connection refused");
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenThrow(sqlEx);

            assertDoesNotThrow(() -> databaseService.connect(),
                    "connect() should not propagate SQLException");

            String errOutput = errContent.toString();
            assertTrue(errOutput.contains("Database connection failed") || errOutput.contains("Connection refused"),
                    "Should print error message to stderr");
        }
    }

    @Test
    @DisplayName("connect() sets connection field when successful")
    void connect_onSuccess_setsConnectionField() throws Exception {
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConn = mock(Connection.class);
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConn);

            databaseService.connect();

            Field connectionField = DatabaseService.class.getDeclaredField("connection");
            connectionField.setAccessible(true);
            Object connectionValue = connectionField.get(databaseService);
            assertNotNull(connectionValue, "Connection field should be set after successful connect()");
        }
    }

    @Test
    @DisplayName("connect() uses hardcoded DB_URL containing localhost and port 3306")
    void connect_usesHardcodedDbUrl() {
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConn = mock(Connection.class);
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConn);

            databaseService.connect();

            String output = outContent.toString();
            assertTrue(output.contains("localhost") || output.contains("3306") || output.contains("mini_app_db"),
                    "Should use hardcoded DB URL with localhost:3306");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // executeQuery() Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("executeQuery() does nothing when connection is null")
    void executeQuery_whenConnectionIsNull_doesNotThrow() {
        assertDoesNotThrow(() -> databaseService.executeQuery("SELECT 1"),
                "executeQuery() should not throw when connection is null");
    }

    @Test
    @DisplayName("executeQuery() does nothing when connection is null - no output")
    void executeQuery_whenConnectionIsNull_producesNoOutput() {
        databaseService.executeQuery("SELECT * FROM users");
        String output = outContent.toString();
        assertFalse(output.contains("Executing query"),
                "Should not print 'Executing query' when connection is null");
    }

    @Test
    @DisplayName("executeQuery() executes query when connection is open")
    void executeQuery_whenConnectionIsOpen_executesQuery() throws Exception {
        Connection mockConn = mock(Connection.class);
        PreparedStatement mockStmt = mock(PreparedStatement.class);
        when(mockConn.isClosed()).thenReturn(false);
        when(mockConn.prepareStatement(anyString())).thenReturn(mockStmt);
        when(mockStmt.execute()).thenReturn(true);

        Field connectionField = DatabaseService.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        connectionField.set(databaseService, mockConn);

        databaseService.executeQuery("SELECT * FROM users");

        verify(mockConn).prepareStatement("SELECT * FROM users");
        verify(mockStmt).setQueryTimeout(30);
        verify(mockStmt).execute();
        verify(mockStmt).close();
    }

    @Test
    @DisplayName("executeQuery() prints executing query message when connection is open")
    void executeQuery_whenConnectionIsOpen_printsExecutingMessage() throws Exception {
        Connection mockConn = mock(Connection.class);
        PreparedStatement mockStmt = mock(PreparedStatement.class);
        when(mockConn.isClosed()).thenReturn(false);
        when(mockConn.prepareStatement(anyString())).thenReturn(mockStmt);

        Field connectionField = DatabaseService.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        connectionField.set(databaseService, mockConn);

        databaseService.executeQuery("SELECT 1");

        String output = outContent.toString();
        assertTrue(output.contains("Executing query"),
                "Should print 'Executing query' message");
    }

    @Test
    @DisplayName("executeQuery() sets query timeout to 30 seconds")
    void executeQuery_whenConnectionIsOpen_setsQueryTimeout30() throws Exception {
        Connection mockConn = mock(Connection.class);
        PreparedStatement mockStmt = mock(PreparedStatement.class);
        when(mockConn.isClosed()).thenReturn(false);
        when(mockConn.prepareStatement(anyString())).thenReturn(mockStmt);

        Field connectionField = DatabaseService.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        connectionField.set(databaseService, mockConn);

        databaseService.executeQuery("SELECT 1");

        verify(mockStmt).setQueryTimeout(30);
    }

    @Test
    @DisplayName("executeQuery() does nothing when connection is closed")
    void executeQuery_whenConnectionIsClosed_doesNotExecute() throws Exception {
        Connection mockConn = mock(Connection.class);
        when(mockConn.isClosed()).thenReturn(true);

        Field connectionField = DatabaseService.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        connectionField.set(databaseService, mockConn);

        databaseService.executeQuery("SELECT 1");

        verify(mockConn, never()).prepareStatement(anyString());
    }

    @Test
    @DisplayName("executeQuery() handles SQLException gracefully")
    void executeQuery_whenSQLExceptionThrown_printsErrorMessage() throws Exception {
        Connection mockConn = mock(Connection.class);
        when(mockConn.isClosed()).thenReturn(false);
        when(mockConn.prepareStatement(anyString())).thenThrow(new SQLException("Query failed"));

        Field connectionField = DatabaseService.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        connectionField.set(databaseService, mockConn);

        assertDoesNotThrow(() -> databaseService.executeQuery("INVALID SQL"),
                "executeQuery() should not propagate SQLException");

        String errOutput = errContent.toString();
        assertTrue(errOutput.contains("Query execution failed") || errOutput.contains("Query failed"),
                "Should print error message to stderr");
    }

    @Test
    @DisplayName("executeQuery() with empty SQL string does not throw")
    void executeQuery_withEmptySql_doesNotThrow() throws Exception {
        Connection mockConn = mock(Connection.class);
        PreparedStatement mockStmt = mock(PreparedStatement.class);
        when(mockConn.isClosed()).thenReturn(false);
        when(mockConn.prepareStatement(anyString())).thenReturn(mockStmt);

        Field connectionField = DatabaseService.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        connectionField.set(databaseService, mockConn);

        assertDoesNotThrow(() -> databaseService.executeQuery(""),
                "executeQuery() should not throw with empty SQL");
    }

    @Test
    @DisplayName("executeQuery() with null SQL and null connection does not throw")
    void executeQuery_withNullSqlAndNullConnection_doesNotThrow() {
        assertDoesNotThrow(() -> databaseService.executeQuery(null),
                "executeQuery() should not throw when connection is null");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // disconnect() Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("disconnect() does nothing when connection is null")
    void disconnect_whenConnectionIsNull_doesNotThrow() {
        assertDoesNotThrow(() -> databaseService.disconnect(),
                "disconnect() should not throw when connection is null");
    }

    @Test
    @DisplayName("disconnect() closes open connection successfully")
    void disconnect_whenConnectionIsOpen_closesConnection() throws Exception {
        Connection mockConn = mock(Connection.class);
        when(mockConn.isClosed()).thenReturn(false);

        Field connectionField = DatabaseService.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        connectionField.set(databaseService, mockConn);

        databaseService.disconnect();

        verify(mockConn).close();
    }

    @Test
    @DisplayName("disconnect() prints closed message when connection is open")
    void disconnect_whenConnectionIsOpen_printsClosed() throws Exception {
        Connection mockConn = mock(Connection.class);
        when(mockConn.isClosed()).thenReturn(false);

        Field connectionField = DatabaseService.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        connectionField.set(databaseService, mockConn);

        databaseService.disconnect();

        String output = outContent.toString();
        assertTrue(output.contains("closed") || output.contains("Database connection closed"),
                "Should print connection closed message");
    }

    @Test
    @DisplayName("disconnect() does not close already-closed connection")
    void disconnect_whenConnectionIsAlreadyClosed_doesNotCallClose() throws Exception {
        Connection mockConn = mock(Connection.class);
        when(mockConn.isClosed()).thenReturn(true);

        Field connectionField = DatabaseService.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        connectionField.set(databaseService, mockConn);

        databaseService.disconnect();

        verify(mockConn, never()).close();
    }

    @Test
    @DisplayName("disconnect() handles SQLException gracefully")
    void disconnect_whenSQLExceptionThrown_printsErrorMessage() throws Exception {
        Connection mockConn = mock(Connection.class);
        when(mockConn.isClosed()).thenReturn(false);
        doThrow(new SQLException("Close failed")).when(mockConn).close();

        Field connectionField = DatabaseService.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        connectionField.set(databaseService, mockConn);

        assertDoesNotThrow(() -> databaseService.disconnect(),
                "disconnect() should not propagate SQLException");

        String errOutput = errContent.toString();
        assertTrue(errOutput.contains("Failed to close") || errOutput.contains("Close failed"),
                "Should print error message to stderr");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Static Field / Constant Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DB_HOST constant is 'localhost'")
    void staticField_dbHost_isLocalhost() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("DB_HOST");
        field.setAccessible(true);
        assertEquals("localhost", field.get(null));
    }

    @Test
    @DisplayName("DB_PORT constant is '3306'")
    void staticField_dbPort_is3306() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("DB_PORT");
        field.setAccessible(true);
        assertEquals("3306", field.get(null));
    }

    @Test
    @DisplayName("DB_NAME constant is 'mini_app_db'")
    void staticField_dbName_isMiniAppDb() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("DB_NAME");
        field.setAccessible(true);
        assertEquals("mini_app_db", field.get(null));
    }

    @Test
    @DisplayName("DB_URL constant contains jdbc:mysql://")
    void staticField_dbUrl_containsJdbcMysql() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("DB_URL");
        field.setAccessible(true);
        String dbUrl = (String) field.get(null);
        assertTrue(dbUrl.startsWith("jdbc:mysql://"),
                "DB_URL should start with 'jdbc:mysql://'");
    }

    @Test
    @DisplayName("DB_USERNAME constant is 'root'")
    void staticField_dbUsername_isRoot() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("DB_USERNAME");
        field.setAccessible(true);
        assertEquals("root", field.get(null));
    }

    @Test
    @DisplayName("DB_PASSWORD constant is 'password123'")
    void staticField_dbPassword_isPassword123() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("DB_PASSWORD");
        field.setAccessible(true);
        assertEquals("password123", field.get(null));
    }

    @Test
    @DisplayName("REDIS_HOST constant is '127.0.0.1'")
    void staticField_redisHost_is127001() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("REDIS_HOST");
        field.setAccessible(true);
        assertEquals("127.0.0.1", field.get(null));
    }

    @Test
    @DisplayName("REDIS_PORT constant is 6379")
    void staticField_redisPort_is6379() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("REDIS_PORT");
        field.setAccessible(true);
        assertEquals(6379, field.get(null));
    }

    @Test
    @DisplayName("EXTERNAL_API_URL constant contains api.example.com")
    void staticField_externalApiUrl_containsApiExampleCom() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("EXTERNAL_API_URL");
        field.setAccessible(true);
        String url = (String) field.get(null);
        assertTrue(url.contains("api.example.com"),
                "EXTERNAL_API_URL should contain 'api.example.com'");
    }

    @Test
    @DisplayName("PAYMENT_SERVICE_URL constant contains payment")
    void staticField_paymentServiceUrl_containsPayment() throws Exception {
        Field field = DatabaseService.class.getDeclaredField("PAYMENT_SERVICE_URL");
        field.setAccessible(true);
        String url = (String) field.get(null);
        assertTrue(url.contains("payment"),
                "PAYMENT_SERVICE_URL should contain 'payment'");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private Method Reflection Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("connectToCache() private method prints Redis connection info")
    void connectToCache_printsRedisConnectionInfo() throws Exception {
        Method method = DatabaseService.class.getDeclaredMethod("connectToCache");
        method.setAccessible(true);

        method.invoke(databaseService);

        String output = outContent.toString();
        assertTrue(output.contains("127.0.0.1") || output.contains("6379") || output.contains("Redis"),
                "connectToCache() should print Redis host/port info");
    }

    @Test
    @DisplayName("initializeExternalServices() private method prints API URLs")
    void initializeExternalServices_printsApiUrls() throws Exception {
        Method method = DatabaseService.class.getDeclaredMethod("initializeExternalServices");
        method.setAccessible(true);

        method.invoke(databaseService);

        String output = outContent.toString();
        assertTrue(output.contains("api.example.com") || output.contains("payment"),
                "initializeExternalServices() should print external API URLs");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Integration-style Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Full lifecycle: connect, executeQuery, disconnect")
    void fullLifecycle_connectExecuteDisconnect_noExceptions() throws Exception {
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConn = mock(Connection.class);
            PreparedStatement mockStmt = mock(PreparedStatement.class);
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConn);
            when(mockConn.isClosed()).thenReturn(false);
            when(mockConn.prepareStatement(anyString())).thenReturn(mockStmt);

            assertDoesNotThrow(() -> {
                databaseService.connect();
                databaseService.executeQuery("SELECT 1");
                databaseService.disconnect();
            }, "Full lifecycle should not throw any exceptions");
        }
    }

    @Test
    @DisplayName("Multiple executeQuery calls on open connection all succeed")
    void executeQuery_multipleCallsOnOpenConnection_allSucceed() throws Exception {
        Connection mockConn = mock(Connection.class);
        PreparedStatement mockStmt = mock(PreparedStatement.class);
        when(mockConn.isClosed()).thenReturn(false);
        when(mockConn.prepareStatement(anyString())).thenReturn(mockStmt);

        Field connectionField = DatabaseService.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        connectionField.set(databaseService, mockConn);

        assertDoesNotThrow(() -> {
            databaseService.executeQuery("SELECT 1");
            databaseService.executeQuery("SELECT * FROM users");
            databaseService.executeQuery("INSERT INTO logs VALUES ('test')");
        }, "Multiple executeQuery calls should not throw");

        verify(mockStmt, times(3)).execute();
    }
}
