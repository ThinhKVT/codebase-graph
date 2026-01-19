package org.example.scip;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Executes scip-java CLI to generate SCIP index from Java source code.
 */
public class ScipRunner {

    private static final Logger logger = LoggerFactory.getLogger(ScipRunner.class);
    private static final int DEFAULT_TIMEOUT_MINUTES = 30;

    private final Path workingDirectory;
    private final int timeoutMinutes;

    public ScipRunner(Path workingDirectory) {
        this(workingDirectory, DEFAULT_TIMEOUT_MINUTES);
    }

    public ScipRunner(Path workingDirectory, int timeoutMinutes) {
        this.workingDirectory = workingDirectory;
        this.timeoutMinutes = timeoutMinutes;
    }

    /**
     * Check if scip-java is installed and available.
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
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                process.waitFor();
                return line != null ? line.trim() : "unknown";
            }
        } catch (IOException | InterruptedException e) {
            return "unknown";
        }
    }

    /**
     * Run scip-java index command on the working directory.
     */
    public Path runIndex() throws ScipException {
        return runIndex(null);
    }

    /**
     * Run scip-java index command with custom output path.
     */
    public Path runIndex(Path outputPath) throws ScipException {
        if (!isInstalled()) {
            throw ScipException.notInstalled();
        }

        Path scipFile = outputPath != null ? outputPath : workingDirectory.resolve("index.scip");

        List<String> command = new ArrayList<>();
        command.add("scip-java");
        command.add("index");
        command.add("--output");
        command.add(scipFile.toAbsolutePath().toString());

        logger.info("Running scip-java index in: {}", workingDirectory);
        logger.debug("Command: {}", String.join(" ", command));

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workingDirectory.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    logger.debug("[scip-java] {}", line);
                }
            }

            boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw ScipException.timeout(timeoutMinutes);
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw ScipException.indexingFailed(exitCode, output.toString());
            }

            if (!Files.exists(scipFile)) {
                throw ScipException.outputNotFound(scipFile);
            }

            logger.info("Successfully generated SCIP index: {}", scipFile);
            return scipFile;

        } catch (IOException e) {
            throw new ScipException("Failed to execute scip-java: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScipException("scip-java execution interrupted", e);
        }
    }

    /**
     * Check if the working directory is a valid Java project.
     */
    public boolean isValidJavaProject() {
        return Files.exists(workingDirectory.resolve("pom.xml")) ||
               Files.exists(workingDirectory.resolve("build.gradle")) ||
               Files.exists(workingDirectory.resolve("build.gradle.kts"));
    }

    /**
     * Get the detected build tool.
     */
    public String detectBuildTool() {
        if (Files.exists(workingDirectory.resolve("pom.xml"))) {
            return "maven";
        } else if (Files.exists(workingDirectory.resolve("build.gradle")) ||
                   Files.exists(workingDirectory.resolve("build.gradle.kts"))) {
            return "gradle";
        }
        return "unknown";
    }
}

