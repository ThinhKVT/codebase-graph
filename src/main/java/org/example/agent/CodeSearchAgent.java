package org.example.agent;

import org.example.embedding.EmbeddingService;
import org.example.graph.GraphStore;
import org.example.model.Symbol;
import org.example.search.*;
import org.example.vector.SearchResult;
import org.example.vector.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent for intelligent code search using Plan → Execute → Synthesize → Output workflow.
 * 
 * <p>Workflow:</p>
 * <ol>
 *   <li><b>PLAN</b>: Analyze query, detect intent, create execution plan</li>
 *   <li><b>EXECUTE</b>: Run semantic search and/or graph traversal</li>
 *   <li><b>SYNTHESIZE</b>: Merge, deduplicate, and rank results</li>
 *   <li><b>OUTPUT</b>: Format and return results</li>
 * </ol>
 */
public class CodeSearchAgent {

    private static final Logger logger = LoggerFactory.getLogger(CodeSearchAgent.class);
    private static final int DEFAULT_LIMIT = 10;
    private static final int DEFAULT_GRAPH_DEPTH = 2;

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final GraphStore graphStore;
    private final String collectionName;
    
    private final QueryAnalyzer queryAnalyzer;
    private final ResultSynthesizer resultSynthesizer;

    public CodeSearchAgent(EmbeddingService embeddingService, VectorStore vectorStore,
            GraphStore graphStore, String collectionName) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.graphStore = graphStore;
        this.collectionName = collectionName;
        this.queryAnalyzer = new QueryAnalyzer();
        this.resultSynthesizer = new ResultSynthesizer();
    }

    /**
     * Agent search result containing plan, execution details, and results.
     */
    public record AgentResult(
        SearchPlan plan,
        ResultSynthesizer.SynthesisOutput output,
        String summary,
        long executionTimeMs,
        List<String> executionLog
    ) {}

    /**
     * Execute an intelligent search with the full agent workflow.
     */
    public AgentResult search(String query) {
        return search(query, DEFAULT_LIMIT);
    }

    /**
     * Execute an intelligent search with custom limit.
     */
    public AgentResult search(String query, int limit) {
        long startTime = System.currentTimeMillis();
        List<String> executionLog = new ArrayList<>();

        // ============= PHASE 1: PLAN =============
        executionLog.add("=== PHASE 1: PLAN ===");
        
        QueryAnalyzer.AnalysisResult analysis = queryAnalyzer.analyze(query);
        executionLog.add("Intent: " + analysis.intent());
        executionLog.add("Strategy: " + analysis.strategy());
        if (analysis.symbolHint() != null) {
            executionLog.add("Symbol hint: " + analysis.symbolHint());
        }

        SearchPlan plan = createPlan(analysis, limit);
        executionLog.add("Plan: " + plan.getSteps().size() + " steps");
        logger.info("Created plan for query '{}': {}", truncate(query, 30), analysis.intent());

        // ============= PHASE 2: EXECUTE =============
        executionLog.add("=== PHASE 2: EXECUTE ===");
        
        List<SemanticSearchResult> semanticResults = new ArrayList<>();
        List<Symbol> graphSymbols = new ArrayList<>();
        Map<String, List<String>> graphRelations = new HashMap<>();

        for (SearchPlan.Step step : plan.getSteps()) {
            try {
                switch (step.type()) {
                    case SEMANTIC_SEARCH -> {
                        String searchQuery = (String) step.parameters().get("query");
                        int searchLimit = (int) step.parameters().get("limit");
                        
                        executionLog.add("Executing semantic search: " + truncate(searchQuery, 40));
                        semanticResults = executeSemanticSearch(searchQuery, searchLimit, plan.getFilters());
                        executionLog.add("Found " + semanticResults.size() + " semantic results");
                    }
                    case GRAPH_TRAVERSAL -> {
                        String symbolId = (String) step.parameters().get("symbolId");
                        SearchPlan.TraversalType traversal = (SearchPlan.TraversalType) step.parameters().get("traversal");
                        int depth = (int) step.parameters().get("depth");
                        
                        executionLog.add("Executing graph traversal: " + traversal + " of " + truncate(symbolId, 30));
                        var traversalResult = executeGraphTraversal(symbolId, traversal, depth);
                        graphSymbols.addAll(traversalResult.symbols());
                        graphRelations.putAll(traversalResult.relations());
                        executionLog.add("Found " + traversalResult.symbols().size() + " graph results");
                    }
                    case SYNTHESIS -> {
                        // Handled in Phase 3
                    }
                }
            } catch (Exception e) {
                executionLog.add("ERROR in " + step.type() + ": " + e.getMessage());
                logger.warn("Step execution failed: {}", e.getMessage());
            }
        }

        // ============= PHASE 3: SYNTHESIZE =============
        executionLog.add("=== PHASE 3: SYNTHESIZE ===");
        
        ResultSynthesizer.SynthesisOutput output = resultSynthesizer.synthesize(
            semanticResults, graphSymbols, graphRelations);
        
        executionLog.add("Synthesized " + output.results().size() + " results");
        if (output.deduplicatedCount() > 0) {
            executionLog.add("Deduplicated " + output.deduplicatedCount() + " results");
        }

        // ============= PHASE 4: OUTPUT =============
        executionLog.add("=== PHASE 4: OUTPUT ===");
        
        String summary = resultSynthesizer.formatSummary(output);
        executionLog.add(summary);

        long executionTime = System.currentTimeMillis() - startTime;
        logger.info("Agent search completed in {}ms: {}", executionTime, summary);

        return new AgentResult(plan, output, summary, executionTime, executionLog);
    }

    /**
     * Create execution plan from query analysis.
     */
    private SearchPlan createPlan(QueryAnalyzer.AnalysisResult analysis, int limit) {
        SearchPlan.Builder planBuilder = SearchPlan.builder()
            .originalQuery(analysis.originalQuery())
            .intent(analysis.intent())
            .strategy(analysis.strategy())
            .filters(analysis.filters());

        String reasoning = "General code search";
        
        switch (analysis.intent()) {
            case FIND_CODE, FIND_SIMILAR -> {
                reasoning = "Semantic search primary, graph for context";
                planBuilder.addStep(SearchPlan.Step.semanticSearch(analysis.searchQuery(), limit));
                planBuilder.addStep(SearchPlan.Step.synthesis("Merge semantic results with graph context"));
            }
            case FIND_USAGE -> {
                reasoning = "Graph traversal to find callers, semantic for similar usage patterns";
                if (analysis.symbolHint() != null) {
                    planBuilder.addStep(SearchPlan.Step.graphTraversal(
                        analysis.symbolHint(), SearchPlan.TraversalType.CALLERS, DEFAULT_GRAPH_DEPTH));
                }
                planBuilder.addStep(SearchPlan.Step.semanticSearch(analysis.searchQuery(), limit));
                planBuilder.addStep(SearchPlan.Step.synthesis("Combine direct callers with semantic matches"));
            }
            case FIND_DEPENDENCIES -> {
                reasoning = "Graph traversal to find dependencies";
                if (analysis.symbolHint() != null) {
                    planBuilder.addStep(SearchPlan.Step.graphTraversal(
                        analysis.symbolHint(), SearchPlan.TraversalType.DEPENDENCIES, DEFAULT_GRAPH_DEPTH));
                }
                planBuilder.addStep(SearchPlan.Step.semanticSearch(analysis.searchQuery(), limit));
                planBuilder.addStep(SearchPlan.Step.synthesis("Merge dependencies with semantic results"));
            }
            case IMPACT -> {
                reasoning = "Reverse graph traversal to find dependents";
                if (analysis.symbolHint() != null) {
                    planBuilder.addStep(SearchPlan.Step.graphTraversal(
                        analysis.symbolHint(), SearchPlan.TraversalType.DEPENDENTS, DEFAULT_GRAPH_DEPTH));
                }
                planBuilder.addStep(SearchPlan.Step.synthesis("Analyze impact through dependents"));
            }
            case EXPLAIN -> {
                reasoning = "Semantic search for documentation and related code";
                planBuilder.addStep(SearchPlan.Step.semanticSearch(analysis.searchQuery(), limit));
                planBuilder.addStep(SearchPlan.Step.synthesis("Gather context for explanation"));
            }
        }

        return planBuilder.reasoning(reasoning).build();
    }

    /**
     * Execute semantic search against Qdrant.
     */
    private List<SemanticSearchResult> executeSemanticSearch(String query, int limit, 
            Map<String, String> filters) {
        try {
            float[] queryVector = embeddingService.embed(query);
            
            Map<String, Object> filterMap = filters.isEmpty() ? null : new HashMap<>(filters);
            
            List<SearchResult> results = vectorStore.search(collectionName, queryVector, limit, filterMap);
            
            return results.stream()
                .map(this::toSemanticSearchResult)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            logger.error("Semantic search failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Execute graph traversal against Neo4j.
     */
    private GraphTraversalResult executeGraphTraversal(String symbolId, 
            SearchPlan.TraversalType traversal, int depth) {
        List<Symbol> symbols = new ArrayList<>();
        Map<String, List<String>> relations = new HashMap<>();

        try {
            switch (traversal) {
                case CALLERS, DEPENDENTS -> {
                    symbols = graphStore.findDependents(symbolId);
                    relations.put(symbolId, symbols.stream().map(Symbol::id).toList());
                }
                case CALLEES, DEPENDENCIES -> {
                    symbols = graphStore.findDependencies(symbolId);
                    relations.put(symbolId, symbols.stream().map(Symbol::id).toList());
                }
                case CONTAINS -> {
                    // Find symbols contained in this symbol
                    var refs = graphStore.findReferencesFromSymbol(symbolId);
                    var containedIds = refs.stream()
                        .filter(r -> r.kind().name().equals("CONTAINS"))
                        .map(r -> r.toSymbolId())
                        .toList();
                    for (String id : containedIds) {
                        graphStore.findSymbolById(id).ifPresent(symbols::add);
                    }
                    relations.put(symbolId, containedIds);
                }
                case PARENT -> {
                    var refs = graphStore.findReferencesToSymbol(symbolId);
                    var parentIds = refs.stream()
                        .filter(r -> r.kind().name().equals("CONTAINS"))
                        .map(r -> r.fromSymbolId())
                        .toList();
                    for (String id : parentIds) {
                        graphStore.findSymbolById(id).ifPresent(symbols::add);
                    }
                    relations.put(symbolId, parentIds);
                }
            }
        } catch (Exception e) {
            logger.error("Graph traversal failed: {}", e.getMessage());
        }

        return new GraphTraversalResult(symbols, relations);
    }

    private record GraphTraversalResult(List<Symbol> symbols, Map<String, List<String>> relations) {}

    private SemanticSearchResult toSemanticSearchResult(SearchResult sr) {
        return SemanticSearchResult.builder()
            .id(sr.getId())
            .score(sr.getScore())
            .symbolId(sr.getSymbolId())
            .repository(sr.getRepository())
            .filePath(sr.getFilePath())
            .name(sr.getName())
            .kind(sr.getKind())
            .signature(sr.getSignature())
            .documentation(sr.getDocumentation())
            .startLine(sr.getStartLine())
            .endLine(sr.getEndLine())
            .language(sr.getLanguage())
            .build();
    }

    private String truncate(String s, int len) {
        if (s == null) return "";
        return s.length() > len ? s.substring(0, len) + "..." : s;
    }
}
