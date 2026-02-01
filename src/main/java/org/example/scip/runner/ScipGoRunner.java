package org.example.scip.runner;

import java.nio.file.Path;
import java.util.List;

/**
 * Go-specific SCIP runner using scip-go CLI.
 * 
 * <p>Supports Go modules (go.mod) projects.</p>
 */
public class ScipGoRunner extends AbstractScipRunner {

    public ScipGoRunner(Path workingDirectory) {
        super(workingDirectory);
    }

    public ScipGoRunner(Path workingDirectory, int timeoutMinutes) {
        super(workingDirectory, timeoutMinutes);
    }

    @Override
    protected String getToolCommand() {
        return "scip-go";
    }

    @Override
    protected List<String> getIndexArgs(Path outputPath) {
        return List.of(
            "--output",
            outputPath.toAbsolutePath().toString()
        );
    }

    @Override
    public boolean isValidProject() {
        return fileExists("go.mod");
    }

    @Override
    public String detectBuildTool() {
        if (fileExists("go.mod")) {
            return "go modules";
        }
        return "unknown";
    }

    // Static convenience methods

    /**
     * Check if scip-go is installed.
     */
    public static boolean isInstalled() {
        try {
            ProcessBuilder pb = new ProcessBuilder("scip-go", "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get scip-go version.
     */
    public static String getVersion() {
        try {
            ProcessBuilder pb = new ProcessBuilder("scip-go", "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                process.waitFor();
                return line != null ? line.trim() : "unknown";
            }
        } catch (Exception e) {
            return "unknown";
        }
    }
}
