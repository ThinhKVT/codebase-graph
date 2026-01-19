package org.example.scip;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import scip.Scip;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Scip Java runner.
 */
class ScipRunnerTest {

    @Test
    void testIsInstalled() {
        // This test checks if scip-java detection works
        boolean installed = ScipJavaRunner.isInstalled();
        System.out.println("scip-java installed: " + installed);
        // No assertion - just verify the method doesn't throw
    }

    @Test
    void testGetVersion() {
        String version = ScipJavaRunner.getVersion();
        assertNotNull(version);
        System.out.println("scip-java version: " + version);
    }

    @Test
    void testIsValidJavaProject_empty(@TempDir Path tempDir) {
        ScipJavaRunner runner = new ScipJavaRunner(tempDir);
        assertFalse(runner.isValidProject());
        assertEquals("unknown", runner.detectBuildTool());
    }

    @Test
    void testIsValidJavaProject_maven(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");

        ScipJavaRunner runner = new ScipJavaRunner(tempDir);
        assertTrue(runner.isValidProject());
        assertEquals("maven", runner.detectBuildTool());
    }

    @Test
    void testIsValidJavaProject_gradle(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("build.gradle"), "plugins { id 'java' }");

        ScipJavaRunner runner = new ScipJavaRunner(tempDir);
        assertTrue(runner.isValidProject());
        assertEquals("gradle", runner.detectBuildTool());
    }

    @Test
    void testRunIndexWithoutScipJava(@TempDir Path tempDir) {
        // Skip if scip-java is installed
        if (ScipJavaRunner.isInstalled()) {
            System.out.println("Skipping - scip-java is installed");
            return;
        }

        ScipJavaRunner runner = new ScipJavaRunner(tempDir);
        ScipException exception = assertThrows(ScipException.class, () -> runner.runIndex());
        assertTrue(exception.getMessage().toLowerCase().contains("not installed") || exception.getMessage().toLowerCase().contains("not implemented"));
    }
}
