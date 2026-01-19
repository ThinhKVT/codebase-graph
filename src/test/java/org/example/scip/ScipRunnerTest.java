package org.example.scip;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import scip.Scip;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ScipRunner.
 */
class ScipRunnerTest {

    @Test
    void testIsInstalled() {
        // This test checks if scip-java detection works
        boolean installed = ScipRunner.isInstalled();
        System.out.println("scip-java installed: " + installed);
        // No assertion - just verify the method doesn't throw
    }

    @Test
    void testGetVersion() {
        String version = ScipRunner.getVersion();
        assertNotNull(version);
        System.out.println("scip-java version: " + version);
    }

    @Test
    void testIsValidJavaProject_empty(@TempDir Path tempDir) {
        ScipRunner runner = new ScipRunner(tempDir);
        assertFalse(runner.isValidJavaProject());
        assertEquals("unknown", runner.detectBuildTool());
    }

    @Test
    void testIsValidJavaProject_maven(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");

        ScipRunner runner = new ScipRunner(tempDir);
        assertTrue(runner.isValidJavaProject());
        assertEquals("maven", runner.detectBuildTool());
    }

    @Test
    void testIsValidJavaProject_gradle(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("build.gradle"), "plugins { id 'java' }");

        ScipRunner runner = new ScipRunner(tempDir);
        assertTrue(runner.isValidJavaProject());
        assertEquals("gradle", runner.detectBuildTool());
    }

    @Test
    void testRunIndexWithoutScipJava(@TempDir Path tempDir) {
        // Skip if scip-java is installed
        if (ScipRunner.isInstalled()) {
            System.out.println("Skipping - scip-java is installed");
            return;
        }

        ScipRunner runner = new ScipRunner(tempDir);
        ScipException exception = assertThrows(ScipException.class, () -> runner.runIndex());
        assertTrue(exception.getMessage().contains("not installed"));
    }
}

