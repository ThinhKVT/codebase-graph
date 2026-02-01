package org.example.scip;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Java-specific SCIP runner that invokes the `scip-java` CLI.
 */
public class ScipJavaRunner implements ScipRunner {
    private static final Logger logger = LoggerFactory.getLogger(ScipJavaRunner.class);
    public static final int DEFAULT_TIMEOUT_MINUTES = 30;

    private final Path workingDirectory;
    private final int timeoutMinutes;

    public ScipJavaRunner(Path workingDirectory) {
        this(workingDirectory, DEFAULT_TIMEOUT_MINUTES);
    }

    public ScipJavaRunner(Path workingDirectory, int timeoutMinutes) {
        this.workingDirectory = workingDirectory;
        this.timeoutMinutes = timeoutMinutes;
    }

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

    @Override
    public Path runIndex() throws ScipException {
        return runIndex(null);
    }

    @Override
    public Path runIndex(Path outputPath) throws ScipException {
        if (!isInstalled()) {
            throw ScipException.notInstalled();
        }

        // Fix mvnw line endings if needed (crucial for Docker/Linux execution of Windows-checked-out files)
        fixMvnwLineEndings();

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

    @Override
    public boolean isValidProject() {
        return Files.exists(workingDirectory.resolve("pom.xml")) ||
               Files.exists(workingDirectory.resolve("build.gradle")) ||
               Files.exists(workingDirectory.resolve("build.gradle.kts"));
    }

    @Override
    public String detectBuildTool() {
        if (Files.exists(workingDirectory.resolve("pom.xml"))) {
            return "maven";
        } else if (Files.exists(workingDirectory.resolve("build.gradle")) ||
                   Files.exists(workingDirectory.resolve("build.gradle.kts"))) {
            return "gradle";
        }
        return "unknown";
    }

    @Override
    public boolean isToolInstalled() {
        return isInstalled();
    }

    @Override
    public String getToolVersion() {
        return getVersion();
    }

    private void fixMvnwLineEndings() {
        Path mvnwPath = workingDirectory.resolve("mvnw");
        if (Files.exists(mvnwPath)) {
            try {
                // Read as ISO-8859-1 to avoid UTF-8 decoding errors if binary data is somehow present,
                // though mvnw should be text. UTF-8 is safer for shell scripts.
                String content = Files.readString(mvnwPath);
                if (content.contains("\r\n")) {
                    logger.info("Detected CRLF line endings in mvnw. Converting to LF to ensure Linux compatibility...");
                    content = content.replace("\r\n", "\n");
                    Files.writeString(mvnwPath, content);
                    logger.info("Successfully converted mvnw line endings to LF.");
                }
            } catch (IOException e) {
                logger.warn("Failed to check/fix mvnw line endings: {}", e.getMessage());
            }
        }
    }
}
