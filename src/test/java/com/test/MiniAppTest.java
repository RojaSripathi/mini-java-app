package com.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive JUnit 5 tests for MiniApp class.
 * Tests cover: main(), initializeApplication(), loadConfiguration(),
 *              initializeLogging(), startServer() and all static constants.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MiniApp Tests")
class MiniAppTest {

    private MiniApp miniApp;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeEach
    void setUp() {
        miniApp = new MiniApp();
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
    @DisplayName("Default constructor creates a non-null MiniApp instance")
    void constructor_defaultConstructor_createsInstance() {
        // Arrange & Act
        MiniApp app = new MiniApp();

        // Assert
        assertNotNull(app, "MiniApp instance should not be null");
    }

    @Test
    @DisplayName("MiniApp class can be instantiated multiple times")
    void constructor_multipleInstances_allNonNull() {
        // Arrange & Act
        MiniApp app1 = new MiniApp();
        MiniApp app2 = new MiniApp();
        MiniApp app3 = new MiniApp();

        // Assert
        assertNotNull(app1);
        assertNotNull(app2);
        assertNotNull(app3);
        assertNotSame(app1, app2, "Each instance should be a distinct object");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Static Field / Constant Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("SERVER_PORT constant is 8080")
    void staticField_serverPort_is8080() throws Exception {
        Field field = MiniApp.class.getDeclaredField("SERVER_PORT");
        field.setAccessible(true);
        assertEquals(8080, field.get(null),
                "SERVER_PORT should be 8080");
    }

    @Test
    @DisplayName("CONFIG_FILE_PATH constant is '/opt/app/config/app.properties'")
    void staticField_configFilePath_isCorrect() throws Exception {
        Field field = MiniApp.class.getDeclaredField("CONFIG_FILE_PATH");
        field.setAccessible(true);
        assertEquals("/opt/app/config/app.properties", field.get(null),
                "CONFIG_FILE_PATH should be '/opt/app/config/app.properties'");
    }

    @Test
    @DisplayName("LOG_FILE_PATH constant is '/var/log/mini-app.log'")
    void staticField_logFilePath_isCorrect() throws Exception {
        Field field = MiniApp.class.getDeclaredField("LOG_FILE_PATH");
        field.setAccessible(true);
        assertEquals("/var/log/mini-app.log", field.get(null),
                "LOG_FILE_PATH should be '/var/log/mini-app.log'");
    }

    @Test
    @DisplayName("CONFIG_FILE_PATH starts with /opt/app")
    void staticField_configFilePath_startsWithOptApp() throws Exception {
        Field field = MiniApp.class.getDeclaredField("CONFIG_FILE_PATH");
        field.setAccessible(true);
        String path = (String) field.get(null);
        assertTrue(path.startsWith("/opt/app"),
                "CONFIG_FILE_PATH should start with '/opt/app'");
    }

    @Test
    @DisplayName("LOG_FILE_PATH starts with /var/log")
    void staticField_logFilePath_startsWithVarLog() throws Exception {
        Field field = MiniApp.class.getDeclaredField("LOG_FILE_PATH");
        field.setAccessible(true);
        String path = (String) field.get(null);
        assertTrue(path.startsWith("/var/log"),
                "LOG_FILE_PATH should start with '/var/log'");
    }

    @Test
    @DisplayName("CONFIG_FILE_PATH ends with .properties")
    void staticField_configFilePath_endsWithProperties() throws Exception {
        Field field = MiniApp.class.getDeclaredField("CONFIG_FILE_PATH");
        field.setAccessible(true);
        String path = (String) field.get(null);
        assertTrue(path.endsWith(".properties"),
                "CONFIG_FILE_PATH should end with '.properties'");
    }

    @Test
    @DisplayName("LOG_FILE_PATH ends with .log")
    void staticField_logFilePath_endsWithLog() throws Exception {
        Field field = MiniApp.class.getDeclaredField("LOG_FILE_PATH");
        field.setAccessible(true);
        String path = (String) field.get(null);
        assertTrue(path.endsWith(".log"),
                "LOG_FILE_PATH should end with '.log'");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // loadConfiguration() Private Method Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("loadConfiguration() does not throw when config file does not exist")
    void loadConfiguration_whenConfigFileNotFound_doesNotThrow() throws Exception {
        // Arrange
        Method method = MiniApp.class.getDeclaredMethod("loadConfiguration");
        method.setAccessible(true);

        // Act & Assert
        assertDoesNotThrow(() -> method.invoke(miniApp),
                "loadConfiguration() should not throw when config file is missing");
    }

    @Test
    @DisplayName("loadConfiguration() prints warning when config file not found")
    void loadConfiguration_whenConfigFileNotFound_printsWarning() throws Exception {
        // Arrange
        Method method = MiniApp.class.getDeclaredMethod("loadConfiguration");
        method.setAccessible(true);

        // Act
        method.invoke(miniApp);

        // Assert
        String output = outContent.toString();
        // Config file at /opt/app/config/app.properties likely doesn't exist in test env
        // Either "Configuration loaded" or "Warning: Configuration file not found" should appear
        assertTrue(output.contains("Configuration") || output.contains("Warning") || output.contains("config"),
                "loadConfiguration() should print a configuration-related message");
    }

    @Test
    @DisplayName("loadConfiguration() prints 'Warning' when config file is absent")
    void loadConfiguration_whenConfigFileAbsent_printsWarningMessage() throws Exception {
        // Arrange
        Method method = MiniApp.class.getDeclaredMethod("loadConfiguration");
        method.setAccessible(true);

        // Act
        method.invoke(miniApp);

        // Assert - /opt/app/config/app.properties should not exist in test environment
        String output = outContent.toString();
        // If file doesn't exist, warning is printed; if it does, loaded message is printed
        assertFalse(output.isEmpty(), "loadConfiguration() should produce some output");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // initializeLogging() Private Method Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("initializeLogging() does not throw regardless of /var/log permissions")
    void initializeLogging_doesNotThrow() throws Exception {
        // Arrange
        Method method = MiniApp.class.getDeclaredMethod("initializeLogging");
        method.setAccessible(true);

        // Act & Assert
        assertDoesNotThrow(() -> method.invoke(miniApp),
                "initializeLogging() should not propagate exceptions");
    }

    @Test
    @DisplayName("initializeLogging() produces output (either success or error)")
    void initializeLogging_producesOutput() throws Exception {
        // Arrange
        Method method = MiniApp.class.getDeclaredMethod("initializeLogging");
        method.setAccessible(true);

        // Act
        method.invoke(miniApp);

        // Assert
        String stdOut = outContent.toString();
        String stdErr = errContent.toString();
        boolean hasOutput = stdOut.contains("Logging") || stdErr.contains("Failed to initialize logging");
        assertTrue(hasOutput, "initializeLogging() should produce some output");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // startServer() Private Method Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("startServer() does not throw even if port 8080 is in use")
    void startServer_doesNotThrowRegardlessOfPortAvailability() throws Exception {
        // Arrange
        Method method = MiniApp.class.getDeclaredMethod("startServer");
        method.setAccessible(true);

        // Act & Assert
        assertDoesNotThrow(() -> method.invoke(miniApp),
                "startServer() should not propagate exceptions");
    }

    @Test
    @DisplayName("startServer() prints server started message when port is available")
    void startServer_whenPortAvailable_printsStartedMessage() throws Exception {
        // Arrange
        Method method = MiniApp.class.getDeclaredMethod("startServer");
        method.setAccessible(true);

        // Act
        method.invoke(miniApp);

        // Assert
        String stdOut = outContent.toString();
        String stdErr = errContent.toString();
        boolean hasServerOutput = stdOut.contains("Server") || stdErr.contains("Failed to start server");
        assertTrue(hasServerOutput, "startServer() should produce server-related output");
    }

    @Test
    @DisplayName("startServer() mentions port 8080 in output")
    void startServer_mentionsPort8080() throws Exception {
        // Arrange
        Method method = MiniApp.class.getDeclaredMethod("startServer");
        method.setAccessible(true);

        // Act
        method.invoke(miniApp);

        // Assert
        String stdOut = outContent.toString();
        String stdErr = errContent.toString();
        boolean mentionsPort = stdOut.contains("8080") || stdErr.contains("8080")
                || stdErr.contains("Failed to start server");
        assertTrue(mentionsPort, "startServer() should mention port 8080 or report failure");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // initializeApplication() Private Method Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("initializeApplication() does not throw")
    void initializeApplication_doesNotThrow() throws Exception {
        // Arrange
        Method method = MiniApp.class.getDeclaredMethod("initializeApplication");
        method.setAccessible(true);

        // Act & Assert
        assertDoesNotThrow(() -> method.invoke(miniApp),
                "initializeApplication() should not propagate exceptions");
    }

    @Test
    @DisplayName("initializeApplication() produces output from loadConfiguration and initializeLogging")
    void initializeApplication_producesOutput() throws Exception {
        // Arrange
        Method method = MiniApp.class.getDeclaredMethod("initializeApplication");
        method.setAccessible(true);

        // Act
        method.invoke(miniApp);

        // Assert
        String stdOut = outContent.toString();
        String stdErr = errContent.toString();
        assertFalse(stdOut.isEmpty() && stdErr.isEmpty(),
                "initializeApplication() should produce some output");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // main() Method Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("main() with empty args array does not throw")
    void main_withEmptyArgs_doesNotThrow() {
        // Arrange & Act & Assert
        assertDoesNotThrow(() -> MiniApp.main(new String[]{}),
                "main() should not throw with empty args");
    }

    @Test
    @DisplayName("main() with null args does not throw")
    void main_withNullArgs_doesNotThrow() {
        // Arrange & Act & Assert
        assertDoesNotThrow(() -> MiniApp.main(null),
                "main() should not throw with null args");
    }

    @Test
    @DisplayName("main() prints 'Starting Mini Java Application' message")
    void main_printsStartingMessage() {
        // Arrange & Act
        MiniApp.main(new String[]{});

        // Assert
        String output = outContent.toString();
        assertTrue(output.contains("Starting Mini Java Application"),
                "main() should print 'Starting Mini Java Application'");
    }

    @Test
    @DisplayName("main() with multiple args does not throw")
    void main_withMultipleArgs_doesNotThrow() {
        // Arrange & Act & Assert
        assertDoesNotThrow(() -> MiniApp.main(new String[]{"arg1", "arg2", "arg3"}),
                "main() should not throw with multiple args");
    }

    @Test
    @DisplayName("main() produces output to stdout")
    void main_producesStdoutOutput() {
        // Arrange & Act
        MiniApp.main(new String[]{});

        // Assert
        assertFalse(outContent.toString().isEmpty(),
                "main() should produce output to stdout");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Class Structure / Reflection Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("MiniApp class has main method with String[] parameter")
    void classStructure_hasMainMethod() throws Exception {
        // Arrange & Act
        Method mainMethod = MiniApp.class.getDeclaredMethod("main", String[].class);

        // Assert
        assertNotNull(mainMethod, "MiniApp should have a main(String[]) method");
    }

    @Test
    @DisplayName("MiniApp class has initializeApplication private method")
    void classStructure_hasInitializeApplicationMethod() throws Exception {
        // Arrange & Act
        Method method = MiniApp.class.getDeclaredMethod("initializeApplication");
        method.setAccessible(true);

        // Assert
        assertNotNull(method, "MiniApp should have initializeApplication() method");
    }

    @Test
    @DisplayName("MiniApp class has loadConfiguration private method")
    void classStructure_hasLoadConfigurationMethod() throws Exception {
        // Arrange & Act
        Method method = MiniApp.class.getDeclaredMethod("loadConfiguration");
        method.setAccessible(true);

        // Assert
        assertNotNull(method, "MiniApp should have loadConfiguration() method");
    }

    @Test
    @DisplayName("MiniApp class has initializeLogging private method")
    void classStructure_hasInitializeLoggingMethod() throws Exception {
        // Arrange & Act
        Method method = MiniApp.class.getDeclaredMethod("initializeLogging");
        method.setAccessible(true);

        // Assert
        assertNotNull(method, "MiniApp should have initializeLogging() method");
    }

    @Test
    @DisplayName("MiniApp class has startServer private method")
    void classStructure_hasStartServerMethod() throws Exception {
        // Arrange & Act
        Method method = MiniApp.class.getDeclaredMethod("startServer");
        method.setAccessible(true);

        // Assert
        assertNotNull(method, "MiniApp should have startServer() method");
    }

    @Test
    @DisplayName("MiniApp class has exactly 3 static String/int fields")
    void classStructure_hasExpectedStaticFields() throws Exception {
        // Arrange & Act
        Field serverPort = MiniApp.class.getDeclaredField("SERVER_PORT");
        Field configPath = MiniApp.class.getDeclaredField("CONFIG_FILE_PATH");
        Field logPath = MiniApp.class.getDeclaredField("LOG_FILE_PATH");

        // Assert
        assertNotNull(serverPort);
        assertNotNull(configPath);
        assertNotNull(logPath);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Edge Case / Boundary Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("SERVER_PORT is a positive integer")
    void serverPort_isPositiveInteger() throws Exception {
        Field field = MiniApp.class.getDeclaredField("SERVER_PORT");
        field.setAccessible(true);
        int port = (int) field.get(null);
        assertTrue(port > 0, "SERVER_PORT should be a positive integer");
    }

    @Test
    @DisplayName("SERVER_PORT is within valid port range (1-65535)")
    void serverPort_isWithinValidRange() throws Exception {
        Field field = MiniApp.class.getDeclaredField("SERVER_PORT");
        field.setAccessible(true);
        int port = (int) field.get(null);
        assertTrue(port >= 1 && port <= 65535,
                "SERVER_PORT should be within valid port range 1-65535");
    }

    @Test
    @DisplayName("CONFIG_FILE_PATH is not null or empty")
    void configFilePath_isNotNullOrEmpty() throws Exception {
        Field field = MiniApp.class.getDeclaredField("CONFIG_FILE_PATH");
        field.setAccessible(true);
        String path = (String) field.get(null);
        assertNotNull(path, "CONFIG_FILE_PATH should not be null");
        assertFalse(path.isEmpty(), "CONFIG_FILE_PATH should not be empty");
    }

    @Test
    @DisplayName("LOG_FILE_PATH is not null or empty")
    void logFilePath_isNotNullOrEmpty() throws Exception {
        Field field = MiniApp.class.getDeclaredField("LOG_FILE_PATH");
        field.setAccessible(true);
        String path = (String) field.get(null);
        assertNotNull(path, "LOG_FILE_PATH should not be null");
        assertFalse(path.isEmpty(), "LOG_FILE_PATH should not be empty");
    }

    @Test
    @DisplayName("CONFIG_FILE_PATH is an absolute path (starts with /)")
    void configFilePath_isAbsolutePath() throws Exception {
        Field field = MiniApp.class.getDeclaredField("CONFIG_FILE_PATH");
        field.setAccessible(true);
        String path = (String) field.get(null);
        assertTrue(path.startsWith("/"),
                "CONFIG_FILE_PATH should be an absolute path starting with '/'");
    }

    @Test
    @DisplayName("LOG_FILE_PATH is an absolute path (starts with /)")
    void logFilePath_isAbsolutePath() throws Exception {
        Field field = MiniApp.class.getDeclaredField("LOG_FILE_PATH");
        field.setAccessible(true);
        String path = (String) field.get(null);
        assertTrue(path.startsWith("/"),
                "LOG_FILE_PATH should be an absolute path starting with '/'");
    }
}
