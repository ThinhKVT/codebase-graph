package org.example.scip;

import java.nio.file.Path;

public interface ScipRunner {

    /**
     * Check if the language-specific scip tool is installed and available on PATH.
     */
    static boolean isInstalled() { return false; }

    /**
     * Get the tool version string if available, or "unknown".
     */
    static String getVersion() { return "unknown"; }

    /**
     * Run the indexer and return the path to the generated .scip file.
     */
    Path runIndex() throws ScipException;

    /**
     * Run the indexer with a specific output path.
     */
    Path runIndex(Path outputPath) throws ScipException;

    /**
     * Check whether the repository at the working directory looks like a valid project for this language.
     */
    boolean isValidProject();

    /**
     * Detect the build tool for the project (e.g. maven, gradle, npm, etc.).
     */
    String detectBuildTool();

    /**
     * Instance-level check if the underlying CLI/tool is installed. Implementations should delegate to static checks.
     */
    boolean isToolInstalled();

    /**
     * Instance-level tool version string.
     */
    String getToolVersion();
}
