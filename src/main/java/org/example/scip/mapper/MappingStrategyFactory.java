package org.example.scip.mapper;

import org.example.model.LanguageSupport;
import org.example.scip.mapper.go.GoStrategy;
import org.example.scip.mapper.java.JavaStrategy;
import org.example.scip.mapper.python.PythonStrategy;

/**
 * Factory for creating language-specific mapping strategies.
 */
public final class MappingStrategyFactory {

    private MappingStrategyFactory() {}

    /**
     * Create a mapping strategy for the given language.
     *
     * @param language Language identifier (java, go, python)
     * @return Appropriate LanguageMappingStrategy
     * @throws IllegalArgumentException if language is not supported
     */
    public static LanguageMappingStrategy create(String language) {
        if (language == null || language.isBlank()) {
            return new JavaStrategy(); // Default
        }
        
        return switch (language.toLowerCase()) {
            case "java" -> new JavaStrategy();
            case "go", "golang" -> new GoStrategy();
            case "python", "py" -> new PythonStrategy();
            case "typescript", "ts", "javascript", "js" -> new JavaStrategy(); // Fallback
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        };
    }

    /**
     * Create a mapping strategy for the given LanguageSupport enum.
     */
    public static LanguageMappingStrategy create(LanguageSupport language) {
        return switch (language) {
            case JAVA -> new JavaStrategy();
            case GO -> new GoStrategy();
            case PYTHON -> new PythonStrategy();
            case TYPESCRIPT -> new JavaStrategy(); // Fallback for now
        };
    }

    /**
     * Get all available strategies.
     */
    public static java.util.List<LanguageMappingStrategy> getAllStrategies() {
        return java.util.List.of(
            new JavaStrategy(),
            new GoStrategy(),
            new PythonStrategy()
        );
    }
}
