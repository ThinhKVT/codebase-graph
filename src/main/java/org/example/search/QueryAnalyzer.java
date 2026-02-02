package org.example.search;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Analyzes user queries to detect intent and extract search parameters.
 * 
 * <p>Intent Types:</p>
 * <ul>
 *   <li>FIND_CODE - Find code by description (semantic search)</li>
 *   <li>FIND_USAGE - Find usages/callers of a symbol</li>
 *   <li>FIND_DEPENDENCIES - Find what a symbol depends on</li>
 *   <li>FIND_SIMILAR - Find similar code patterns</li>
 *   <li>EXPLAIN - Explain what code does</li>
 *   <li>IMPACT - Find impact of changes</li>
 * </ul>
 */
public class QueryAnalyzer {

    /**
     * Intent types for code search queries.
     */
    public enum Intent {
        FIND_CODE,        // General semantic search
        FIND_USAGE,       // Who calls this? Who uses this?
        FIND_DEPENDENCIES,// What does this depend on?
        FIND_SIMILAR,     // Find similar patterns
        EXPLAIN,          // Explain code
        IMPACT            // Impact analysis
    }

    /**
     * Search strategy based on intent.
     */
    public enum Strategy {
        QDRANT_ONLY,      // Pure semantic search
        NEO4J_ONLY,       // Pure graph traversal
        QDRANT_THEN_NEO4J,// Semantic first, enrich with graph
        NEO4J_THEN_QDRANT // Graph first, expand with semantic
    }

    // Patterns for intent detection
    private static final Map<Intent, List<Pattern>> INTENT_PATTERNS = new HashMap<>();
    
    static {
        INTENT_PATTERNS.put(Intent.FIND_USAGE, Arrays.asList(
            Pattern.compile("(?i)who\\s+(calls?|uses?)"),
            Pattern.compile("(?i)(callers?|usages?)\\s+of"),
            Pattern.compile("(?i)where\\s+is\\s+.+\\s+(called|used)"),
            Pattern.compile("(?i)find\\s+(all\\s+)?(usages?|callers?)"),
            Pattern.compile("(?i)references?\\s+to")
        ));
        
        INTENT_PATTERNS.put(Intent.FIND_DEPENDENCIES, Arrays.asList(
            Pattern.compile("(?i)what\\s+does\\s+.+\\s+depend"),
            Pattern.compile("(?i)dependencies\\s+of"),
            Pattern.compile("(?i)depends\\s+on"),
            Pattern.compile("(?i)imports?\\s+of"),
            Pattern.compile("(?i)what\\s+.+\\s+(calls?|uses?)")
        ));
        
        INTENT_PATTERNS.put(Intent.FIND_SIMILAR, Arrays.asList(
            Pattern.compile("(?i)similar\\s+(to|code|patterns?)"),
            Pattern.compile("(?i)like\\s+this"),
            Pattern.compile("(?i)same\\s+pattern"),
            Pattern.compile("(?i)related\\s+(code|methods?|classes?)")
        ));
        
        INTENT_PATTERNS.put(Intent.EXPLAIN, Arrays.asList(
            Pattern.compile("(?i)explain\\s+"),
            Pattern.compile("(?i)what\\s+does\\s+.+\\s+do"),
            Pattern.compile("(?i)how\\s+does\\s+.+\\s+work"),
            Pattern.compile("(?i)describe\\s+")
        ));
        
        INTENT_PATTERNS.put(Intent.IMPACT, Arrays.asList(
            Pattern.compile("(?i)impact\\s+of"),
            Pattern.compile("(?i)if\\s+i\\s+change"),
            Pattern.compile("(?i)affected\\s+by"),
            Pattern.compile("(?i)what\\s+breaks?")
        ));
    }

