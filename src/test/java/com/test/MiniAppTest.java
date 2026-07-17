package com.test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive JUnit 5 tests for MiniApp.
 *
 * Strategy:
 *  - MiniApp has private methods that interact with the filesystem and network.
 *    We test the public API (main, and the class structure) and exercise the
 *    private methods via reflection, verifying they do not throw unchecked
 *    exceptions (all IOExceptions are caught internally).
 *  - We verify the hardcoded constant values that are part of the class contract.
 *  - We verify the class structure (modifiers, method signatures).
 */
@DisplayName("MiniApp Tests")
class MiniAppTest {

    private MiniApp miniApp;

    // -----------------------------------------------------------------------
    // Constants declared in MiniApp (verified via reflection)
    // -----------------------------------------------------------------------
    private static final int    EXPECTED_SERVER_PORT    = 8080;
    private static final String EXPECTED_CONFIG_PATH    = "/opt/app/config/app.properties";
    private static final String EXPECTED_LOG_FILE_PATH  = "/var/log/mini-app.log";

    @BeforeEach
    void setUp() {
        miniApp = new MiniApp();
    }

    // -----------------------------------------------------------------------
    // Constructor tests
    // -----------------------------------------------------------------------

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
        assertNotSame(app1, app2, "Each MiniApp() call should produce a distinct instance");
    }

    // -----------------------------------------------------------------------
    // Static constant tests (via reflection)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("SERVER_PORT constant equals 8080")
    void constants_serverPort_is8080() throws Exception {
        Field field = MiniApp.class.getDeclaredField("SERVER_PORT");
        field.setAccessible(true);
        assertEquals(EXPECTED_SERVER_PORT, field.get(null),
                "SERVER_PORT should be 8080");
    }

    @Test
    @DisplayName("CONFIG_FILE_PATH constant equals '/opt/app/config/app.properties'")
    void constants_configFilePath_isCorrect() throws Exception {
        Field field = MiniApp.class.getDeclaredField("CONFIG_FILE_PATH");
        field.setAccessible(true);
        assertEquals(EXPECTED_CONFIG_PATH, field.get(null),
                "CONFIG_FILE_PATH should be /opt/app/config/app.properties");
    }

    @Test
    @DisplayName("LOG_FILE_PATH constant equals '/var/log/mini-app.log'")
    void constants_logFilePath_isCorrect() throws Exception {
        Field field = MiniApp.class.getDeclaredField("LOG_FILE_PATH");
        field.setAccessible(true);
        assertEquals(EXPECTED_LOG_FILE_PATH, field.get(null),
                "LOG_FILE_PATH should be /var/log/mini-app.log");
    }

    @Test
    @DisplayName("SERVER_PORT constant is a positive integer")
    void constants_serverPort_isPositive() throws Exception {
        Field field = MiniApp.class.getDeclaredField("SERVER_PORT");
        field.setAccessible(true);
        int port = (int) field.get(null);
        assertTrue(port > 0, "SERVER_PORT must be a positive integer");
    }

    @Test
    @DisplayName("CONFIG_FILE_PATH constant is not null or blank")
    void constants_configFilePath_isNotNullOrBlank() throws Exception {
        Field field = MiniApp.class.getDeclaredField("CONFIG_FILE_PATH");
        field.setAccessible(true);
        String value = (String) field.get(null);
        assertNotNull(value);
        assertFalse(value.isBlank(), "CONFIG_FILE_PATH must not be blank");
    }

    @Test
    @DisplayName("LOG_FILE_PATH constant is not null or blank")
    void constants_logFilePath_isNotNullOrBlank() throws Exception {
        Field field = MiniApp.class.getDeclaredField("LOG_FILE_PATH");
        field.setAccessible(true);
        String value = (String) field.get(null);
        assertNotNull(value);
        assertFalse(value.isBlank(), "LOG_FILE_PATH must not be blank");
    }

    // -----------------------------------------------------------------------
    // main() tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("main() with empty args array does not throw unchecked exception")
    void main_withEmptyArgs_doesNotThrow() {
        // main() catches all exceptions internally (IOException, InterruptedException, etc.)
        assertDoesNotThrow(() -> MiniApp.main(new String[]{}),
                "main() must not propagate any unchecked exception");
    }

    @Test
    @DisplayName("main() with null args does not throw unchecked exception")
    void main_withNullArgs_doesNotThrow() {
        assertDoesNotThrow(() -> MiniApp.main(null),
                "main() must not propagate any unchecked exception when args is null");
    }

    @Test
    @DisplayName("main() with non-empty args does not throw unchecked exception")
    void main_withNonEmptyArgs_doesNotThrow() {
        assertDoesNotThrow(() -> MiniApp.main(new String[]{"--debug", "--port=9090"}),
                "main() must not propagate any unchecked exception with arbitrary args");
    }

    // -----------------------------------------------------------------------
    // Private method tests (via reflection)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("initializeApplication() private method exists and does not throw")
    void privateMethod_initializeApplication_existsAndDoesNotThrow() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("initializeApplication");
        method.setAccessible(true);
        assertDoesNotThrow(() -> method.invoke(miniApp),
                "initializeApplication() must not throw an unchecked exception");
    }

    @Test
    @DisplayName("loadConfiguration() private method exists and does not throw")
    void privateMethod_loadConfiguration_existsAndDoesNotThrow() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("loadConfiguration");
        method.setAccessible(true);
        assertDoesNotThrow(() -> method.invoke(miniApp),
                "loadConfiguration() must not throw an unchecked exception");
    }

    @Test
    @DisplayName("initializeLogging() private method exists and does not throw")
    void privateMethod_initializeLogging_existsAndDoesNotThrow() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("initializeLogging");
        method.setAccessible(true);
        assertDoesNotThrow(() -> method.invoke(miniApp),
                "initializeLogging() must not throw an unchecked exception");
    }

    @Test
    @DisplayName("startServer() private method exists and does not throw")
    void privateMethod_startServer_existsAndDoesNotThrow() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("startServer");
        method.setAccessible(true);
        assertDoesNotThrow(() -> method.invoke(miniApp),
                "startServer() must not throw an unchecked exception");
    }

    // -----------------------------------------------------------------------
    // Class structure / reflection tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("MiniApp class is public and concrete")
    void classStructure_isPublicAndConcrete() {
        int modifiers = MiniApp.class.getModifiers();
        assertTrue(Modifier.isPublic(modifiers),
                "MiniApp should be public");
        assertFalse(Modifier.isAbstract(modifiers),
                "MiniApp should not be abstract");
        assertFalse(Modifier.isInterface(modifiers),
                "MiniApp should not be an interface");
    }

    @Test
    @DisplayName("main() method is public and static")
    void classStructure_mainMethod_isPublicAndStatic() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("main", String[].class);
        assertTrue(Modifier.isPublic(method.getModifiers()),
                "main() should be public");
        assertTrue(Modifier.isStatic(method.getModifiers()),
                "main() should be static");
    }

    @Test
    @DisplayName("main() method accepts String[] parameter")
    void classStructure_mainMethod_acceptsStringArrayParam() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("main", String[].class);
        assertEquals(1, method.getParameterCount(),
                "main() should have exactly one parameter");
        assertEquals(String[].class, method.getParameterTypes()[0],
                "main() parameter should be String[]");
    }

    @Test
    @DisplayName("initializeApplication() method is private")
    void classStructure_initializeApplicationMethod_isPrivate() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("initializeApplication");
        assertTrue(Modifier.isPrivate(method.getModifiers()),
                "initializeApplication() should be private");
    }

    @Test
    @DisplayName("loadConfiguration() method is private")
    void classStructure_loadConfigurationMethod_isPrivate() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("loadConfiguration");
        assertTrue(Modifier.isPrivate(method.getModifiers()),
                "loadConfiguration() should be private");
    }

    @Test
    @DisplayName("initializeLogging() method is private")
    void classStructure_initializeLoggingMethod_isPrivate() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("initializeLogging");
        assertTrue(Modifier.isPrivate(method.getModifiers()),
                "initializeLogging() should be private");
    }

    @Test
    @DisplayName("startServer() method is private")
    void classStructure_startServerMethod_isPrivate() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("startServer");
        assertTrue(Modifier.isPrivate(method.getModifiers()),
                "startServer() should be private");
    }

    // -----------------------------------------------------------------------
    // Integration / lifecycle tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("loadConfiguration() followed by initializeLogging() does not throw")
    void lifecycle_loadConfigThenInitLogging_doesNotThrow() throws Exception {
        Method loadConfig = MiniApp.class.getDeclaredMethod("loadConfiguration");
        loadConfig.setAccessible(true);
        Method initLogging = MiniApp.class.getDeclaredMethod("initializeLogging");
        initLogging.setAccessible(true);

        assertDoesNotThrow(() -> {
            loadConfig.invoke(miniApp);
            initLogging.invoke(miniApp);
        }, "loadConfiguration() then initializeLogging() must not throw");
    }

    @Test
    @DisplayName("initializeApplication() can be called multiple times without throwing")
    void lifecycle_initializeApplicationCalledMultipleTimes_doesNotThrow() throws Exception {
        Method method = MiniApp.class.getDeclaredMethod("initializeApplication");
        method.setAccessible(true);

        assertDoesNotThrow(() -> {
            method.invoke(miniApp);
            method.invoke(miniApp);
        }, "initializeApplication() called twice must not throw");
    }

    @Test
    @DisplayName("SERVER_PORT is within valid TCP port range (1-65535)")
    void constants_serverPort_isInValidTcpRange() throws Exception {
        Field field = MiniApp.class.getDeclaredField("SERVER_PORT");
        field.setAccessible(true);
        int port = (int) field.get(null);
        assertTrue(port >= 1 && port <= 65535,
                "SERVER_PORT must be within valid TCP port range 1-65535, but was: " + port);
    }

    @Test
    @DisplayName("CONFIG_FILE_PATH starts with '/' indicating an absolute path")
    void constants_configFilePath_isAbsolutePath() throws Exception {
        Field field = MiniApp.class.getDeclaredField("CONFIG_FILE_PATH");
        field.setAccessible(true);
        String path = (String) field.get(null);
        assertTrue(path.startsWith("/"),
                "CONFIG_FILE_PATH should be an absolute path starting with '/'");
    }

    @Test
    @DisplayName("LOG_FILE_PATH starts with '/' indicating an absolute path")
    void constants_logFilePath_isAbsolutePath() throws Exception {
        Field field = MiniApp.class.getDeclaredField("LOG_FILE_PATH");
        field.setAccessible(true);
        String path = (String) field.get(null);
        assertTrue(path.startsWith("/"),
                "LOG_FILE_PATH should be an absolute path starting with '/'");
    }
}
