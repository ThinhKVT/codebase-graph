package org.example.scip.runner;

import java.nio.file.Path;
import java.util.List;

/**
 * Python-specific SCIP runner using scip-python CLI.
 * 
 * <p>Supports projects with pyproject.toml, setup.py, or requirements.txt.</p>
 */
public class ScipPythonRunner extends AbstractScipRunner {

    public ScipPythonRunner(Path workingDirectory) {
        super(workingDirectory);
    }

    public ScipPythonRunner(Path workingDirectory, int timeoutMinutes) {
        super(workingDirectory, timeoutMinutes);
    }

    @Override
    protected String getToolCommand() {
        return "scip-python";
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
        return anyFileExists("pyproject.toml", "setup.py", "requirements.txt");
    }

    @Override
    public String detectBuildTool() {
        if (fileExists("pyproject.toml")) {
            return "poetry/pyproject";
        } else if (fileExists("setup.py")) {
            return "setuptools";
        } else if (fileExists("requirements.txt")) {
            return "pip";
        }
        return "unknown";
    }

    // Static convenience methods

    /**
     * Check if scip-python is installed.
     */
    public static boolean isInstalled() {
        try {
            ProcessBuilder pb = new ProcessBuilder("scip-python", "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get scip-python version.
     */
    public static String getVersion() {
        try {
            ProcessBuilder pb = new ProcessBuilder("scip-python", "--version");
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