    /**
     * Result of query analysis.
     */
    public record AnalysisResult(
        String originalQuery,
        Intent intent,
        Strategy strategy,
        String searchQuery,        // Cleaned query for semantic search
        String symbolHint,         // Extracted symbol name if any
        Map<String, String> filters,
        boolean needsGraphEnrichment,
        boolean needsSemanticSearch
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String originalQuery;
            private Intent intent = Intent.FIND_CODE;
            private Strategy strategy = Strategy.QDRANT_THEN_NEO4J;
            private String searchQuery;
            private String symbolHint;
            private Map<String, String> filters = new HashMap<>();
            private boolean needsGraphEnrichment = true;
            private boolean needsSemanticSearch = true;

            public Builder originalQuery(String q) { this.originalQuery = q; return this; }
            public Builder intent(Intent i) { this.intent = i; return this; }
            public Builder strategy(Strategy s) { this.strategy = s; return this; }
            public Builder searchQuery(String q) { this.searchQuery = q; return this; }
            public Builder symbolHint(String h) { this.symbolHint = h; return this; }
            public Builder filters(Map<String, String> f) { this.filters = f; return this; }
            public Builder addFilter(String k, String v) { this.filters.put(k, v); return this; }
            public Builder needsGraphEnrichment(boolean b) { this.needsGraphEnrichment = b; return this; }
            public Builder needsSemanticSearch(boolean b) { this.needsSemanticSearch = b; return this; }

            public AnalysisResult build() {
                if (searchQuery == null) searchQuery = originalQuery;
                return new AnalysisResult(originalQuery, intent, strategy, searchQuery, 
                    symbolHint, filters, needsGraphEnrichment, needsSemanticSearch);
            }
        }
    }

    /**
     * Analyze a query and determine intent and strategy.
     */
    public AnalysisResult analyze(String query) {
        if (query == null || query.isBlank()) {
            return AnalysisResult.builder()
                .originalQuery("")
                .intent(Intent.FIND_CODE)
                .strategy(Strategy.QDRANT_ONLY)
                .needsSemanticSearch(true)
                .needsGraphEnrichment(false)
                .build();
        }

        String cleanQuery = query.trim();
        Intent detectedIntent = detectIntent(cleanQuery);
        Strategy strategy = determineStrategy(detectedIntent);
        String searchQuery = extractSearchQuery(cleanQuery, detectedIntent);
        String symbolHint = extractSymbolHint(cleanQuery);
        Map<String, String> filters = extractFilters(cleanQuery);

        return AnalysisResult.builder()
            .originalQuery(cleanQuery)
            .intent(detectedIntent)
            .strategy(strategy)
            .searchQuery(searchQuery)
            .symbolHint(symbolHint)
            .filters(filters)
            .needsSemanticSearch(needsSemanticSearch(detectedIntent))
            .needsGraphEnrichment(needsGraphEnrichment(detectedIntent))
            .build();
    }

    /**
     * Detect intent from query patterns.
     */
    private Intent detectIntent(String query) {
        for (Map.Entry<Intent, List<Pattern>> entry : INTENT_PATTERNS.entrySet()) {
            for (Pattern pattern : entry.getValue()) {
                if (pattern.matcher(query).find()) {
                    return entry.getKey();
                }
            }
        }
        return Intent.FIND_CODE;
    }

    /**
     * Determine search strategy based on intent.
     */
    private Strategy determineStrategy(Intent intent) {
        return switch (intent) {
            case FIND_CODE, FIND_SIMILAR, EXPLAIN -> Strategy.QDRANT_THEN_NEO4J;
            case FIND_USAGE, FIND_DEPENDENCIES, IMPACT -> Strategy.NEO4J_THEN_QDRANT;
        };
    }

    /**
     * Extract the core search query, removing intent keywords.
     */
    private String extractSearchQuery(String query, Intent intent) {
        String cleaned = query;
        
        // Remove common prefixes
        cleaned = cleaned.replaceAll("(?i)^(find|search|show|get|list)\\s+(me\\s+)?", "");
        cleaned = cleaned.replaceAll("(?i)^(who|what|where|how)\\s+(does|is|are)?\\s*", "");
        cleaned = cleaned.replaceAll("(?i)^(callers?|usages?|dependencies?)\\s+of\\s+", "");
        cleaned = cleaned.replaceAll("(?i)\\s+(called|used|depend(s|ing)?)\\s*$", "");
        
        // Remove filter keywords
        cleaned = cleaned.replaceAll("(?i)\\s+in\\s+(java|python|go|typescript)\\s*", " ");
        cleaned = cleaned.replaceAll("(?i)\\s+(class|method|function|interface)\\s*", " ");
        
        return cleaned.trim();
    }

    /**
     * Extract potential symbol name from query.
     */
    private String extractSymbolHint(String query) {
        // Look for quoted strings
        var quotedPattern = Pattern.compile("[\"']([^\"']+)[\"']");
        var matcher = quotedPattern.matcher(query);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // Look for CamelCase or snake_case patterns
        var symbolPattern = Pattern.compile("\\b([A-Z][a-zA-Z0-9]*(?:\\.[A-Z][a-zA-Z0-9]*)*(?:#\\w+)?)\\b");
        matcher = symbolPattern.matcher(query);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // Look for method-like patterns
        var methodPattern = Pattern.compile("\\b(\\w+(?:\\.\\w+)*\\(\\))\\b");
        matcher = methodPattern.matcher(query);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * Extract filters from query (language, kind, etc.).
     */
    private Map<String, String> extractFilters(String query) {
        Map<String, String> filters = new HashMap<>();

        // Language filter
        var langPattern = Pattern.compile("(?i)\\b(in|for)\\s+(java|python|go|typescript|javascript)\\b");
        var matcher = langPattern.matcher(query);
        if (matcher.find()) {
            filters.put("language", matcher.group(2).toLowerCase());
        }

        // Kind filter
        var kindPattern = Pattern.compile("(?i)\\b(class|method|function|interface|constructor)\\b");
        matcher = kindPattern.matcher(query);
        if (matcher.find()) {
            filters.put("kind", matcher.group(1).toUpperCase());
        }

        return filters;
    }

    private boolean needsSemanticSearch(Intent intent) {
        return switch (intent) {
            case FIND_CODE, FIND_SIMILAR, EXPLAIN -> true;
            case FIND_USAGE, FIND_DEPENDENCIES, IMPACT -> false;
        };
    }

    private boolean needsGraphEnrichment(Intent intent) {
        return switch (intent) {
            case FIND_CODE -> true;  // Basic enrichment
            case FIND_USAGE, FIND_DEPENDENCIES, IMPACT -> true;  // Primary source
            case FIND_SIMILAR, EXPLAIN -> false;
        };
    }
}
