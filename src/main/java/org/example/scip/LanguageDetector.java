package org.example.scip;

import org.example.model.LanguageSupport;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Detects programming language based on presence of common project files.
 */
public final class LanguageDetector {

    private LanguageDetector() {}

    /**
     * Detect the programming language of a project.
     * 
     * @param repoPath Path to the repository
     * @return Language identifier (java, go, python, typescript)
     */
    public static String detectLanguage(Path repoPath) {
        // Java: Maven or Gradle
        if (exists(repoPath, "pom.xml") || 
            exists(repoPath, "build.gradle") || 
            exists(repoPath, "build.gradle.kts")) {
            return "java";
        }
        
        // Go: Go modules
        if (exists(repoPath, "go.mod")) {
            return "go";
        }
        
        // Python: Various package managers
        if (exists(repoPath, "pyproject.toml") || 
            exists(repoPath, "setup.py") || 
            exists(repoPath, "requirements.txt")) {
            return "python";
        }
        
        // TypeScript/JavaScript
        if (exists(repoPath, "package.json") || 
            exists(repoPath, "tsconfig.json")) {
            return "typescript";
        }
        
        // Default to java if unknown
        return "java";
    }

    /**
     * Detect and return the LanguageSupport enum.
     */
    public static LanguageSupport detectLanguageSupport(Path repoPath) {
        String language = detectLanguage(repoPath);
        return LanguageSupport.fromString(language);
    }

    /**
     * Check if a project contains files for a specific language.
     */
    public static boolean isLanguage(Path repoPath, LanguageSupport language) {
        return switch (language) {
            case JAVA -> exists(repoPath, "pom.xml") || 
                        exists(repoPath, "build.gradle") || 
                        exists(repoPath, "build.gradle.kts");
            case GO -> exists(repoPath, "go.mod");
            case PYTHON -> exists(repoPath, "pyproject.toml") || 
                          exists(repoPath, "setup.py") || 
                          exists(repoPath, "requirements.txt");
            case TYPESCRIPT -> exists(repoPath, "package.json") || 
                              exists(repoPath, "tsconfig.json");
        };
    }

    private static boolean exists(Path base, String filename) {
        return Files.exists(base.resolve(filename));
    }
}
