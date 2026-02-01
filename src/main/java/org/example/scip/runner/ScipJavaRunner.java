package org.example.scip.runner;

import org.example.scip.ScipException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Java-specific SCIP runner using scip-java CLI.
 * 
 * <p>Supports Maven and Gradle projects. Automatically fixes CRLF line
 * endings in mvnw scripts for cross-platform compatibility.</p>
 */
public class ScipJavaRunner extends AbstractScipRunner {

    public ScipJavaRunner(Path workingDirectory) {
        super(workingDirectory);
    }

    public ScipJavaRunner(Path workingDirectory, int timeoutMinutes) {
        super(workingDirectory, timeoutMinutes);
    }

    @Override
    protected String getToolCommand() {
        return "scip-java";
    }

    @Override
    protected List<String> getIndexArgs(Path outputPath) {
        return List.of(
            "index",
            "--output",
            outputPath.toAbsolutePath().toString()
        );
    }

    @Override
    public boolean isValidProject() {
        return anyFileExists("pom.xml", "build.gradle", "build.gradle.kts");
    }

    @Override
    public String detectBuildTool() {
        if (fileExists("pom.xml")) {
            return "maven";
        } else if (fileExists("build.gradle") || fileExists("build.gradle.kts")) {
            return "gradle";
        }
        return "unknown";
    }

    /**
     * Pre-process: Fix CRLF line endings in mvnw script.
     * 
     * <p>This is crucial for Docker/Linux execution when files are
     * checked out on Windows with CRLF line endings.</p>
     */
    @Override
    protected void preProcess() throws ScipException {
        fixMvnwLineEndings();
    }

    private void fixMvnwLineEndings() {
        Path mvnwPath = workingDirectory.resolve("mvnw");
        if (Files.exists(mvnwPath)) {
            try {
                String content = Files.readString(mvnwPath);
                if (content.contains("\r\n")) {
                    logger.info("Detected CRLF line endings in mvnw. Converting to LF...");
                    content = content.replace("\r\n", "\n");
                    Files.writeString(mvnwPath, content);
                    logger.info("Successfully converted mvnw line endings to LF.");
                }
            } catch (IOException e) {
                logger.warn("Failed to check/fix mvnw line endings: {}", e.getMessage());
            }
        }
    }

    // Static convenience methods for backward compatibility

    /**
     * Check if scip-java is installed.
     */
    public static boolean isInstalled() {
        try {
            ProcessBuilder pb = new ProcessBuilder("scip-java", "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    /**
     * Get scip-java version.
     */
    public static String getVersion() {
        try {
            ProcessBuilder pb = new ProcessBuilder("scip-java", "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                process.waitFor();
                return line != null ? line.trim() : "unknown";
            }
        } catch (IOException | InterruptedException e) {
            return "unknown";
        }
    }
}
