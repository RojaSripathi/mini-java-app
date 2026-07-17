package com.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive JUnit 5 tests for DatabaseService class.
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
    // Constructor
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Default constructor creates a non-null DatabaseService instance")
    void constructor_defaultConstructor_createsInstance() {
        DatabaseService service = new DatabaseService();
        assertNotNull(service);
    }

    @Test
    @DisplayName("Newly constructed DatabaseService has null connection field")
    void constructor_newInstance_connectionIsNull() throws Exception {
        DatabaseService service = new DatabaseService();
        Field f = DatabaseService.class.getDeclaredField("connection");
        f.setAccessible(true);
        assertNull(f.get(service));
    }

    @Test
    @DisplayName("Multiple DatabaseService instances are independent objects")
    void constructor_multipleInstances_areIndependent() {
        assertNotSame(new DatabaseService(), new DatabaseService());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // connect() – happy path
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("connect() prints 'Connecting to database...' message")
    void connect_whenCalled_printsConnectingMessage() {
        try (MockedStatic<DriverManager> dm = Mockito.mockStatic(DriverManager.class)) {
            dm.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
              .thenReturn(mock(Connection.class));
            databaseService.connect();
            assertTrue(outContent.toString().contains("Connecting to database..."));
        }
    }

    @Test
    @DisplayName("connect() prints the DB_URL after successful connection")
    void connect_successfulConnection_printsDbUrl() {
        try (MockedStatic<DriverManager> dm = Mockito.mockStatic(DriverManager.class)) {
            dm.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
              .thenReturn(mock(Connection.class));
            databaseService.connect();
            String out = outContent.toString();
            assertTrue(out.contains("Connected to database:"));
            assertTrue(out.contains("jdbc:mysql://localhost:3306/mini_app_db"));
        }
    }

    @Test
    @DisplayName("connect() prints the DB_USERNAME after successful connection")
    void connect_successfulConnection_printsUsername() {
        try (MockedStatic<DriverManager> dm = Mockito.mockStatic(DriverManager.class)) {
            dm.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
              .thenReturn(mock(Connection.class));
            databaseService.connect();
            assertTrue(outContent.toString().contains("Using username: root"));
        }
    }

    @Test
    @DisplayName("connect() invokes connectToCache() and prints Redis message")
    void connect_successfulConnection_printsRedisCacheMessage() {
        try (MockedStatic<DriverManager> dm = Mockito.mockStatic(DriverManager.class)) {
            dm.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
              .thenReturn(mock(Connection.class));
            databaseService.connect();
            assertTrue(outContent.toString().contains("Connecting to Redis cache at: 127.0.0.1:6379"));
        }
    }

    @Test
    @DisplayName("connect() invokes initializeExternalServices() and prints API URL")
    void connect_successfulConnection_printsExternalApiMessage() {
        try (MockedStatic<DriverManager> dm = Mockito.mockStatic(DriverManager.class)) {
            dm.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
              .thenReturn(mock(Connection.class));
            databaseService.connect();
            assertTrue(outContent.toString().contains("Initializing external API: http://api.example.com:8080/v1"));
        }
    }

    @Test
    @DisplayName("connect() invokes initializeExternalServices() and prints payment service URL")
    void connect_successfulConnection_printsPaymentServiceMessage() {
        try (MockedStatic<DriverManager> dm = Mockito.mockStatic(DriverManager.class)) {
            dm.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
              .thenReturn(mock(Connection.class));
            databaseService.connect();
            assertTrue(outContent.toString().contains("Initializing payment service: https://payment.internal.company.com/process"));
        }
    }

    @Test
    @DisplayName("connect() sets the connection field on success")
    void connect_successfulConnection_setsConnectionField() throws Exception {
        try (MockedStatic<DriverManager> dm = Mockito.mockStatic(DriverManager.class)) {
            dm.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
              .thenReturn(mock(Connection.class));
            databaseService.connect();
            Field f = DatabaseService.class.getDeclaredField("connection");
            f.setAccessible(true);
            assertNotNull(f.get(databaseService));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // connect() – failure path (SQLException via Answer)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("connect() prints error message when DriverManager throws SQLException")
    void connect_sqlException_printsErrorMessage() {
        try (MockedStatic<DriverManager> dm = Mockito.mockStatic(DriverManager.class)) {
            dm.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
              .thenAnswer(new ThrowSQLException("Connection refused"));
            databaseService.connect();
            assertTrue(errContent.toString().contains("Database connection failed: Connection refused"));
        }
    }

    @Test
    @DisplayName("connect() does not throw exception when DriverManager throws SQLException")
    void connect_sqlException_doesNotPropagateException() {
        try (MockedStatic<DriverManager> dm = Mockito.mockStatic(DriverManager.class)) {
            dm.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
              .thenAnswer(new ThrowSQLException("Timeout"));
            assertDoesNotThrow(() -> databaseService.connect());
        }
    }

    @Test
    @DisplayName("connect() leaves connection null when DriverManager throws SQLException")
    void connect_sqlException_connectionRemainsNull() throws Exception {
        try (MockedStatic<DriverManager> dm = Mockito.mockStatic(DriverManager.class)) {
            dm.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
              .thenAnswer(new ThrowSQLException("Auth failed"));
            databaseService.connect();
            Field f = DatabaseService.class.getDeclaredField("connection");
            f.setAccessible(true);
            assertNull(f.get(databaseService));
        }
    }

    @Test
    @DisplayName("connect() always prints 'Connecting to database...' regardless of outcome")
    void connect_alwaysPrintsConnectingMessage_beforeDriverManagerCall() {
        try (MockedStatic<DriverManager> dm = Mockito.mockStatic(DriverManager.class)) {
            dm.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
              .thenAnswer(new ThrowSQLException("Refused"));
            databaseService.connect();
            assertTrue(outContent.toString().contains("Connecting to database..."));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // executeQuery() – happy path
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("executeQuery() executes SQL when connection is open")
    void executeQuery_openConnection_executesStatement() throws Exception {
        Connection mockConn = mock(Connection.class);
        PreparedStatement mockStmt = mock(PreparedStatement.class);
        when(mockConn.isClosed()).thenReturn(false);
        when(mockConn.prepareStatement(anyString())).thenReturn(mockStmt);
        injectConnection(databaseService, mockConn);

        databaseService.executeQuery("SELECT 1");

        verify(mockStmt, times(1)).execute();
    }

    @Test
    @DisplayName("executeQuery() sets query timeout to 30 seconds")
    void executeQuery_openConnection_setsQueryTimeout() throws Exception {
        Connection mockConn = mock(Connection.class);
        PreparedStatement mockStmt = mock(PreparedStatement.class);
        when(mockConn.isClosed()).thenReturn(false);
        when(mockConn.prepareStatement(anyString())).thenReturn(mockStmt);
        injectConnection(databaseService, mockConn);

        databaseService.executeQuery("SELECT 1");

        verify(mockStmt, times(1)).setQueryTimeout(30);
    }

    @Test
    @DisplayName("executeQuery() prints the SQL being executed")
    void executeQuery_openConnection_printsSqlMessage() throws Exception {
        Connection mockConn = mock(Connection.class);
        PreparedStatement mockStmt = mock(PreparedStatement.class);
        when(mockConn.isClosed()).thenReturn(false);
        when(mockConn.prepareStatement(anyString())).thenReturn(mockStmt);
        injectConnection(databaseService, mockConn);

        databaseService.executeQuery("SELECT * FROM users");

        assertTrue(outContent.toString().contains("Executing query: SELECT * FROM users"));
    }

    @Test
    @DisplayName("executeQuery() closes PreparedStatement after execution (try-with-resources)")
    void executeQuery_openConnection_closesPreparedStatement() throws Exception {
        Connection mockConn = mock(Connection.class);
        PreparedStatement mockStmt = mock(PreparedStatement.class);
        when(mockConn.isClosed()).thenReturn(false);
        when(mockConn.prepareStatement(anyString())).thenReturn(mockStmt);
        injectConnection(databaseService, mockConn);

        databaseService.executeQuery("DELETE FROM temp");

        verify(mockStmt, times(1)).close();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // executeQuery() – null / closed connection guard
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("executeQuery() does nothing when connection is null")
    void executeQuery_nullConnection_doesNothing() {
        assertDoesNotThrow(() -> databaseService.executeQuery("SELECT 1"));
    }

    @Test
    @DisplayName("executeQuery() does nothing when connection is closed")
    void executeQuery_closedConnection_doesNothing() throws Exception {
        Connection mockConn = mock(Connection.class);
        when(mockConn.isClosed()).thenReturn(true);
        injectConnection(databaseService, mockConn);

        assertDoesNotThrow(() -> databaseService.executeQuery("SELECT 1"));
        verify(mockConn, never()).prepareStatement(anyString());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // executeQuery() – SQLException path
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("executeQuery() prints error message when prepareStatement throws SQLException")
    void executeQuery_sqlException_printsErrorMessage() throws Exception {
        Connection mockConn = mock(Connection.class);
        when(mockConn.isClosed()).thenReturn(false);
        when(mockConn.prepareStatement(anyString())).thenThrow(new SQLException("Syntax error"));
        injectConnection(databaseService, mockConn);

        databaseService.executeQuery("INVALID SQL");

        assertTrue(errContent.toString().contains("Query execution failed: Syntax error"));
    }

    @Test
    @DisplayName("executeQuery() does not propagate SQLException")
    void executeQuery_sqlException_doesNotPropagateException() throws Exception {
        Connection mockConn = mock(Connection.class);
        when(mockConn.isClosed()).thenReturn(false);
        when(mockConn.prepareStatement(anyString())).thenThrow(new SQLException("Table not found"));
        injectConnection(databaseService, mockConn);

        assertDoesNotThrow(() -> databaseService.executeQuery("SELECT * FROM missing_table"));
    }

    @Test
    @DisplayName("executeQuery() prints error when stmt.execute() throws SQLException")
    void executeQuery_executeThrowsSqlException_printsErrorMessage() throws Exception {
        Connection mockConn = mock(Connection.class);
        PreparedStatement mockStmt = mock(PreparedStatement.class);
        when(mockConn.isClosed()).thenReturn(false);
        when(mockConn.prepareStatement(anyString())).thenReturn(mockStmt);
        doThrow(new SQLException("Execution error")).when(mockStmt).execute();
        injectConnection(databaseService, mockConn);

        databaseService.executeQuery("SELECT 1");

        assertTrue(errContent.toString().contains("Query execution failed: Execution error"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // disconnect() – happy path
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("disconnect() closes an open connection")
    void disconnect_openConnection_closesConnection() throws Exception {
        Connection mockConn = mock(Connection.class);
        when(mockConn.isClosed()).thenReturn(false);
        injectConnection(databaseService, mockConn);

        databaseService.disconnect();

        verify(mockConn, times(1)).close();
    }

    @Test
    @DisplayName("disconnect() prints 'Database connection closed' message")
    void disconnect_openConnection_printsClosed() throws Exception {
        Connection mockConn = mock(Connection.class);
        when(mockConn.isClosed()).thenReturn(false);
        injectConnection(databaseService, mockConn);

        databaseService.disconnect();

        assertTrue(outContent.toString().contains("Database connection closed"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // disconnect() – null / closed connection guard
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("disconnect() does nothing when connection is null")
    void disconnect_nullConnection_doesNothing() {
        assertDoesNotThrow(() -> databaseService.disconnect());
    }

    @Test
    @DisplayName("disconnect() does nothing when connection is already closed")
    void disconnect_alreadyClosedConnection_doesNothing() throws Exception {
        Connection mockConn = mock(Connection.class);
        when(mockConn.isClosed()).thenReturn(true);
        injectConnection(databaseService, mockConn);

        assertDoesNotThrow(() -> databaseService.disconnect());
        verify(mockConn, never()).close();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // disconnect() – SQLException path
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("disconnect() prints error message when close() throws SQLException")
    void disconnect_sqlException_printsErrorMessage() throws Exception {
        Connection mockConn = mock(Connection.class);
        when(mockConn.isClosed()).thenReturn(false);
        doThrow(new SQLException("Close failed")).when(mockConn).close();
        injectConnection(databaseService, mockConn);

        databaseService.disconnect();

        assertTrue(errContent.toString().contains("Failed to close database connection: Close failed"));
    }

    @Test
    @DisplayName("disconnect() does not propagate SQLException from close()")
    void disconnect_sqlException_doesNotPropagateException() throws Exception {
        Connection mockConn = mock(Connection.class);
        when(mockConn.isClosed()).thenReturn(false);
        doThrow(new SQLException("IO error")).when(mockConn).close();
        injectConnection(databaseService, mockConn);

        assertDoesNotThrow(() -> databaseService.disconnect());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Static constant verification (via connect() output)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Static constants: DB_URL is correctly composed from host, port, and db name")
    void staticConstants_dbUrl_isCorrectlyComposed() {
        try (MockedStatic<DriverManager> dm = Mockito.mockStatic(DriverManager.class)) {
            dm.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
              .thenReturn(mock(Connection.class));
            databaseService.connect();
            String out = outContent.toString();
            assertTrue(out.contains("localhost"));
            assertTrue(out.contains("3306"));
            assertTrue(out.contains("mini_app_db"));
        }
    }

    @Test
    @DisplayName("Static constants: REDIS_PORT is 6379")
    void staticConstants_redisPort_is6379() {
        try (MockedStatic<DriverManager> dm = Mockito.mockStatic(DriverManager.class)) {
            dm.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
              .thenReturn(mock(Connection.class));
            databaseService.connect();
            assertTrue(outContent.toString().contains("6379"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Full lifecycle integration
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Full lifecycle: connect -> executeQuery -> disconnect succeeds without exceptions")
    void lifecycle_connectExecuteQueryDisconnect_noExceptions() throws Exception {
        try (MockedStatic<DriverManager> dm = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConn = mock(Connection.class);
            PreparedStatement mockStmt = mock(PreparedStatement.class);
            dm.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
              .thenReturn(mockConn);
            when(mockConn.isClosed()).thenReturn(false);
            when(mockConn.prepareStatement(anyString())).thenReturn(mockStmt);

            assertDoesNotThrow(() -> {
                databaseService.connect();
                databaseService.executeQuery("SELECT 1");
                databaseService.disconnect();
            });
        }
    }

    @Test
    @DisplayName("executeQuery() after disconnect() (connection closed) does nothing")
    void executeQuery_afterDisconnect_doesNothing() throws Exception {
        try (MockedStatic<DriverManager> dm = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConn = mock(Connection.class);
            dm.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
              .thenReturn(mockConn);
            when(mockConn.isClosed())
                .thenReturn(false)
                .thenReturn(true);

            databaseService.connect();
            databaseService.disconnect();
            databaseService.executeQuery("SELECT 1");

            verify(mockConn, never()).prepareStatement(anyString());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void injectConnection(DatabaseService service, Connection conn) throws Exception {
        Field f = DatabaseService.class.getDeclaredField("connection");
        f.setAccessible(true);
        f.set(service, conn);
    }

    /**
     * Answer that throws a SQLException – avoids the UnfinishedStubbingException
     * that occurs when using thenThrow() with checked exceptions in static mock lambdas.
     */
    private static class ThrowSQLException implements Answer<Object> {
        private final String message;

        ThrowSQLException(String message) {
            this.message = message;
        }

        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            throw new SQLException(message);
        }
    }
}
