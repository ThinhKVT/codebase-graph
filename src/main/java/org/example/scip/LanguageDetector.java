package org.example.scip;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Minimal language detector based on presence of common project files.
 */
public final class LanguageDetector {

    private LanguageDetector() {}

    public static String detectLanguage(Path repoPath) {
        if (Files.exists(repoPath.resolve("pom.xml")) || Files.exists(repoPath.resolve("build.gradle")) || Files.exists(repoPath.resolve("build.gradle.kts"))) {
            return "java";
        }
        if (Files.exists(repoPath.resolve("pyproject.toml")) || Files.exists(repoPath.resolve("setup.py")) || Files.exists(repoPath.resolve("requirements.txt"))) {
            return "python";
        }
        if (Files.exists(repoPath.resolve("package.json")) || Files.exists(repoPath.resolve("tsconfig.json"))) {
            return "typescript";
        }
        // Default to java if unknown
        return "java";
    }
}

