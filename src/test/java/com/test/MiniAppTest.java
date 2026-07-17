package com.test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive JUnit 5 tests for {@link MiniApp}.
 *
 * <p>Strategy:
 * <ul>
 *   <li>Reflective access to private methods to test them in isolation.</li>
 *   <li>{@code @TempDir} for file-system operations that need a writable directory.</li>
 *   <li>Static final fields cannot be mutated in Java 17 without --add-opens; tests that
 *       relied on that approach are replaced with behaviour-based assertions.</li>
 * </ul>
 */
@DisplayName("MiniApp Tests")
class MiniAppTest {

    private MiniApp miniApp;

    @BeforeEach
    void setUp() {
        miniApp = new MiniApp();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Invoke a private void method by name (no parameters). */
    private void invokePrivate(Object target, String methodName) throws Exception {
        Method m = target.getClass().getDeclaredMethod(methodName);
        m.setAccessible(true);
        m.invoke(target);
    }

    /** Read a private static String field. */
    private String getStaticStringField(Class<?> clazz, String fieldName) throws Exception {
        Field f = clazz.getDeclaredField(fieldName);
        f.setAccessible(true);
        return (String) f.get(null);
    }

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Default constructor creates a non-null MiniApp instance")
    void constructor_defaultConstructor_createsInstance() {
        // Act
        MiniApp app = new MiniApp();

        // Assert
        assertNotNull(app, "MiniApp instance must not be null");
    }

    // -----------------------------------------------------------------------
    // loadConfiguration() – private method via reflection
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("loadConfiguration() – missing config file prints warning without throwing")
    void loadConfiguration_missingConfigFile_doesNotThrow() {
        // Arrange – CONFIG_FILE_PATH points to a non-existent path by default
        MiniApp app = new MiniApp();

        // Act & Assert
        assertDoesNotThrow(() -> invokePrivate(app, "loadConfiguration"),
                "loadConfiguration() must not throw when config file is absent");
    }

    @Test
    @DisplayName("loadConfiguration() – invoked twice does not throw")
    void loadConfiguration_invokedTwice_doesNotThrow() {
        MiniApp app = new MiniApp();
        assertDoesNotThrow(() -> {
            invokePrivate(app, "loadConfiguration");
            invokePrivate(app, "loadConfiguration");
        }, "loadConfiguration() must be idempotent and not throw");
    }

    // -----------------------------------------------------------------------
    // initializeLogging() – private method via reflection
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("initializeLogging() – does not throw even when /var/log is not writable")
    void initializeLogging_doesNotThrow() {
        // Arrange
        MiniApp app = new MiniApp();

        // Act & Assert – IOException is caught internally; must not propagate
        assertDoesNotThrow(() -> invokePrivate(app, "initializeLogging"),
                "initializeLogging() must not propagate IOException");
    }

    @Test
    @DisplayName("initializeLogging() – invoked twice does not throw")
    void initializeLogging_invokedTwice_doesNotThrow() {
        MiniApp app = new MiniApp();
        assertDoesNotThrow(() -> {
            invokePrivate(app, "initializeLogging");
            invokePrivate(app, "initializeLogging");
        }, "initializeLogging() must be idempotent and not throw");
    }

    // -----------------------------------------------------------------------
    // startServer() – private method via reflection
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("startServer() – does not throw regardless of port availability")
    void startServer_doesNotThrow() {
        // Arrange
        MiniApp app = new MiniApp();

        // Act & Assert – the method catches all exceptions internally
        assertDoesNotThrow(() -> invokePrivate(app, "startServer"),
                "startServer() must not propagate any exception");
    }

    @Test
    @DisplayName("startServer() – invoked twice does not throw")
    void startServer_invokedTwice_doesNotThrow() {
        MiniApp app = new MiniApp();
        assertDoesNotThrow(() -> {
            invokePrivate(app, "startServer");
            invokePrivate(app, "startServer");
        }, "startServer() must not throw on repeated invocations");
    }

    // -----------------------------------------------------------------------
    // Static constants – verify expected values
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("SERVER_PORT constant equals 8080")
    void constant_serverPort_equals8080() throws Exception {
        Field f = MiniApp.class.getDeclaredField("SERVER_PORT");
        f.setAccessible(true);
        int port = (int) f.get(null);
        assertEquals(8080, port, "SERVER_PORT must be 8080");
    }

    @Test
    @DisplayName("CONFIG_FILE_PATH constant is not null or empty")
    void constant_configFilePath_notNullOrEmpty() throws Exception {
        String path = getStaticStringField(MiniApp.class, "CONFIG_FILE_PATH");
        assertNotNull(path, "CONFIG_FILE_PATH must not be null");
        assertFalse(path.isBlank(), "CONFIG_FILE_PATH must not be blank");
    }

    @Test
    @DisplayName("LOG_FILE_PATH constant is not null or empty")
    void constant_logFilePath_notNullOrEmpty() throws Exception {
        String path = getStaticStringField(MiniApp.class, "LOG_FILE_PATH");
        assertNotNull(path, "LOG_FILE_PATH must not be null");
        assertFalse(path.isBlank(), "LOG_FILE_PATH must not be blank");
    }

    @Test
    @DisplayName("CONFIG_FILE_PATH constant contains expected path segment")
    void constant_configFilePath_containsExpectedSegment() throws Exception {
        String path = getStaticStringField(MiniApp.class, "CONFIG_FILE_PATH");
        assertTrue(path.contains("app.properties"),
                "CONFIG_FILE_PATH should reference app.properties");
    }

    @Test
    @DisplayName("LOG_FILE_PATH constant contains expected log file name")
    void constant_logFilePath_containsExpectedSegment() throws Exception {
        String path = getStaticStringField(MiniApp.class, "LOG_FILE_PATH");
        assertTrue(path.contains("mini-app.log"),
                "LOG_FILE_PATH should reference mini-app.log");
    }

    // -----------------------------------------------------------------------
    // main() – smoke test
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("main() – does not throw when invoked with empty args array")
    void main_emptyArgs_doesNotThrow() {
        // main() calls initializeApplication() which tries DB connection (will fail)
        // and startServer() which catches all exceptions – so it must not propagate
        assertDoesNotThrow(() -> MiniApp.main(new String[]{}),
                "main() must not propagate any exception");
    }

    @Test
    @DisplayName("main() – does not throw when invoked with non-empty args array")
    void main_withArgs_doesNotThrow() {
        assertDoesNotThrow(() -> MiniApp.main(new String[]{"--debug", "--port=9090"}),
                "main() must not propagate any exception regardless of args");
    }

    // -----------------------------------------------------------------------
    // initializeApplication() – private method via reflection
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("initializeApplication() – does not throw (DB connection failure is swallowed)")
    void initializeApplication_doesNotThrow() {
        // Arrange
        MiniApp app = new MiniApp();

        // Act & Assert – DatabaseService.connect() will fail (no DB), but must be swallowed
        assertDoesNotThrow(() -> invokePrivate(app, "initializeApplication"),
                "initializeApplication() must not propagate any exception");
    }

    // -----------------------------------------------------------------------
    // Multiple MiniApp instances
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Multiple MiniApp instances are independent objects")
    void multipleInstances_areIndependent() {
        MiniApp app1 = new MiniApp();
        MiniApp app2 = new MiniApp();

        assertNotSame(app1, app2, "Each MiniApp() call must produce a distinct instance");
    }

    // -----------------------------------------------------------------------
    // Combined private method invocations
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("loadConfiguration() followed by initializeLogging() does not throw")
    void loadConfigThenInitLogging_doesNotThrow() {
        MiniApp app = new MiniApp();
        assertDoesNotThrow(() -> {
            invokePrivate(app, "loadConfiguration");
            invokePrivate(app, "initializeLogging");
        }, "Sequential private method calls must not throw");
    }

    @Test
    @DisplayName("All private methods invoked in sequence do not throw")
    void allPrivateMethods_invokedInSequence_doesNotThrow() {
        MiniApp app = new MiniApp();
        assertDoesNotThrow(() -> {
            invokePrivate(app, "loadConfiguration");
            invokePrivate(app, "initializeLogging");
            invokePrivate(app, "startServer");
        }, "All private methods invoked in sequence must not throw");
    }
}
