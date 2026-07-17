package com.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive JUnit 5 tests for {@link DatabaseService}.
 *
 * <p>Covers:
 * <ul>
 *   <li>connect() – success path, SQLException path</li>
 *   <li>executeQuery() – null connection, closed connection, open connection, SQLException</li>
 *   <li>disconnect() – null connection, closed connection, open connection, SQLException</li>
 *   <li>Private helpers (connectToCache, initializeExternalServices) exercised via connect()</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DatabaseService Tests")
class DatabaseServiceTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Inject a {@link Connection} directly into the private field. */
    private void setConnection(DatabaseService service, Connection conn) throws Exception {
        Field field = DatabaseService.class.getDeclaredField("connection");
        field.setAccessible(true);
        field.set(service, conn);
    }

    /** Read the private {@code connection} field. */
    private Connection getConnection(DatabaseService service) throws Exception {
        Field field = DatabaseService.class.getDeclaredField("connection");
        field.setAccessible(true);
        return (Connection) field.get(service);
    }

    // -----------------------------------------------------------------------
    // connect()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("connect() – successful connection sets connection field")
    void connect_successfulConnection_setsConnectionField() throws Exception {
        // Arrange
        DatabaseService service = new DatabaseService();
        Connection mockConn = mock(Connection.class);

        try (MockedStatic<DriverManager> dmStatic = Mockito.mockStatic(DriverManager.class)) {
            dmStatic.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenReturn(mockConn);

            // Act
            service.connect();

            // Assert
            Connection actual = getConnection(service);
            assertNotNull(actual, "Connection should be set after successful connect()");
            assertSame(mockConn, actual, "Connection should be the mock returned by DriverManager");
        }
    }

    @Test
    @DisplayName("connect() – SQLException is caught and does not propagate")
    void connect_sqlException_doesNotPropagate() {
        // Arrange
        DatabaseService service = new DatabaseService();
        // Build the exception BEFORE entering the MockedStatic scope to avoid
        // triggering DriverManager.getLogWriter() inside the mock scope.
        SQLException ex = new SQLException("Connection refused");

        try (MockedStatic<DriverManager> dmStatic = Mockito.mockStatic(DriverManager.class)) {
            dmStatic.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenThrow(ex);

            // Act & Assert – must not throw
            assertDoesNotThrow(service::connect,
                    "connect() must swallow SQLException and not propagate it");
        }
    }

    @Test
    @DisplayName("connect() – calls DriverManager with correct URL, user, password")
    void connect_callsDriverManagerWithCorrectCredentials() {
        // Arrange
        DatabaseService service = new DatabaseService();
        Connection mockConn = mock(Connection.class);

        try (MockedStatic<DriverManager> dmStatic = Mockito.mockStatic(DriverManager.class)) {
            dmStatic.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenReturn(mockConn);

            // Act
            service.connect();

            // Assert – verify the exact URL / credentials used
            dmStatic.verify(() -> DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/mini_app_db", "root", "password123"));
        }
    }

    // -----------------------------------------------------------------------
    // executeQuery()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("executeQuery() – null connection skips execution silently")
    void executeQuery_nullConnection_doesNotThrow() throws Exception {
        // Arrange
        DatabaseService service = new DatabaseService();
        setConnection(service, null);

        // Act & Assert
        assertDoesNotThrow(() -> service.executeQuery("SELECT 1"),
                "executeQuery() must not throw when connection is null");
    }

    @Test
    @DisplayName("executeQuery() – closed connection skips execution silently")
    void executeQuery_closedConnection_doesNotThrow() throws Exception {
        // Arrange
        DatabaseService service = new DatabaseService();
        Connection mockConn = mock(Connection.class);
        when(mockConn.isClosed()).thenReturn(true);
        setConnection(service, mockConn);

        // Act & Assert
        assertDoesNotThrow(() -> service.executeQuery("SELECT 1"),
                "executeQuery() must not throw when connection is closed");
    }

    @Test
    @DisplayName("executeQuery() – open connection executes statement")
    void executeQuery_openConnection_executesStatement() throws Exception {
        // Arrange
        DatabaseService service = new DatabaseService();
        Connection mockConn = mock(Connection.class);
        PreparedStatement mockStmt = mock(PreparedStatement.class);

        when(mockConn.isClosed()).thenReturn(false);
        when(mockConn.prepareStatement(anyString())).thenReturn(mockStmt);
        setConnection(service, mockConn);

        // Act
        service.executeQuery("SELECT * FROM users");

        // Assert
        verify(mockConn).prepareStatement("SELECT * FROM users");
        verify(mockStmt).setQueryTimeout(30);
        verify(mockStmt).execute();
    }

    @Test
    @DisplayName("executeQuery() – empty SQL string is still forwarded to prepareStatement")
    void executeQuery_emptySql_forwardedToPrepareStatement() throws Exception {
        // Arrange
        DatabaseService service = new DatabaseService();
        Connection mockConn = mock(Connection.class);
        PreparedStatement mockStmt = mock(PreparedStatement.class);

        when(mockConn.isClosed()).thenReturn(false);
        when(mockConn.prepareStatement(anyString())).thenReturn(mockStmt);
        setConnection(service, mockConn);

        // Act
        service.executeQuery("");

        // Assert
        verify(mockConn).prepareStatement("");
    }

    @Test
    @DisplayName("executeQuery() – SQLException from prepareStatement is caught and does not propagate")
    void executeQuery_sqlExceptionFromPrepare_doesNotPropagate() throws Exception {
        // Arrange
        DatabaseService service = new DatabaseService();
        Connection mockConn = mock(Connection.class);

        when(mockConn.isClosed()).thenReturn(false);
        when(mockConn.prepareStatement(anyString())).thenThrow(new SQLException("Syntax error"));
        setConnection(service, mockConn);

        // Act & Assert
        assertDoesNotThrow(() -> service.executeQuery("INVALID SQL"),
                "executeQuery() must swallow SQLException");
    }

    @Test
    @DisplayName("executeQuery() – SQLException from isClosed is caught and does not propagate")
    void executeQuery_sqlExceptionFromIsClosed_doesNotPropagate() throws Exception {
        // Arrange
        DatabaseService service = new DatabaseService();
        Connection mockConn = mock(Connection.class);

        when(mockConn.isClosed()).thenThrow(new SQLException("Connection broken"));
        setConnection(service, mockConn);

        // Act & Assert
        assertDoesNotThrow(() -> service.executeQuery("SELECT 1"),
                "executeQuery() must swallow SQLException from isClosed()");
    }

    @Test
    @DisplayName("executeQuery() – query timeout is always set to 30 seconds")
    void executeQuery_queryTimeoutSetTo30() throws Exception {
        // Arrange
        DatabaseService service = new DatabaseService();
        Connection mockConn = mock(Connection.class);
        PreparedStatement mockStmt = mock(PreparedStatement.class);

        when(mockConn.isClosed()).thenReturn(false);
        when(mockConn.prepareStatement(anyString())).thenReturn(mockStmt);
        setConnection(service, mockConn);

        // Act
        service.executeQuery("SELECT 1");

        // Assert
        verify(mockStmt).setQueryTimeout(30);
    }

    // -----------------------------------------------------------------------
    // disconnect()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("disconnect() – null connection does not throw")
    void disconnect_nullConnection_doesNotThrow() throws Exception {
        // Arrange
        DatabaseService service = new DatabaseService();
        setConnection(service, null);

        // Act & Assert
        assertDoesNotThrow(service::disconnect,
                "disconnect() must not throw when connection is null");
    }

    @Test
    @DisplayName("disconnect() – already closed connection does not call close() again")
    void disconnect_alreadyClosedConnection_doesNotCallClose() throws Exception {
        // Arrange
        DatabaseService service = new DatabaseService();
        Connection mockConn = mock(Connection.class);
        when(mockConn.isClosed()).thenReturn(true);
        setConnection(service, mockConn);

        // Act
        service.disconnect();

        // Assert
        verify(mockConn, never()).close();
    }

    @Test
    @DisplayName("disconnect() – open connection is closed")
    void disconnect_openConnection_closesConnection() throws Exception {
        // Arrange
        DatabaseService service = new DatabaseService();
        Connection mockConn = mock(Connection.class);
        when(mockConn.isClosed()).thenReturn(false);
        setConnection(service, mockConn);

        // Act
        service.disconnect();

        // Assert
        verify(mockConn).close();
    }

    @Test
    @DisplayName("disconnect() – SQLException from close() is caught and does not propagate")
    void disconnect_sqlExceptionFromClose_doesNotPropagate() throws Exception {
        // Arrange
        DatabaseService service = new DatabaseService();
        Connection mockConn = mock(Connection.class);
        when(mockConn.isClosed()).thenReturn(false);
        doThrow(new SQLException("Close failed")).when(mockConn).close();
        setConnection(service, mockConn);

        // Act & Assert
        assertDoesNotThrow(service::disconnect,
                "disconnect() must swallow SQLException from close()");
    }

    @Test
    @DisplayName("disconnect() – SQLException from isClosed() is caught and does not propagate")
    void disconnect_sqlExceptionFromIsClosed_doesNotPropagate() throws Exception {
        // Arrange
        DatabaseService service = new DatabaseService();
        Connection mockConn = mock(Connection.class);
        when(mockConn.isClosed()).thenThrow(new SQLException("Connection broken"));
        setConnection(service, mockConn);

        // Act & Assert
        assertDoesNotThrow(service::disconnect,
                "disconnect() must swallow SQLException from isClosed()");
    }

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Default constructor creates instance with null connection")
    void constructor_defaultConstructor_connectionIsNull() throws Exception {
        // Act
        DatabaseService service = new DatabaseService();

        // Assert
        assertNotNull(service, "DatabaseService instance must not be null");
        assertNull(getConnection(service), "Initial connection must be null");
    }

    // -----------------------------------------------------------------------
    // connect() then disconnect() lifecycle
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Full lifecycle: connect then disconnect closes connection")
    void lifecycle_connectThenDisconnect_closesConnection() throws Exception {
        // Arrange
        DatabaseService service = new DatabaseService();
        Connection mockConn = mock(Connection.class);

        try (MockedStatic<DriverManager> dmStatic = Mockito.mockStatic(DriverManager.class)) {
            dmStatic.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenReturn(mockConn);
            when(mockConn.isClosed()).thenReturn(false);

            // Act
            service.connect();
            service.disconnect();

            // Assert
            verify(mockConn).close();
        }
    }

    // -----------------------------------------------------------------------
    // executeQuery() with multiple SQL statements
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("executeQuery() – INSERT statement is executed")
    void executeQuery_insertStatement_executed() throws Exception {
        // Arrange
        DatabaseService service = new DatabaseService();
        Connection mockConn = mock(Connection.class);
        PreparedStatement mockStmt = mock(PreparedStatement.class);

        when(mockConn.isClosed()).thenReturn(false);
        when(mockConn.prepareStatement(anyString())).thenReturn(mockStmt);
        setConnection(service, mockConn);

        // Act
        service.executeQuery("INSERT INTO users (name) VALUES ('Alice')");

        // Assert
        verify(mockStmt).execute();
    }

    @Test
    @DisplayName("executeQuery() – UPDATE statement is executed")
    void executeQuery_updateStatement_executed() throws Exception {
        // Arrange
        DatabaseService service = new DatabaseService();
        Connection mockConn = mock(Connection.class);
        PreparedStatement mockStmt = mock(PreparedStatement.class);

        when(mockConn.isClosed()).thenReturn(false);
        when(mockConn.prepareStatement(anyString())).thenReturn(mockStmt);
        setConnection(service, mockConn);

        // Act
        service.executeQuery("UPDATE users SET name='Bob' WHERE id=1");

        // Assert
        verify(mockStmt).execute();
    }
}
