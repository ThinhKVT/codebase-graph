package org.example.scip;

import org.example.model.LanguageSupport;
import org.example.scip.runner.ScipGoRunner;
import org.example.scip.runner.ScipJavaRunner;
import org.example.scip.runner.ScipPythonRunner;

import java.nio.file.Path;

/**
 * Factory for creating language-specific SCIP runners.
 */
public final class ScipRunnerFactory {

    private ScipRunnerFactory() {}

    /**
     * Create a SCIP runner for the specified language.
     * 
     * @param language Language name (java, go, python, typescript). If null, auto-detects.
     * @param repoPath Path to the repository
     * @return Appropriate ScipRunner for the language
     * @throws IllegalArgumentException if language is not supported
     */
    public static ScipRunner create(String language, Path repoPath) {
        String lang = language;
        if (lang == null || lang.isBlank()) {
            lang = LanguageDetector.detectLanguage(repoPath);
        }
        
        return switch (lang.toLowerCase()) {
            case "java" -> new ScipJavaRunner(repoPath);
            case "go", "golang" -> new ScipGoRunner(repoPath);
            case "python", "py" -> new ScipPythonRunner(repoPath);
            case "typescript", "ts", "javascript", "js" -> new ScipTypescriptRunner(repoPath);
            default -> throw new IllegalArgumentException("Unsupported language: " + lang);
        };
    }

    /**
     * Create a SCIP runner for the specified language enum.
     */
    public static ScipRunner create(LanguageSupport language, Path repoPath) {
        return switch (language) {
            case JAVA -> new ScipJavaRunner(repoPath);
            case GO -> new ScipGoRunner(repoPath);
            case PYTHON -> new ScipPythonRunner(repoPath);
            case TYPESCRIPT -> new ScipTypescriptRunner(repoPath);
        };
    }

    /**
     * Create a SCIP runner with custom timeout.
     */
    public static ScipRunner create(String language, Path repoPath, int timeoutMinutes) {
        String lang = language;
        if (lang == null || lang.isBlank()) {
            lang = LanguageDetector.detectLanguage(repoPath);
        }
        
        return switch (lang.toLowerCase()) {
            case "java" -> new ScipJavaRunner(repoPath, timeoutMinutes);
            case "go", "golang" -> new ScipGoRunner(repoPath, timeoutMinutes);
            case "python", "py" -> new ScipPythonRunner(repoPath, timeoutMinutes);
            case "typescript", "ts", "javascript", "js" -> new ScipTypescriptRunner(repoPath);
            default -> throw new IllegalArgumentException("Unsupported language: " + lang);
        };
    }
}
