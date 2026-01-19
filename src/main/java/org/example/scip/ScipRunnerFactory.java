package org.example.scip;

import java.nio.file.Path;

public final class ScipRunnerFactory {

    private ScipRunnerFactory() {}

    public static ScipRunner create(String language, Path repoPath) {
        String lang = language;
        if (lang == null || lang.isBlank()) {
            lang = LanguageDetector.detectLanguage(repoPath);
        }
        switch (lang.toLowerCase()) {
            case "java":
                return new ScipJavaRunner(repoPath);
            case "python":
                return new ScipPythonRunner(repoPath);
            case "typescript":
            case "ts":
            case "js":
                return new ScipTypescriptRunner(repoPath);
            default:
                throw new IllegalArgumentException("Unsupported language: " + lang);
        }
    }
}

