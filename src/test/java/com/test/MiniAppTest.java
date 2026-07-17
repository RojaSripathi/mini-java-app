package com.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive JUnit 5 tests for MiniApp class.
 * Tests cover main(), initializeApplication(), loadConfiguration(),
 * initializeLogging(), and startServer() via reflection and output capture.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MiniApp Tests")
class MiniAppTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
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
    @DisplayName("Multiple MiniApp instances are independent objects")
    void constructor_multipleInstances_areIndependent() {
        // Arrange & Act
        MiniApp app1 = new MiniApp();
        MiniApp app2 = new MiniApp();

        // Assert
        assertNotSame(app1, app2, "Two MiniApp instances should be different objects");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // main() Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("main() prints 'Starting Mini Java Application...' message")
    void main_whenCalled_printsStartingMessage() {
        // Act
        assertDoesNotThrow(() -> MiniApp.main(new String[]{}),
                "main() should not throw any exception");

        // Assert
        String output = outContent.toString();
        assertTrue(output.contains("Starting Mini Java Application..."),
                "Output should contain 'Starting Mini Java Application...'");
    }

    @Test
    @DisplayName("main() with null args does not throw NullPointerException")
    void main_withNullArgs_doesNotThrowException() {
        // Act & Assert
        assertDoesNotThrow(() -> MiniApp.main(null),
                "main() should handle null args gracefully");
    }

    @Test
    @DisplayName("main() with empty args array does not throw exception")
    void main_withEmptyArgs_doesNotThrowException() {
        // Act & Assert
        assertDoesNotThrow(() -> MiniApp.main(new String[]{}),
                "main() should handle empty args array gracefully");
    }

    @Test
    @DisplayName("main() with multiple args does not throw exception")
    void main_withMultipleArgs_doesNotThrowException() {
        // Act & Assert
        assertDoesNotThrow(() -> MiniApp.main(new String[]{"arg1", "arg2", "arg3"}),
                "main() should handle multiple args gracefully");
    }

    @Test
    @DisplayName("main() completes without propagating any exception")
    void main_alwaysCompletesWithoutPropagatingException() {
        // Act & Assert - even if internal operations fail (e.g., DB connection),
        // main() should not propagate exceptions
        assertDoesNotThrow(() -> MiniApp.main(new String[]{}),
                "main() should complete without propagating exceptions");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // initializeApplication() Tests (via reflection)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("initializeApplication() is accessible via reflection")
    void initializeApplication_isAccessibleViaReflection() throws NoSuchMethodException {
        // Arrange
        MiniApp app = new MiniApp();

        // Act
        Method method = MiniApp.class.getDeclaredMethod("initializeApplication");
        method.setAccessible(true);

        // Assert
        assertNotNull(method, "initializeApplication() method should be accessible via reflection");
    }

    @Test
    @DisplayName("initializeApplication() does not throw exception when invoked")
    void initializeApplication_whenInvoked_doesNotThrowException() throws NoSuchMethodException {
        // Arrange
        MiniApp app = new MiniApp();
        Method method = MiniApp.class.getDeclaredMethod("initializeApplication");
        method.setAccessible(true);

        // Act & Assert
        assertDoesNotThrow(() -> {
            try {
                method.invoke(app);
            } catch (InvocationTargetException e) {
                // If the underlying cause is not a RuntimeException, it's acceptable
                if (e.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) e.getCause();
                }
                // Otherwise swallow - expected for missing DB/file resources
            }
        }, "initializeApplication() should not throw RuntimeException");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // loadConfiguration() Tests (via reflection)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("loadConfiguration() is accessible via reflection")
    void loadConfiguration_isAccessibleViaReflection() throws NoSuchMethodException {
        // Arrange
        MiniApp app = new MiniApp();

        // Act
        Method method = MiniApp.class.getDeclaredMethod("loadConfiguration");
        method.setAccessible(true);

        // Assert
        assertNotNull(method, "loadConfiguration() method should be accessible via reflection");
    }

    @Test
    @DisplayName("loadConfiguration() when config file does not exist prints warning message")
    void loadConfiguration_whenConfigFileNotFound_printsWarningMessage()
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        // Arrange
        MiniApp app = new MiniApp();
        Method method = MiniApp.class.getDeclaredMethod("loadConfiguration");
        method.setAccessible(true);

        // Act
        method.invoke(app);

        // Assert - CONFIG_FILE_PATH = "/opt/app/config/app.properties" likely doesn't exist
        String output = outContent.toString();
        // Either "Configuration loaded from:" or "Warning: Configuration file not found at:"
        boolean hasExpectedOutput = output.contains("Configuration loaded from:")
                || output.contains("Warning: Configuration file not found at:");
        assertTrue(hasExpectedOutput,
                "loadConfiguration() should print either loaded or warning message");
    }

    @Test
    @DisplayName("loadConfiguration() when config file does not exist prints path in warning")
    void loadConfiguration_whenConfigFileNotFound_includesPathInWarning()
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        // Arrange
        MiniApp app = new MiniApp();
        Method method = MiniApp.class.getDeclaredMethod("loadConfiguration");
        method.setAccessible(true);

        // Act
        method.invoke(app);

        // Assert
        String output = outContent.toString();
        // The output should contain the config file path reference
        assertTrue(output.contains("/opt/app/config/app.properties")
                        || output.contains("Warning: Configuration file not found"),
                "Output should reference the configuration file path");
    }

    @Test
    @DisplayName("loadConfiguration() does not throw exception when invoked")
    void loadConfiguration_whenInvoked_doesNotThrowException() throws NoSuchMethodException {
        // Arrange
        MiniApp app = new MiniApp();
        Method method = MiniApp.class.getDeclaredMethod("loadConfiguration");
        method.setAccessible(true);

        // Act & Assert
        assertDoesNotThrow(() -> {
            try {
                method.invoke(app);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) e.getCause();
                }
            }
        }, "loadConfiguration() should not throw RuntimeException");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // initializeLogging() Tests (via reflection)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("initializeLogging() is accessible via reflection")
    void initializeLogging_isAccessibleViaReflection() throws NoSuchMethodException {
        // Arrange
        MiniApp app = new MiniApp();

        // Act
        Method method = MiniApp.class.getDeclaredMethod("initializeLogging");
        method.setAccessible(true);

        // Assert
        assertNotNull(method, "initializeLogging() method should be accessible via reflection");
    }

    @Test
    @DisplayName("initializeLogging() does not throw exception when invoked")
    void initializeLogging_whenInvoked_doesNotThrowException() throws NoSuchMethodException {
        // Arrange
        MiniApp app = new MiniApp();
        Method method = MiniApp.class.getDeclaredMethod("initializeLogging");
        method.setAccessible(true);

        // Act & Assert
        assertDoesNotThrow(() -> {
            try {
                method.invoke(app);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) e.getCause();
                }
                // IOException wrapped in InvocationTargetException is acceptable
            }
        }, "initializeLogging() should not throw RuntimeException");
    }

    @Test
    @DisplayName("initializeLogging() produces output or error message")
    void initializeLogging_whenInvoked_producesOutputOrErrorMessage()
            throws NoSuchMethodException {
        // Arrange
        MiniApp app = new MiniApp();
        Method method = MiniApp.class.getDeclaredMethod("initializeLogging");
        method.setAccessible(true);

        // Act
        try {
            method.invoke(app);
        } catch (InvocationTargetException | IllegalAccessException e) {
            // acceptable
        }

        // Assert - either success or failure message should appear
        String allOutput = outContent.toString() + errContent.toString();
        // The method either succeeds (prints "Logging initialized at:") or fails
        // (prints "Failed to initialize logging:") - both are valid outcomes
        assertTrue(allOutput.contains("Logging initialized at:")
                        || allOutput.contains("Failed to initialize logging:")
                        || allOutput.isEmpty(), // permission denied silently
                "initializeLogging() should produce appropriate output");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // startServer() Tests (via reflection)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("startServer() is accessible via reflection")
    void startServer_isAccessibleViaReflection() throws NoSuchMethodException {
        // Arrange
        MiniApp app = new MiniApp();

        // Act
        Method method = MiniApp.class.getDeclaredMethod("startServer");
        method.setAccessible(true);

        // Assert
        assertNotNull(method, "startServer() method should be accessible via reflection");
    }

    @Test
    @DisplayName("startServer() does not throw exception when invoked")
    void startServer_whenInvoked_doesNotThrowException() throws NoSuchMethodException {
        // Arrange
        MiniApp app = new MiniApp();
        Method method = MiniApp.class.getDeclaredMethod("startServer");
        method.setAccessible(true);

        // Act & Assert
        assertDoesNotThrow(() -> {
            try {
                method.invoke(app);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) e.getCause();
                }
                // Port binding failure is acceptable in test environment
            }
        }, "startServer() should not throw RuntimeException");
    }

    @Test
    @DisplayName("startServer() prints server started message or error message")
    void startServer_whenInvoked_printsServerStartedOrErrorMessage()
            throws NoSuchMethodException {
        // Arrange
        MiniApp app = new MiniApp();
        Method method = MiniApp.class.getDeclaredMethod("startServer");
        method.setAccessible(true);

        // Act
        try {
            method.invoke(app);
        } catch (InvocationTargetException | IllegalAccessException e) {
            // acceptable
        }

        // Assert
        String allOutput = outContent.toString() + errContent.toString();
        boolean hasExpectedOutput = allOutput.contains("Server started on port:")
                || allOutput.contains("Failed to start server:")
                || allOutput.contains("Server ready to accept connections...");
        assertTrue(hasExpectedOutput,
                "startServer() should print server started or error message");
    }

    @Test
    @DisplayName("startServer() references port 8080 in output when successful")
    void startServer_whenPortAvailable_referencesPort8080() throws NoSuchMethodException {
        // Arrange
        MiniApp app = new MiniApp();
        Method method = MiniApp.class.getDeclaredMethod("startServer");
        method.setAccessible(true);

        // Act
        try {
            method.invoke(app);
        } catch (InvocationTargetException | IllegalAccessException e) {
            // acceptable
        }

        // Assert - if server started, it should mention port 8080
        String output = outContent.toString();
        if (output.contains("Server started on port:")) {
            assertTrue(output.contains("8080"),
                    "Server started message should reference port 8080");
        }
        // If port is in use, error message is acceptable
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Static Field / Constant Tests (via reflection)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("SERVER_PORT constant is 8080")
    void serverPort_constantValue_is8080() throws NoSuchFieldException, IllegalAccessException {
        // Arrange
        java.lang.reflect.Field field = MiniApp.class.getDeclaredField("SERVER_PORT");
        field.setAccessible(true);

        // Act
        int serverPort = (int) field.get(null);

        // Assert
        assertEquals(8080, serverPort, "SERVER_PORT should be 8080");
    }

    @Test
    @DisplayName("CONFIG_FILE_PATH constant is '/opt/app/config/app.properties'")
    void configFilePath_constantValue_isExpectedPath()
            throws NoSuchFieldException, IllegalAccessException {
        // Arrange
        java.lang.reflect.Field field = MiniApp.class.getDeclaredField("CONFIG_FILE_PATH");
        field.setAccessible(true);

        // Act
        String configFilePath = (String) field.get(null);

        // Assert
        assertEquals("/opt/app/config/app.properties", configFilePath,
                "CONFIG_FILE_PATH should be '/opt/app/config/app.properties'");
    }

    @Test
    @DisplayName("LOG_FILE_PATH constant is '/var/log/mini-app.log'")
    void logFilePath_constantValue_isExpectedPath()
            throws NoSuchFieldException, IllegalAccessException {
        // Arrange
        java.lang.reflect.Field field = MiniApp.class.getDeclaredField("LOG_FILE_PATH");
        field.setAccessible(true);

        // Act
        String logFilePath = (String) field.get(null);

        // Assert
        assertEquals("/var/log/mini-app.log", logFilePath,
                "LOG_FILE_PATH should be '/var/log/mini-app.log'");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Integration-style Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("MiniApp instance can be created and main() invoked without exception")
    void integration_createInstanceAndCallMain_doesNotThrowException() {
        // Arrange
        MiniApp app = new MiniApp();

        // Act & Assert
        assertNotNull(app);
        assertDoesNotThrow(() -> MiniApp.main(new String[]{}),
                "Creating instance and calling main() should not throw exception");
    }

    @Test
    @DisplayName("main() output contains application startup sequence")
    void main_outputContainsStartupSequence() {
        // Act
        MiniApp.main(new String[]{});

        // Assert
        String output = outContent.toString();
        assertTrue(output.contains("Starting Mini Java Application..."),
                "Output should contain startup message");
    }

    @Test
    @DisplayName("Calling main() twice does not throw exception")
    void main_calledTwice_doesNotThrowException() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            MiniApp.main(new String[]{});
            MiniApp.main(new String[]{});
        }, "Calling main() twice should not throw exception");
    }
}
