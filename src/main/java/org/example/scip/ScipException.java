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
            "scip tool is not installed. Please install the appropriate scip tool for your language.\n" +
            "  Java: scip-java (https://github.com/sourcegraph/scip-java)\n" +
            "  Python: scip-python (if available)\n" +
            "  TypeScript: scip-typescript (if available)"
        );
    }

    public static ScipException timeout(int minutes) {
        return new ScipException("scip tool timed out after " + minutes + " minutes");
    }

    public static ScipException indexingFailed(int exitCode, String output) {
        return new ScipException("scip tool failed with exit code " + exitCode + ":\n" + output);
    }

    public static ScipException outputNotFound(Path expectedPath) {
        return new ScipException("SCIP index file not found at: " + expectedPath);
    }

    public static ScipException parseError(String message, Throwable cause) {
        return new ScipException("Failed to parse SCIP index: " + message, cause);
    }

    public static ScipException notImplemented(String message) {
        return new ScipException("Not implemented: " + message);
    }
}
