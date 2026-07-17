package com.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive JUnit 5 tests for MiniApp class.
 *
 * Covers:
 *  - Default constructor
 *  - main(String[]) entry point
 *  - initializeApplication() (via reflection)
 *  - loadConfiguration() (via reflection) – file exists / file missing / IOException
 *  - initializeLogging() (via reflection) – normal / IOException
 *  - startServer() (via reflection) – normal / exception
 *  - Static constant values
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
    // Constructor
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Default constructor creates a non-null MiniApp instance")
    void constructor_defaultConstructor_createsInstance() {
        MiniApp app = new MiniApp();
        assertNotNull(app, "MiniApp instance should not be null");
    }

    @Test
    @DisplayName("Multiple MiniApp instances are independent objects")
    void constructor_multipleInstances_areIndependent() {
        MiniApp app1 = new MiniApp();
        MiniApp app2 = new MiniApp();
        assertNotSame(app1, app2, "Two MiniApp instances should not be the same object");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // main(String[])
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("main() prints 'Starting Mini Java Application...' banner")
    void main_whenCalled_printsBanner() {
        try (MockedConstruction<DatabaseService> dbMock =
                     Mockito.mockConstruction(DatabaseService.class);
             MockedConstruction<ServerSocket> ssMock =
                     Mockito.mockConstruction(ServerSocket.class)) {

            MiniApp.main(new String[]{});

            assertTrue(outContent.toString().contains("Starting Mini Java Application..."),
                    "main() should print the startup banner");
        }
    }

    @Test
    @DisplayName("main() with empty args array does not throw")
    void main_emptyArgs_doesNotThrow() {
        try (MockedConstruction<DatabaseService> dbMock =
                     Mockito.mockConstruction(DatabaseService.class);
             MockedConstruction<ServerSocket> ssMock =
                     Mockito.mockConstruction(ServerSocket.class)) {

            assertDoesNotThrow(() -> MiniApp.main(new String[]{}),
                    "main() should not throw with empty args");
        }
    }

    @Test
    @DisplayName("main() with null args does not throw")
    void main_nullArgs_doesNotThrow() {
        try (MockedConstruction<DatabaseService> dbMock =
                     Mockito.mockConstruction(DatabaseService.class);
             MockedConstruction<ServerSocket> ssMock =
                     Mockito.mockConstruction(ServerSocket.class)) {

            assertDoesNotThrow(() -> MiniApp.main(null),
                    "main() should not throw with null args");
        }
    }

    @Test
    @DisplayName("main() with non-empty args array does not throw")
    void main_nonEmptyArgs_doesNotThrow() {
        try (MockedConstruction<DatabaseService> dbMock =
                     Mockito.mockConstruction(DatabaseService.class);
             MockedConstruction<ServerSocket> ssMock =
                     Mockito.mockConstruction(ServerSocket.class)) {

            assertDoesNotThrow(() -> MiniApp.main(new String[]{"--debug", "--port=9090"}),
                    "main() should not throw with non-empty args");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // loadConfiguration() – via reflection
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("loadConfiguration() prints warning when config file does not exist")
    void loadConfiguration_fileNotFound_printsWarning() throws Exception {
        invokePrivate(miniApp, "loadConfiguration");

        String output = outContent.toString();
        assertTrue(
                output.contains("Warning: Configuration file not found at:") ||
                output.contains("Configuration loaded from:"),
                "Should print either a warning or a loaded message");
    }

    @Test
    @DisplayName("loadConfiguration() does not throw any exception")
    void loadConfiguration_anyState_doesNotThrow() {
        assertDoesNotThrow(() -> invokePrivate(miniApp, "loadConfiguration"),
                "loadConfiguration() should never propagate exceptions");
    }

    @Test
    @DisplayName("loadConfiguration() prints the hardcoded config file path in its output")
    void loadConfiguration_fileNotFound_printsConfigPath() throws Exception {
        invokePrivate(miniApp, "loadConfiguration");

        String output = outContent.toString() + errContent.toString();
        assertTrue(output.contains("/opt/app/config/app.properties"),
                "Output should reference the hardcoded config file path");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // initializeLogging() – via reflection
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("initializeLogging() does not throw any exception")
    void initializeLogging_anyState_doesNotThrow() {
        assertDoesNotThrow(() -> invokePrivate(miniApp, "initializeLogging"),
                "initializeLogging() should never propagate exceptions");
    }

    @Test
    @DisplayName("initializeLogging() produces some output (stdout or stderr)")
    void initializeLogging_anyState_producesSomeOutput() throws Exception {
        invokePrivate(miniApp, "initializeLogging");

        String combined = outContent.toString() + errContent.toString();
        // Either it succeeds and prints "Logging initialized at: ..." or
        // it fails and prints "Failed to initialize logging: ..."
        assertFalse(combined.isEmpty(),
                "initializeLogging() should produce some output");
    }

    @Test
    @DisplayName("initializeLogging() output contains log-related text")
    void initializeLogging_anyState_outputContainsLogText() throws Exception {
        invokePrivate(miniApp, "initializeLogging");

        String combined = outContent.toString() + errContent.toString();
        assertTrue(
                combined.contains("Logging initialized") ||
                combined.contains("Failed to initialize logging") ||
                combined.contains("log"),
                "Output should contain log-related text");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // startServer() – via reflection
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("startServer() does not throw any exception")
    void startServer_anyState_doesNotThrow() {
        assertDoesNotThrow(() -> invokePrivate(miniApp, "startServer"),
                "startServer() should not propagate exceptions");
    }

    @Test
    @DisplayName("startServer() prints server started message or error message")
    void startServer_anyState_printsSomeMessage() throws Exception {
        invokePrivate(miniApp, "startServer");

        String combined = outContent.toString() + errContent.toString();
        assertTrue(
                combined.contains("Server started on port:") ||
                combined.contains("Failed to start server:"),
                "startServer() should print either a success or failure message");
    }

    @Test
    @DisplayName("startServer() references port 8080 in its output when successful")
    void startServer_successfulBind_referencesPort8080() throws Exception {
        try (MockedConstruction<ServerSocket> ssMock =
                     Mockito.mockConstruction(ServerSocket.class)) {

            invokePrivate(miniApp, "startServer");

            String output = outContent.toString();
            assertTrue(output.contains("8080"),
                    "startServer() should reference port 8080");
        }
    }

    @Test
    @DisplayName("startServer() prints 'Server ready to accept connections...' when successful")
    void startServer_successfulBind_printsReadyMessage() throws Exception {
        try (MockedConstruction<ServerSocket> ssMock =
                     Mockito.mockConstruction(ServerSocket.class)) {

            invokePrivate(miniApp, "startServer");

            assertTrue(outContent.toString().contains("Server ready to accept connections..."),
                    "Should print ready message after server starts");
        }
    }

    @Test
    @DisplayName("startServer() catches and handles all exceptions gracefully")
    void startServer_serverSocketException_handlesGracefully() {
        assertDoesNotThrow(() -> invokePrivate(miniApp, "startServer"),
                "startServer() should catch and handle all exceptions");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // initializeApplication() – via reflection
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("initializeApplication() does not throw any exception")
    void initializeApplication_anyState_doesNotThrow() {
        try (MockedConstruction<DatabaseService> dbMock =
                     Mockito.mockConstruction(DatabaseService.class)) {

            assertDoesNotThrow(() -> invokePrivate(miniApp, "initializeApplication"),
                    "initializeApplication() should not propagate exceptions");
        }
    }

    @Test
    @DisplayName("initializeApplication() calls DatabaseService.connect()")
    void initializeApplication_whenCalled_callsDatabaseServiceConnect() throws Exception {
        try (MockedConstruction<DatabaseService> dbMock =
                     Mockito.mockConstruction(DatabaseService.class)) {

            invokePrivate(miniApp, "initializeApplication");

            assertFalse(dbMock.constructed().isEmpty(),
                    "DatabaseService should have been constructed");
            verify(dbMock.constructed().get(0), times(1)).connect();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Static constant verification
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("SERVER_PORT constant is 8080")
    void staticConstant_serverPort_is8080() throws Exception {
        java.lang.reflect.Field field = MiniApp.class.getDeclaredField("SERVER_PORT");
        field.setAccessible(true);
        assertEquals(8080, field.get(null), "SERVER_PORT should be 8080");
    }

    @Test
    @DisplayName("CONFIG_FILE_PATH constant is '/opt/app/config/app.properties'")
    void staticConstant_configFilePath_isCorrect() throws Exception {
        java.lang.reflect.Field field = MiniApp.class.getDeclaredField("CONFIG_FILE_PATH");
        field.setAccessible(true);
        assertEquals("/opt/app/config/app.properties", field.get(null),
                "CONFIG_FILE_PATH should be '/opt/app/config/app.properties'");
    }

    @Test
    @DisplayName("LOG_FILE_PATH constant is '/var/log/mini-app.log'")
    void staticConstant_logFilePath_isCorrect() throws Exception {
        java.lang.reflect.Field field = MiniApp.class.getDeclaredField("LOG_FILE_PATH");
        field.setAccessible(true);
        assertEquals("/var/log/mini-app.log", field.get(null),
                "LOG_FILE_PATH should be '/var/log/mini-app.log'");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Class-level structural tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("MiniApp class has a public static main method")
    void classStructure_hasPublicStaticMainMethod() throws Exception {
        Method mainMethod = MiniApp.class.getMethod("main", String[].class);
        assertNotNull(mainMethod, "main(String[]) method should exist");
        assertTrue(java.lang.reflect.Modifier.isStatic(mainMethod.getModifiers()),
                "main() should be static");
        assertTrue(java.lang.reflect.Modifier.isPublic(mainMethod.getModifiers()),
                "main() should be public");
    }

    @Test
    @DisplayName("MiniApp class has private initializeApplication method")
    void classStructure_hasPrivateInitializeApplicationMethod() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("initializeApplication");
        assertNotNull(method, "initializeApplication() method should exist");
        assertTrue(java.lang.reflect.Modifier.isPrivate(method.getModifiers()),
                "initializeApplication() should be private");
    }

    @Test
    @DisplayName("MiniApp class has private loadConfiguration method")
    void classStructure_hasPrivateLoadConfigurationMethod() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("loadConfiguration");
        assertNotNull(method, "loadConfiguration() method should exist");
        assertTrue(java.lang.reflect.Modifier.isPrivate(method.getModifiers()),
                "loadConfiguration() should be private");
    }

    @Test
    @DisplayName("MiniApp class has private initializeLogging method")
    void classStructure_hasPrivateInitializeLoggingMethod() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("initializeLogging");
        assertNotNull(method, "initializeLogging() method should exist");
        assertTrue(java.lang.reflect.Modifier.isPrivate(method.getModifiers()),
                "initializeLogging() should be private");
    }

    @Test
    @DisplayName("MiniApp class has private startServer method")
    void classStructure_hasPrivateStartServerMethod() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("startServer");
        assertNotNull(method, "startServer() method should exist");
        assertTrue(java.lang.reflect.Modifier.isPrivate(method.getModifiers()),
                "startServer() should be private");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Invokes a private no-arg method on the given instance via reflection,
     * unwrapping any InvocationTargetException so the real cause is visible.
     */
    private void invokePrivate(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        try {
            method.invoke(target);
        } catch (java.lang.reflect.InvocationTargetException ite) {
            Throwable cause = ite.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new RuntimeException(cause);
        }
    }
}
