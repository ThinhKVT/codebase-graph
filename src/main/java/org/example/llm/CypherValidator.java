package org.example.llm;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates and sanitizes Cypher for read-only execution (prevents injection).
 */
public class CypherValidator {

    private static final Set<String> FORBIDDEN_UPPER = Set.of(
        "CREATE", "DELETE", "DROP", "REMOVE", "SET", "MERGE",
        "CALL {", "FOREACH", "LOAD", "ALTER", "START", "STOP"
    );

    private static final Pattern LIMIT_PATTERN = Pattern.compile("\\bLIMIT\\s+(\\d+)\\b", Pattern.CASE_INSENSITIVE);

    private static final int MAX_LIMIT = 500;
    private static final int DEFAULT_LIMIT = 50;

    /**
     * Check if the Cypher string is allowed (read-only: MATCH, RETURN, WITH, WHERE, ORDER BY, LIMIT).
     */
    public boolean isReadOnly(String cypher) {
        if (cypher == null || cypher.isBlank()) return false;
        String upper = cypher.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
        for (String forbidden : FORBIDDEN_UPPER) {
            if (upper.contains(forbidden)) return false;
        }
        return true;
    }

    /**
     * Validate: read-only and basic structure (must contain MATCH or RETURN for read).
     */
    public boolean isValid(String cypher) {
        if (cypher == null || cypher.isBlank()) return false;
        if (!isReadOnly(cypher)) return false;
        String upper = cypher.toUpperCase(Locale.ROOT).trim();
        return upper.contains("RETURN") || upper.startsWith("MATCH");
    }

    /**
     * Sanitize: ensure LIMIT is present and capped for list queries.
     */
    public String sanitize(String cypher) {
        if (cypher == null) return "";
        String trimmed = cypher.trim();
        if (trimmed.isBlank()) return trimmed;
        if (!isReadOnly(trimmed)) return trimmed;
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (upper.contains("RETURN") && !upper.contains("COUNT(") && !upper.contains("LIMIT")) {
            trimmed = trimmed.replaceFirst("(?i)(\\s*)$", " LIMIT " + DEFAULT_LIMIT + "$1");
        }
        var limitMatcher = LIMIT_PATTERN.matcher(trimmed);
        while (limitMatcher.find()) {
            int n = Integer.parseInt(limitMatcher.group(1));
            if (n > MAX_LIMIT) {
                trimmed = limitMatcher.replaceFirst("LIMIT " + MAX_LIMIT);
                limitMatcher = LIMIT_PATTERN.matcher(trimmed);
            }
        }
        return trimmed.trim();
    }
}
