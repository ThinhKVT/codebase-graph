package org.example.model;

/**
 * Supported programming languages for code indexing.
 */
public enum LanguageSupport {
    JAVA("java", "scip-java"),
    GO("go", "scip-go"),
    PYTHON("python", "scip-python"),
    TYPESCRIPT("typescript", "scip-typescript");

    private final String languageId;
    private final String indexerTool;

    LanguageSupport(String languageId, String indexerTool) {
        this.languageId = languageId;
        this.indexerTool = indexerTool;
    }

    public String getLanguageId() {
        return languageId;
    }

    public String getIndexerTool() {
        return indexerTool;
    }

    /**
     * Parse language from string (case-insensitive).
     */
    public static LanguageSupport fromString(String language) {
        if (language == null || language.isBlank()) {
            return JAVA; // Default
        }
        String lower = language.toLowerCase().trim();
        return switch (lower) {
            case "java" -> JAVA;
            case "go", "golang" -> GO;
            case "python", "py" -> PYTHON;
            case "typescript", "ts", "javascript", "js" -> TYPESCRIPT;
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        };
    }

    /**
     * Check if language is supported.
     */
    public static boolean isSupported(String language) {
        try {
            fromString(language);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
