package org.example.search;

import org.example.model.Symbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Synthesizes search results from multiple sources (Qdrant + Neo4j).
 * 
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Deduplicate results by symbol ID</li>
 *   <li>Merge scores and metadata</li>
 *   <li>Rank results by combined relevance</li>
 *   <li>Build context graph from relationships</li>
 * </ul>
 */
public class ResultSynthesizer {

    private static final Logger logger = LoggerFactory.getLogger(ResultSynthesizer.class);

    // Weight factors for scoring
    private static final float SEMANTIC_WEIGHT = 0.6f;
    private static final float GRAPH_WEIGHT = 0.4f;
    private static final float CALLER_BONUS = 0.1f;
    private static final float CALLEE_BONUS = 0.05f;

    /**
     * Synthesized result combining semantic and graph data.
     */
    public record SynthesizedResult(
        String symbolId,
        String name,
        String kind,
        String filePath,
        Integer startLine,
        Integer endLine,
        String signature,
        String documentation,
        String language,
        float semanticScore,
        float graphScore,
        float combinedScore,
        List<String> callers,
        List<String> callees,
        Map<String, Object> metadata
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String symbolId;
            private String name;
            private String kind;
            private String filePath;
            private Integer startLine;
            private Integer endLine;
            private String signature;
            private String documentation;
            private String language;
            private float semanticScore;
            private float graphScore;
            private float combinedScore;
            private List<String> callers = new ArrayList<>();
            private List<String> callees = new ArrayList<>();
            private Map<String, Object> metadata = new HashMap<>();

            public Builder symbolId(String v) { this.symbolId = v; return this; }
            public Builder name(String v) { this.name = v; return this; }
            public Builder kind(String v) { this.kind = v; return this; }
            public Builder filePath(String v) { this.filePath = v; return this; }
            public Builder startLine(Integer v) { this.startLine = v; return this; }
            public Builder endLine(Integer v) { this.endLine = v; return this; }
            public Builder signature(String v) { this.signature = v; return this; }
            public Builder documentation(String v) { this.documentation = v; return this; }
            public Builder language(String v) { this.language = v; return this; }
            public Builder semanticScore(float v) { this.semanticScore = v; return this; }
            public Builder graphScore(float v) { this.graphScore = v; return this; }
            public Builder combinedScore(float v) { this.combinedScore = v; return this; }
            public Builder callers(List<String> v) { this.callers = v; return this; }
            public Builder callees(List<String> v) { this.callees = v; return this; }
            public Builder metadata(Map<String, Object> v) { this.metadata = v; return this; }
            public Builder addMetadata(String k, Object v) { this.metadata.put(k, v); return this; }

            public SynthesizedResult build() {
                return new SynthesizedResult(symbolId, name, kind, filePath, startLine, endLine,
                    signature, documentation, language, semanticScore, graphScore, combinedScore,
                    callers, callees, metadata);
            }
        }
    }

    /**
     * Synthesis output containing merged results and context.
     */
    public record SynthesisOutput(
        List<SynthesizedResult> results,
        Map<String, List<String>> contextGraph,  // symbol -> [related symbols]
        int totalSemanticResults,
        int totalGraphResults,
        int deduplicatedCount
    ) {}

    /**
     * Synthesize results from semantic search and graph traversal.
     *
     * @param semanticResults Results from Qdrant semantic search
     * @param graphSymbols Additional symbols from Neo4j traversal
     * @param graphRelations Map of symbolId -> related symbolIds
     * @return Synthesized and ranked results
     */
    public SynthesisOutput synthesize(
            List<SemanticSearchResult> semanticResults,
            List<Symbol> graphSymbols,
            Map<String, List<String>> graphRelations) {

        logger.debug("Synthesizing {} semantic results + {} graph symbols",
            semanticResults.size(), graphSymbols.size());

        // Build result map keyed by symbol ID
        Map<String, SynthesizedResult.Builder> resultMap = new LinkedHashMap<>();

        // Add semantic results
        for (SemanticSearchResult sr : semanticResults) {
            String id = sr.getSymbolId();
            if (id == null) continue;

            resultMap.put(id, SynthesizedResult.builder()
                .symbolId(id)
                .name(sr.getName())
                .kind(sr.getKind())
                .filePath(sr.getFilePath())
                .startLine(sr.getStartLine())
                .endLine(sr.getEndLine())
                .signature(sr.getSignature())
                .documentation(sr.getDocumentation())
                .language(sr.getLanguage())
                .semanticScore(sr.getScore())
                .callers(sr.getCallers() != null ? sr.getCallers() : new ArrayList<>())
                .callees(sr.getCallees() != null ? sr.getCallees() : new ArrayList<>()));
        }

        // Add/merge graph symbols
        for (Symbol symbol : graphSymbols) {
            String id = symbol.id();
            SynthesizedResult.Builder builder = resultMap.get(id);
            
            if (builder == null) {
                // New symbol from graph
                builder = SynthesizedResult.builder()
                    .symbolId(id)
                    .name(symbol.displayName() != null ? symbol.displayName() : symbol.name())
                    .kind(symbol.kind() != null ? symbol.kind().name() : null)
                    .filePath(symbol.filePath())
                    .startLine(symbol.startLine())
                    .endLine(symbol.endLine())
                    .signature(symbol.signature())
                    .documentation(symbol.documentation())
                    .semanticScore(0);
                resultMap.put(id, builder);
            }

            // Add graph relations
            List<String> relations = graphRelations.get(id);
            if (relations != null) {
                builder.graphScore(calculateGraphScore(relations.size()));
            }
        }

        // Calculate combined scores and build final results
        List<SynthesizedResult> results = new ArrayList<>();
        for (SynthesizedResult.Builder builder : resultMap.values()) {
            float combined = calculateCombinedScore(
                builder.semanticScore, 
                builder.graphScore,
                builder.callers.size(),
                builder.callees.size());
            builder.combinedScore(combined);
            results.add(builder.build());
        }

        // Sort by combined score descending
        results.sort((a, b) -> Float.compare(b.combinedScore(), a.combinedScore()));

        // Build context graph
        Map<String, List<String>> contextGraph = buildContextGraph(results, graphRelations);

        int deduped = semanticResults.size() + graphSymbols.size() - results.size();
        
        logger.debug("Synthesized {} results (deduped {})", results.size(), deduped);

        return new SynthesisOutput(
            results,
            contextGraph,
            semanticResults.size(),
            graphSymbols.size(),
            Math.max(0, deduped)
        );
    }

    /**
     * Simple overload for semantic-only results.
     */
    public SynthesisOutput synthesize(List<SemanticSearchResult> semanticResults) {
        return synthesize(semanticResults, Collections.emptyList(), Collections.emptyMap());
    }

    /**
     * Calculate graph score based on relationship count.
     */
    private float calculateGraphScore(int relationCount) {
        // Logarithmic scaling to prevent very connected nodes from dominating
        return (float) (Math.log(relationCount + 1) / Math.log(10)) * 0.3f;
    }

    /**
     * Calculate combined score from semantic and graph scores.
     */
    private float calculateCombinedScore(float semantic, float graph, int callerCount, int calleeCount) {
        float base = semantic * SEMANTIC_WEIGHT + graph * GRAPH_WEIGHT;
        
        // Bonus for having callers (indicates the code is used)
        float callerBonus = Math.min(callerCount * CALLER_BONUS, 0.2f);
        
        // Small bonus for having callees (indicates complexity)
        float calleeBonus = Math.min(calleeCount * CALLEE_BONUS, 0.1f);
        
        return Math.min(base + callerBonus + calleeBonus, 1.0f);
    }

    /**
     * Build a context graph showing relationships between results.
     */
    private Map<String, List<String>> buildContextGraph(
            List<SynthesizedResult> results,
            Map<String, List<String>> graphRelations) {
        
        Set<String> resultIds = results.stream()
            .map(SynthesizedResult::symbolId)
            .collect(Collectors.toSet());

        Map<String, List<String>> contextGraph = new HashMap<>();
        
        for (SynthesizedResult result : results) {
            String id = result.symbolId();
            List<String> related = new ArrayList<>();
            
            // Add callers/callees that are in results
            if (result.callers() != null) {
                related.addAll(result.callers().stream()
                    .filter(resultIds::contains)
                    .toList());
            }
            if (result.callees() != null) {
                related.addAll(result.callees().stream()
                    .filter(resultIds::contains)
                    .toList());
            }
            
            // Add from graph relations
            List<String> graphRels = graphRelations.get(id);
            if (graphRels != null) {
                related.addAll(graphRels.stream()
                    .filter(resultIds::contains)
                    .filter(r -> !related.contains(r))
                    .toList());
            }
            
            if (!related.isEmpty()) {
                contextGraph.put(id, related);
            }
        }
        
        return contextGraph;
    }

    /**
     * Format results as a summary string.
     */
    public String formatSummary(SynthesisOutput output) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Found %d results", output.results().size()));
        
        if (output.totalSemanticResults() > 0) {
            sb.append(String.format(" (%d semantic", output.totalSemanticResults()));
        }
        if (output.totalGraphResults() > 0) {
            sb.append(String.format(", %d graph", output.totalGraphResults()));
        }
        if (output.deduplicatedCount() > 0) {
            sb.append(String.format(", %d deduplicated", output.deduplicatedCount()));
        }
        sb.append(")");
        
        // Top result kinds
        Map<String, Long> kindCounts = output.results().stream()
            .filter(r -> r.kind() != null)
            .collect(Collectors.groupingBy(SynthesizedResult::kind, Collectors.counting()));
        
        if (!kindCounts.isEmpty()) {
            sb.append(": ");
            sb.append(kindCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .map(e -> e.getValue() + " " + e.getKey().toLowerCase() + "s")
                .collect(Collectors.joining(", ")));
        }
        
        return sb.toString();
    }
}
