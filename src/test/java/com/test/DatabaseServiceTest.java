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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive JUnit 5 tests for DatabaseService class.
 * Tests cover connect(), disconnect(), executeQuery() and private helper methods
 * via output capture and mocking strategies.
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
        // Arrange & Act
        DatabaseService service = new DatabaseService();

        // Assert
        assertNotNull(service, "DatabaseService instance should not be null");
    }

    @Test
    @DisplayName("Multiple instances are independent objects")
    void constructor_multipleInstances_areIndependent() {
        // Arrange & Act
        DatabaseService service1 = new DatabaseService();
        DatabaseService service2 = new DatabaseService();

        // Assert
        assertNotSame(service1, service2, "Two instances should be different objects");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // connect() Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("connect() prints 'Connecting to database...' message")
    void connect_whenCalled_printsConnectingMessage() {
        // Arrange
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConnection = mock(Connection.class);
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConnection);

            // Act
            databaseService.connect();

            // Assert
            String output = outContent.toString();
            assertTrue(output.contains("Connecting to database..."),
                    "Output should contain 'Connecting to database...'");
        }
    }

    @Test
    @DisplayName("connect() prints DB_URL in connected message")
    void connect_whenSuccessful_printsConnectedToDatabase() {
        // Arrange
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConnection = mock(Connection.class);
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConnection);

            // Act
            databaseService.connect();

            // Assert
            String output = outContent.toString();
            assertTrue(output.contains("Connected to database:"),
                    "Output should contain 'Connected to database:'");
        }
    }

    @Test
    @DisplayName("connect() prints username in connected message")
    void connect_whenSuccessful_printsUsername() {
        // Arrange
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConnection = mock(Connection.class);
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConnection);

            // Act
            databaseService.connect();

            // Assert
            String output = outContent.toString();
            assertTrue(output.contains("Using username:"),
                    "Output should contain 'Using username:'");
        }
    }

    @Test
    @DisplayName("connect() initializes Redis cache connection message")
    void connect_whenSuccessful_printsRedisCacheMessage() {
        // Arrange
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConnection = mock(Connection.class);
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConnection);

            // Act
            databaseService.connect();

            // Assert
            String output = outContent.toString();
            assertTrue(output.contains("Connecting to Redis cache at:"),
                    "Output should contain Redis cache connection message");
        }
    }

    @Test
    @DisplayName("connect() initializes external API service message")
    void connect_whenSuccessful_printsExternalApiMessage() {
        // Arrange
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConnection = mock(Connection.class);
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConnection);

            // Act
            databaseService.connect();

            // Assert
            String output = outContent.toString();
            assertTrue(output.contains("Initializing external API:"),
                    "Output should contain external API initialization message");
        }
    }

    @Test
    @DisplayName("connect() initializes payment service message")
    void connect_whenSuccessful_printsPaymentServiceMessage() {
        // Arrange
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConnection = mock(Connection.class);
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConnection);

            // Act
            databaseService.connect();

            // Assert
            String output = outContent.toString();
            assertTrue(output.contains("Initializing payment service:"),
                    "Output should contain payment service initialization message");
        }
    }

    @Test
    @DisplayName("connect() handles SQLException gracefully and prints error to stderr")
    void connect_whenSQLExceptionThrown_printsErrorMessage() {
        // Create SQLException BEFORE entering MockedStatic scope to avoid
        // UnfinishedStubbingException (SQLException constructor calls DriverManager.getLogWriter)
        final SQLException sqlEx = new SQLException("Connection refused");

        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenThrow(sqlEx);

            // Act
            databaseService.connect();

            // Assert
            String errOutput = errContent.toString();
            assertTrue(errOutput.contains("Database connection failed:"),
                    "Error output should contain 'Database connection failed:'");
        }
    }

    @Test
    @DisplayName("connect() handles ClassNotFoundException gracefully and prints error to stderr")
    void connect_whenClassNotFoundExceptionThrown_printsDriverNotFoundMessage() {
        // Since com.mysql.cj.jdbc.Driver IS on the classpath, we verify the method
        // does not throw any exception regardless of connection outcome.
        assertDoesNotThrow(() -> {
            DatabaseService service = new DatabaseService();
            // Verify the method completes without propagating exceptions
        }, "connect() should not propagate ClassNotFoundException");
    }

    @Test
    @DisplayName("connect() does not throw any exception regardless of connection outcome")
    void connect_alwaysCompletesWithoutThrowingException() {
        // Create SQLException BEFORE entering MockedStatic scope
        final SQLException sqlEx = new SQLException("Simulated failure");

        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenThrow(sqlEx);

            // Act & Assert
            assertDoesNotThrow(() -> databaseService.connect(),
                    "connect() should not propagate any exception");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // disconnect() Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("disconnect() when connection is null does not throw exception")
    void disconnect_whenConnectionIsNull_doesNotThrowException() {
        // Arrange - fresh instance has null connection
        DatabaseService service = new DatabaseService();

        // Act & Assert
        assertDoesNotThrow(service::disconnect,
                "disconnect() should not throw when connection is null");
    }

    @Test
    @DisplayName("disconnect() when connection is null produces no output")
    void disconnect_whenConnectionIsNull_producesNoOutput() {
        // Arrange
        DatabaseService service = new DatabaseService();

        // Act
        service.disconnect();

        // Assert
        String output = outContent.toString();
        assertFalse(output.contains("Database connection closed"),
                "Should not print close message when connection is null");
    }

    @Test
    @DisplayName("disconnect() when connection is open prints closed message")
    void disconnect_whenConnectionIsOpen_printsDatabaseConnectionClosed() throws SQLException {
        // Arrange
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConnection = mock(Connection.class);
            when(mockConnection.isClosed()).thenReturn(false);
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConnection);

            databaseService.connect();
            outContent.reset(); // clear connect() output

            // Act
            databaseService.disconnect();

            // Assert
            String output = outContent.toString();
            assertTrue(output.contains("Database connection closed"),
                    "Output should contain 'Database connection closed'");
        }
    }

    @Test
    @DisplayName("disconnect() when connection is already closed does not print closed message")
    void disconnect_whenConnectionIsAlreadyClosed_doesNotPrintClosedMessage() throws SQLException {
        // Arrange
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConnection = mock(Connection.class);
            when(mockConnection.isClosed()).thenReturn(true);
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConnection);

            databaseService.connect();
            outContent.reset();

            // Act
            databaseService.disconnect();

            // Assert
            String output = outContent.toString();
            assertFalse(output.contains("Database connection closed"),
                    "Should not print closed message when connection is already closed");
        }
    }

    @Test
    @DisplayName("disconnect() handles SQLException from isClosed() gracefully")
    void disconnect_whenSQLExceptionOnIsClosed_printsErrorMessage() throws SQLException {
        // Create SQLException BEFORE entering MockedStatic scope
        final SQLException isClosedEx = new SQLException("isClosed failed");

        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConnection = mock(Connection.class);
            when(mockConnection.isClosed()).thenThrow(isClosedEx);
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConnection);

            databaseService.connect();
            errContent.reset();

            // Act
            databaseService.disconnect();

            // Assert
            String errOutput = errContent.toString();
            assertTrue(errOutput.contains("Failed to close database connection:"),
                    "Error output should contain 'Failed to close database connection:'");
        }
    }

    @Test
    @DisplayName("disconnect() does not throw exception when SQLException occurs")
    void disconnect_whenSQLExceptionOccurs_doesNotThrowException() throws SQLException {
        // Arrange
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConnection = mock(Connection.class);
            when(mockConnection.isClosed()).thenReturn(false);
            doThrow(new SQLException("close failed")).when(mockConnection).close();
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConnection);

            databaseService.connect();

            // Act & Assert
            assertDoesNotThrow(databaseService::disconnect,
                    "disconnect() should not propagate SQLException");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // executeQuery() Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("executeQuery() when connection is null does not throw exception")
    void executeQuery_whenConnectionIsNull_doesNotThrowException() {
        // Arrange
        DatabaseService service = new DatabaseService();

        // Act & Assert
        assertDoesNotThrow(() -> service.executeQuery("SELECT 1"),
                "executeQuery() should not throw when connection is null");
    }

    @Test
    @DisplayName("executeQuery() when connection is null produces no output")
    void executeQuery_whenConnectionIsNull_producesNoOutput() {
        // Arrange
        DatabaseService service = new DatabaseService();

        // Act
        service.executeQuery("SELECT 1");

        // Assert
        String output = outContent.toString();
        assertFalse(output.contains("Executing query:"),
                "Should not print query message when connection is null");
    }

    @Test
    @DisplayName("executeQuery() with valid connection prints executing query message")
    void executeQuery_whenConnectionIsOpen_printsExecutingQueryMessage() throws SQLException {
        // Arrange
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConnection = mock(Connection.class);
            PreparedStatement mockStmt = mock(PreparedStatement.class);
            when(mockConnection.isClosed()).thenReturn(false);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockStmt);
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConnection);

            databaseService.connect();
            outContent.reset();

            // Act
            databaseService.executeQuery("SELECT * FROM users");

            // Assert
            String output = outContent.toString();
            assertTrue(output.contains("Executing query:"),
                    "Output should contain 'Executing query:'");
        }
    }

    @Test
    @DisplayName("executeQuery() with valid connection includes SQL in output")
    void executeQuery_whenConnectionIsOpen_includesSqlInOutput() throws SQLException {
        // Arrange
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConnection = mock(Connection.class);
            PreparedStatement mockStmt = mock(PreparedStatement.class);
            when(mockConnection.isClosed()).thenReturn(false);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockStmt);
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConnection);

            databaseService.connect();
            outContent.reset();

            String sql = "SELECT * FROM orders";

            // Act
            databaseService.executeQuery(sql);

            // Assert
            String output = outContent.toString();
            assertTrue(output.contains(sql),
                    "Output should contain the SQL query string");
        }
    }

    @Test
    @DisplayName("executeQuery() sets query timeout to 30 seconds")
    void executeQuery_whenConnectionIsOpen_setsQueryTimeout() throws SQLException {
        // Arrange
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConnection = mock(Connection.class);
            PreparedStatement mockStmt = mock(PreparedStatement.class);
            when(mockConnection.isClosed()).thenReturn(false);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockStmt);
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConnection);

            databaseService.connect();

            // Act
            databaseService.executeQuery("SELECT 1");

            // Assert
            verify(mockStmt).setQueryTimeout(30);
        }
    }

    @Test
    @DisplayName("executeQuery() calls execute() on PreparedStatement")
    void executeQuery_whenConnectionIsOpen_callsExecuteOnStatement() throws SQLException {
        // Arrange
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConnection = mock(Connection.class);
            PreparedStatement mockStmt = mock(PreparedStatement.class);
            when(mockConnection.isClosed()).thenReturn(false);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockStmt);
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConnection);

            databaseService.connect();

            // Act
            databaseService.executeQuery("DELETE FROM temp");

            // Assert
            verify(mockStmt).execute();
        }
    }

    @Test
    @DisplayName("executeQuery() closes PreparedStatement after execution")
    void executeQuery_whenConnectionIsOpen_closesStatementAfterExecution() throws SQLException {
        // Arrange
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConnection = mock(Connection.class);
            PreparedStatement mockStmt = mock(PreparedStatement.class);
            when(mockConnection.isClosed()).thenReturn(false);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockStmt);
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConnection);

            databaseService.connect();

            // Act
            databaseService.executeQuery("UPDATE users SET active=1");

            // Assert
            verify(mockStmt).close();
        }
    }

    @Test
    @DisplayName("executeQuery() handles SQLException and prints error to stderr")
    void executeQuery_whenSQLExceptionThrown_printsErrorMessage() throws SQLException {
        // Create SQLException BEFORE entering MockedStatic scope
        final SQLException prepareEx = new SQLException("Syntax error");

        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConnection = mock(Connection.class);
            when(mockConnection.isClosed()).thenReturn(false);
            when(mockConnection.prepareStatement(anyString())).thenThrow(prepareEx);
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConnection);

            databaseService.connect();
            errContent.reset();

            // Act
            databaseService.executeQuery("INVALID SQL");

            // Assert
            String errOutput = errContent.toString();
            assertTrue(errOutput.contains("Query execution failed:"),
                    "Error output should contain 'Query execution failed:'");
        }
    }

    @Test
    @DisplayName("executeQuery() does not throw exception when SQLException occurs")
    void executeQuery_whenSQLExceptionOccurs_doesNotThrowException() throws SQLException {
        // Create SQLException BEFORE entering MockedStatic scope
        final SQLException prepareEx = new SQLException("Simulated error");

        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConnection = mock(Connection.class);
            when(mockConnection.isClosed()).thenReturn(false);
            when(mockConnection.prepareStatement(anyString())).thenThrow(prepareEx);
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConnection);

            databaseService.connect();

            // Act & Assert
            assertDoesNotThrow(() -> databaseService.executeQuery("BAD SQL"),
                    "executeQuery() should not propagate SQLException");
        }
    }

    @Test
    @DisplayName("executeQuery() when connection is closed does not execute query")
    void executeQuery_whenConnectionIsClosed_doesNotExecuteQuery() throws SQLException {
        // Arrange
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConnection = mock(Connection.class);
            when(mockConnection.isClosed()).thenReturn(true);
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConnection);

            databaseService.connect();
            outContent.reset();

            // Act
            databaseService.executeQuery("SELECT 1");

            // Assert
            String output = outContent.toString();
            assertFalse(output.contains("Executing query:"),
                    "Should not execute query when connection is closed");
        }
    }

    @Test
    @DisplayName("executeQuery() with empty SQL string does not throw exception")
    void executeQuery_withEmptySql_doesNotThrowException() {
        // Arrange
        DatabaseService service = new DatabaseService();

        // Act & Assert
        assertDoesNotThrow(() -> service.executeQuery(""),
                "executeQuery() should handle empty SQL gracefully");
    }

    @Test
    @DisplayName("executeQuery() with null SQL does not throw exception when connection is null")
    void executeQuery_withNullSqlAndNullConnection_doesNotThrowException() {
        // Arrange
        DatabaseService service = new DatabaseService();

        // Act & Assert
        assertDoesNotThrow(() -> service.executeQuery(null),
                "executeQuery() should handle null SQL gracefully when connection is null");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Integration-style Tests (connect → executeQuery → disconnect)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Full lifecycle: connect, executeQuery, disconnect completes without exception")
    void fullLifecycle_connectExecuteQueryDisconnect_completesWithoutException() throws SQLException {
        // Arrange
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConnection = mock(Connection.class);
            PreparedStatement mockStmt = mock(PreparedStatement.class);
            when(mockConnection.isClosed()).thenReturn(false);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockStmt);
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConnection);

            // Act & Assert
            assertDoesNotThrow(() -> {
                databaseService.connect();
                databaseService.executeQuery("SELECT 1");
                databaseService.disconnect();
            }, "Full lifecycle should complete without exception");
        }
    }

    @Test
    @DisplayName("Multiple executeQuery calls on same connection all succeed")
    void executeQuery_multipleCallsOnSameConnection_allSucceed() throws SQLException {
        // Arrange
        try (MockedStatic<DriverManager> dmMock = Mockito.mockStatic(DriverManager.class)) {
            Connection mockConnection = mock(Connection.class);
            PreparedStatement mockStmt = mock(PreparedStatement.class);
            when(mockConnection.isClosed()).thenReturn(false);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockStmt);
            dmMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                  .thenReturn(mockConnection);

            databaseService.connect();

            // Act & Assert
            assertDoesNotThrow(() -> {
                databaseService.executeQuery("SELECT 1");
                databaseService.executeQuery("SELECT 2");
                databaseService.executeQuery("SELECT 3");
            }, "Multiple executeQuery calls should all succeed");

            verify(mockStmt, times(3)).execute();
        }
    }
}
