package org.example.agent;

import org.example.search.QueryAnalyzer;

import java.util.*;

/**
 * Represents a search execution plan created by the agent.
 * 
 * <p>The plan consists of steps to execute in order:</p>
 * <ol>
 *   <li>Semantic search (Qdrant)</li>
 *   <li>Graph traversal (Neo4j)</li>
 *   <li>Result synthesis</li>
 * </ol>
 */
public class SearchPlan {

    /**
     * A single step in the execution plan.
     */
    public record Step(
        StepType type,
        String description,
        Map<String, Object> parameters,
        int order
    ) {
        public static Step semanticSearch(String query, int limit) {
            return new Step(
                StepType.SEMANTIC_SEARCH,
                "Search for: " + truncate(query, 50),
                Map.of("query", query, "limit", limit),
                1
            );
        }

        public static Step graphTraversal(String symbolId, TraversalType traversal, int depth) {
            return new Step(
                StepType.GRAPH_TRAVERSAL,
                traversal.name().toLowerCase() + " of " + truncate(symbolId, 30),
                Map.of("symbolId", symbolId, "traversal", traversal, "depth", depth),
                2
            );
        }

        public static Step synthesis(String reason) {
            return new Step(
                StepType.SYNTHESIS,
                "Merge and rank results: " + reason,
                Map.of(),
                3
            );
        }

        private static String truncate(String s, int len) {
            return s.length() > len ? s.substring(0, len) + "..." : s;
        }
    }

    public enum StepType {
        SEMANTIC_SEARCH,  // Query Qdrant
        GRAPH_TRAVERSAL,  // Query Neo4j
        SYNTHESIS         // Merge results
    }

    public enum TraversalType {
        CALLERS,      // Who calls this symbol?
        CALLEES,      // What does this symbol call?
        DEPENDENCIES, // What does this depend on?
        DEPENDENTS,   // What depends on this?
        CONTAINS,     // What's inside this?
        PARENT        // What contains this?
    }

    private final String originalQuery;
    private final QueryAnalyzer.Intent intent;
    private final QueryAnalyzer.Strategy strategy;
    private final List<Step> steps;
    private final String reasoning;
    private final Map<String, String> filters;

    private SearchPlan(Builder builder) {
        this.originalQuery = builder.originalQuery;
        this.intent = builder.intent;
        this.strategy = builder.strategy;
        this.steps = Collections.unmodifiableList(builder.steps);
        this.reasoning = builder.reasoning;
        this.filters = Collections.unmodifiableMap(builder.filters);
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public String getOriginalQuery() { return originalQuery; }
    public QueryAnalyzer.Intent getIntent() { return intent; }
    public QueryAnalyzer.Strategy getStrategy() { return strategy; }
    public List<Step> getSteps() { return steps; }
    public String getReasoning() { return reasoning; }
    public Map<String, String> getFilters() { return filters; }

    /**
     * Check if this plan includes semantic search.
     */
    public boolean hasSemanticSearch() {
        return steps.stream().anyMatch(s -> s.type() == StepType.SEMANTIC_SEARCH);
    }

    /**
     * Check if this plan includes graph traversal.
     */
    public boolean hasGraphTraversal() {
        return steps.stream().anyMatch(s -> s.type() == StepType.GRAPH_TRAVERSAL);
    }

    /**
     * Get semantic search query if present.
     */
    public Optional<String> getSemanticQuery() {
        return steps.stream()
            .filter(s -> s.type() == StepType.SEMANTIC_SEARCH)
            .map(s -> (String) s.parameters().get("query"))
            .findFirst();
    }

    /**
     * Format plan as readable string.
     */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("Plan: ").append(intent.name()).append("\n");
        sb.append("Strategy: ").append(strategy.name()).append("\n");
        sb.append("Reasoning: ").append(reasoning).append("\n");
        sb.append("Steps:\n");
        for (Step step : steps) {
            sb.append("  ").append(step.order()).append(". ")
              .append(step.type().name()).append(": ")
              .append(step.description()).append("\n");
        }
        return sb.toString();
    }

    public static class Builder {
        private String originalQuery;
        private QueryAnalyzer.Intent intent = QueryAnalyzer.Intent.FIND_CODE;
        private QueryAnalyzer.Strategy strategy = QueryAnalyzer.Strategy.QDRANT_THEN_NEO4J;
        private List<Step> steps = new ArrayList<>();
        private String reasoning = "";
        private Map<String, String> filters = new HashMap<>();

        public Builder originalQuery(String q) { this.originalQuery = q; return this; }
        public Builder intent(QueryAnalyzer.Intent i) { this.intent = i; return this; }
        public Builder strategy(QueryAnalyzer.Strategy s) { this.strategy = s; return this; }
        public Builder addStep(Step step) { this.steps.add(step); return this; }
        public Builder reasoning(String r) { this.reasoning = r; return this; }
        public Builder filters(Map<String, String> f) { this.filters = new HashMap<>(f); return this; }
        public Builder addFilter(String k, String v) { this.filters.put(k, v); return this; }

        public SearchPlan build() {
            // Sort steps by order
            steps.sort(Comparator.comparingInt(Step::order));
            return new SearchPlan(this);
        }
    }
}
