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
 * Python-specific SCIP runner that invokes the `scip-python` CLI.
 */
public class ScipPythonRunner implements ScipRunner {
    private static final Logger logger = LoggerFactory.getLogger(ScipPythonRunner.class);
    public static final int DEFAULT_TIMEOUT_MINUTES = 30;

    private final Path workingDirectory;
    private final int timeoutMinutes;

    public ScipPythonRunner(Path workingDirectory) {
        this(workingDirectory, DEFAULT_TIMEOUT_MINUTES);
    }

    public ScipPythonRunner(Path workingDirectory, int timeoutMinutes) {
        this.workingDirectory = workingDirectory;
        this.timeoutMinutes = timeoutMinutes;
    }

    public static boolean isInstalled() {
        try {
            ProcessBuilder pb = new ProcessBuilder("scip-python", "--version");
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
            ProcessBuilder pb = new ProcessBuilder("scip-python", "--version");
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

        Path scipFile = outputPath != null ? outputPath : workingDirectory.resolve("index.scip");

        List<String> command = new ArrayList<>();
        command.add("scip-python");
        command.add("index");
        command.add("--output");
        command.add(scipFile.toAbsolutePath().toString());

        logger.info("Running scip-python index in: {}", workingDirectory);
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
                    logger.debug("[scip-python] {}", line);
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
            throw new ScipException("Failed to execute scip-python: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScipException("scip-python execution interrupted", e);
        }
    }

    @Override
    public boolean isValidProject() {
        return Files.exists(workingDirectory.resolve("pyproject.toml")) ||
               Files.exists(workingDirectory.resolve("setup.py")) ||
               Files.exists(workingDirectory.resolve("requirements.txt"));
    }

    @Override
    public String detectBuildTool() {
        if (Files.exists(workingDirectory.resolve("pyproject.toml"))) return "poetry/pyproject";
        if (Files.exists(workingDirectory.resolve("setup.py"))) return "setuptools";
        if (Files.exists(workingDirectory.resolve("requirements.txt"))) return "requirements.txt";
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
}

