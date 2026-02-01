package org.example.scip.runner;

import org.example.scip.ScipException;
import org.example.scip.ScipRunner;
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
 * Abstract base class for SCIP runners using Template Method pattern.
 * 
 * <p>Subclasses must implement:</p>
 * <ul>
 *   <li>{@link #getToolCommand()} - The CLI command name (e.g., "scip-java")</li>
 *   <li>{@link #getIndexArgs(Path)} - Command arguments for indexing</li>
 *   <li>{@link #isValidProject()} - Check if directory is valid for this language</li>
 *   <li>{@link #detectBuildTool()} - Detect the build tool used</li>
 * </ul>
 * 
 * <p>Subclasses can optionally override:</p>
 * <ul>
 *   <li>{@link #preProcess()} - Hook called before indexing (e.g., fix line endings)</li>
 *   <li>{@link #postProcess(Path)} - Hook called after successful indexing</li>
 * </ul>
 */
public abstract class AbstractScipRunner implements ScipRunner {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    
    public static final int DEFAULT_TIMEOUT_MINUTES = 30;

    protected final Path workingDirectory;
    protected final int timeoutMinutes;

    protected AbstractScipRunner(Path workingDirectory) {
        this(workingDirectory, DEFAULT_TIMEOUT_MINUTES);
    }

    protected AbstractScipRunner(Path workingDirectory, int timeoutMinutes) {
        this.workingDirectory = workingDirectory;
        this.timeoutMinutes = timeoutMinutes;
    }

    /**
     * Get the working directory.
     */
    public Path getWorkingDirectory() {
        return workingDirectory;
    }

    // ============= Abstract Methods (must implement) =============

    /**
     * Get the command name for this indexer (e.g., "scip-java", "scip-go").
     */
    protected abstract String getToolCommand();

    /**
     * Get the command arguments for indexing.
     * 
     * @param outputPath Path where the index file should be written
     * @return List of command arguments (not including the command itself)
     */
    protected abstract List<String> getIndexArgs(Path outputPath);

    // ============= Hook Methods (can override) =============

    /**
     * Hook called before running the indexer.
     * Override to perform pre-processing (e.g., fix file line endings).
     */
    protected void preProcess() throws ScipException {
        // Default: do nothing
    }

    /**
     * Hook called after successful indexing.
     * Override to perform post-processing.
     * 
     * @param outputPath Path to the generated index file
     */
    protected void postProcess(Path outputPath) throws ScipException {
        // Default: do nothing
    }

    // ============= Template Method =============

    @Override
    public final Path runIndex() throws ScipException {
        return runIndex(null);
    }

    /**
     * Template method that defines the indexing algorithm.
     * This method is final to ensure the algorithm skeleton is not modified.
     */
    @Override
    public final Path runIndex(Path outputPath) throws ScipException {
        // Step 1: Check tool is installed
        if (!isToolInstalled()) {
            throw ScipException.notInstalled(getToolCommand());
        }

        // Step 2: Pre-processing hook
        preProcess();

        // Step 3: Determine output path
        Path scipFile = outputPath != null ? outputPath : workingDirectory.resolve("index.scip");

        // Step 4: Build command
        List<String> command = buildCommand(scipFile);
        logger.info("Running {} in: {}", getToolCommand(), workingDirectory);
        logger.debug("Command: {}", String.join(" ", command));

        // Step 5: Execute command
        executeCommand(command, scipFile);

        // Step 6: Verify output exists
        if (!Files.exists(scipFile)) {
            throw ScipException.outputNotFound(scipFile);
        }

        // Step 7: Post-processing hook
        postProcess(scipFile);

        logger.info("Successfully generated SCIP index: {}", scipFile);
        return scipFile;
    }

    // ============= Common Implementation =============

    /**
     * Build the full command including tool and arguments.
     */
    protected List<String> buildCommand(Path outputPath) {
        List<String> command = new ArrayList<>();
        command.add(getToolCommand());
        command.addAll(getIndexArgs(outputPath));
        return command;
    }

    /**
     * Execute the command and handle output.
     */
    protected void executeCommand(List<String> command, Path expectedOutput) throws ScipException {
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
                    logger.debug("[{}] {}", getToolCommand(), line);
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

        } catch (IOException e) {
            throw new ScipException("Failed to execute " + getToolCommand() + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScipException(getToolCommand() + " execution interrupted", e);
        }
    }

    @Override
    public boolean isToolInstalled() {
        return checkCommand(getToolCommand(), "--version");
    }

    @Override
    public String getToolVersion() {
        return getCommandOutput(getToolCommand(), "--version");
    }

    // ============= Utility Methods =============

    /**
     * Check if a command exists and returns success.
     */
    protected boolean checkCommand(String... args) {
        try {
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    /**
     * Get the first line of output from a command.
     */
    protected String getCommandOutput(String... args) {
        try {
            ProcessBuilder pb = new ProcessBuilder(args);
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
     * Check if a file exists in the working directory.
     */
    protected boolean fileExists(String filename) {
        return Files.exists(workingDirectory.resolve(filename));
    }

    /**
     * Check if any of the given files exist in the working directory.
     */
    protected boolean anyFileExists(String... filenames) {
        for (String filename : filenames) {
            if (fileExists(filename)) {
                return true;
            }
        }
        return false;
    }
}
