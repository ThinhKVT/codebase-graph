package org.example.model;

import java.util.Objects;

/**
 * Represents a source file in the repository.
 */
public record SourceFile(
    String path,
    String relativePath,
    String hash,
    String language
) {
    public SourceFile {
        Objects.requireNonNull(path, "path cannot be null");
        Objects.requireNonNull(relativePath, "relativePath cannot be null");
    }

    public static SourceFile of(String path, String relativePath) {
        return new SourceFile(path, relativePath, null, null);
    }

    public static SourceFile of(String path, String relativePath, String language) {
        return new SourceFile(path, relativePath, null, language);
    }

    public static String detectLanguage(String path) {
        if (path == null) return "unknown";
        String lower = path.toLowerCase();
        if (lower.endsWith(".java")) return "java";
        if (lower.endsWith(".py")) return "python";
        if (lower.endsWith(".ts")) return "typescript";
        if (lower.endsWith(".js")) return "javascript";
        return "unknown";
    }
}

