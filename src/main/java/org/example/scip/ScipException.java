package org.example.scip;

import java.nio.file.Path;

/**
 * Exception thrown when SCIP operations fail.
 */
public class ScipException extends Exception {

    public ScipException(String message) {
        super(message);
    }

    public ScipException(String message, Throwable cause) {
        super(message, cause);
    }

    public static ScipException notInstalled() {
        return new ScipException(
            "scip-java is not installed. Please install it:\n" +
            "  Option 1: cs install scip-java\n" +
            "  Option 2: Download from https://github.com/sourcegraph/scip-java/releases"
        );
    }

    public static ScipException timeout(int minutes) {
        return new ScipException("scip-java timed out after " + minutes + " minutes");
    }

    public static ScipException indexingFailed(int exitCode, String output) {
        return new ScipException("scip-java failed with exit code " + exitCode + ":\n" + output);
    }

    public static ScipException outputNotFound(Path expectedPath) {
        return new ScipException("SCIP index file not found at: " + expectedPath);
    }

    public static ScipException parseError(String message, Throwable cause) {
        return new ScipException("Failed to parse SCIP index: " + message, cause);
    }
}

