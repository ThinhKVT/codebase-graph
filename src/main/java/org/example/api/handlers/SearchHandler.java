package org.example.api.handlers;

import io.javalin.http.Context;
import org.example.agent.CodeSearchAgent;
import org.example.embedding.EmbeddingService;
import org.example.graph.GraphStore;
import org.example.search.SemanticSearchResult;
import org.example.search.SemanticSearchService;
import org.example.vector.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * HTTP handlers for semantic search and agent search endpoints.
 */
public class SearchHandler {

    private static final Logger logger = LoggerFactory.getLogger(SearchHandler.class);
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final SemanticSearchService searchService;
    private final CodeSearchAgent searchAgent;
    private final boolean agentEnabled;

    /**
     * Create handler with semantic search only.
     */
    public SearchHandler(EmbeddingService embeddingService, VectorStore vectorStore,
            GraphStore graphStore, String collectionName) {
        this.searchService = new SemanticSearchService(
            embeddingService, vectorStore, graphStore, collectionName);
        this.searchAgent = new CodeSearchAgent(
            embeddingService, vectorStore, graphStore, collectionName);
        this.agentEnabled = true;
    }

    /**
     * Create handler without agent support.
     */
    public SearchHandler(SemanticSearchService searchService) {
        this.searchService = searchService;
        this.searchAgent = null;
        this.agentEnabled = false;
    }

    // ===================== Semantic Search Endpoint =====================

    /**
     * GET /search/semantic?q=query&limit=10&repository=x&language=y&kind=z
     */
    public void semanticSearch(Context ctx) {
        String query = ctx.queryParam("q");
        if (query == null || query.isBlank()) {
            ctx.status(400).json(new ErrorResponse("query parameter 'q' is required"));
            return;
        }

        int limit = parseLimit(ctx.queryParam("limit"));
        String repository = ctx.queryParam("repository");
        String language = ctx.queryParam("language");
        String kind = ctx.queryParam("kind");

        logger.info("Semantic search: q='{}', limit={}", truncate(query, 30), limit);

        try {
            SemanticSearchService.SearchFilters filters = buildFilters(repository, language, kind);
            List<SemanticSearchResult> results = searchService.search(query, limit, filters);

            ctx.json(new SemanticSearchResponse(
                query,
                results.size(),
                results.stream().map(this::toResultDto).toList()
            ));
        } catch (Exception e) {
            logger.error("Semantic search failed", e);
            ctx.status(500).json(new ErrorResponse("Search failed: " + e.getMessage()));
        }
    }

    /**
     * POST /search/semantic
     * Body: { "query": "...", "limit": 10, "filters": { ... } }
     */
    public void semanticSearchPost(Context ctx) {
        SearchRequest request = ctx.bodyAsClass(SearchRequest.class);
        
        if (request.query() == null || request.query().isBlank()) {
            ctx.status(400).json(new ErrorResponse("query is required"));
            return;
        }

        int limit = request.limit() > 0 ? Math.min(request.limit(), MAX_LIMIT) : DEFAULT_LIMIT;
        
        logger.info("Semantic search (POST): q='{}', limit={}", truncate(request.query(), 30), limit);

        try {
            SemanticSearchService.SearchFilters filters = null;
            if (request.filters() != null) {
                filters = buildFilters(
                    request.filters().get("repository"),
                    request.filters().get("language"),
                    request.filters().get("kind")
                );
            }

            List<SemanticSearchResult> results = searchService.search(request.query(), limit, filters);

            ctx.json(new SemanticSearchResponse(
                request.query(),
                results.size(),
                results.stream().map(this::toResultDto).toList()
            ));
        } catch (Exception e) {
            logger.error("Semantic search failed", e);
            ctx.status(500).json(new ErrorResponse("Search failed: " + e.getMessage()));
        }
    }

    // ===================== Agent Search Endpoint =====================

    /**
     * POST /search/agent
     * Body: { "query": "...", "limit": 10 }
     * 
     * Uses agent workflow: Plan → Execute → Synthesize → Output
     */
    public void agentSearch(Context ctx) {
        if (!agentEnabled || searchAgent == null) {
            ctx.status(501).json(new ErrorResponse("Agent search is not enabled"));
            return;
        }

        SearchRequest request = ctx.bodyAsClass(SearchRequest.class);
        
        if (request.query() == null || request.query().isBlank()) {
            ctx.status(400).json(new ErrorResponse("query is required"));
            return;
        }

        int limit = request.limit() > 0 ? Math.min(request.limit(), MAX_LIMIT) : DEFAULT_LIMIT;
        
        logger.info("Agent search: q='{}', limit={}", truncate(request.query(), 30), limit);

        try {
            CodeSearchAgent.AgentResult agentResult = searchAgent.search(request.query(), limit);

            ctx.json(new AgentSearchResponse(
                request.query(),
                agentResult.plan().getIntent().name(),
                agentResult.plan().getStrategy().name(),
                agentResult.plan().getReasoning(),
                agentResult.output().results().size(),
                agentResult.output().results().stream()
                    .map(this::synthesizedToDto)
                    .toList(),
                agentResult.summary(),
                agentResult.executionTimeMs(),
                agentResult.executionLog()
            ));
        } catch (Exception e) {
            logger.error("Agent search failed", e);
            ctx.status(500).json(new ErrorResponse("Agent search failed: " + e.getMessage()));
        }
    }

    /**
     * GET /search/agent?q=query&limit=10
     */
    public void agentSearchGet(Context ctx) {
        if (!agentEnabled || searchAgent == null) {
            ctx.status(501).json(new ErrorResponse("Agent search is not enabled"));
            return;
        }

        String query = ctx.queryParam("q");
        if (query == null || query.isBlank()) {
            ctx.status(400).json(new ErrorResponse("query parameter 'q' is required"));
            return;
        }

        int limit = parseLimit(ctx.queryParam("limit"));
        
        logger.info("Agent search (GET): q='{}', limit={}", truncate(query, 30), limit);

        try {
            CodeSearchAgent.AgentResult agentResult = searchAgent.search(query, limit);

            ctx.json(new AgentSearchResponse(
                query,
                agentResult.plan().getIntent().name(),
                agentResult.plan().getStrategy().name(),
                agentResult.plan().getReasoning(),
                agentResult.output().results().size(),
                agentResult.output().results().stream()
                    .map(this::synthesizedToDto)
                    .toList(),
                agentResult.summary(),
                agentResult.executionTimeMs(),
                agentResult.executionLog()
            ));
        } catch (Exception e) {
            logger.error("Agent search failed", e);
            ctx.status(500).json(new ErrorResponse("Agent search failed: " + e.getMessage()));
        }
    }

    // ===================== Helper Methods =====================

    private int parseLimit(String limitStr) {
        if (limitStr == null) return DEFAULT_LIMIT;
        try {
            int limit = Integer.parseInt(limitStr);
            return Math.min(Math.max(limit, 1), MAX_LIMIT);
        } catch (NumberFormatException e) {
            return DEFAULT_LIMIT;
        }
    }

    private SemanticSearchService.SearchFilters buildFilters(String repository, String language, String kind) {
        if (repository == null && language == null && kind == null) {
            return null;
        }
        SemanticSearchService.SearchFilters filters = null;
        if (repository != null) {
            filters = SemanticSearchService.SearchFilters.forRepository(repository);
        }
        if (language != null) {
            filters = filters != null ? filters.language(language) : 
                new SemanticSearchService.SearchFilters().language(language);
        }
        if (kind != null) {
            filters = filters != null ? filters.kind(kind) : 
                new SemanticSearchService.SearchFilters().kind(kind);
        }
        return filters;
    }

    private SearchResultDto toResultDto(SemanticSearchResult r) {
        return new SearchResultDto(
            r.getSymbolId(),
            r.getName(),
            r.getKind(),
            r.getFilePath(),
            r.getStartLine(),
            r.getEndLine(),
            r.getSignature(),
            r.getDocumentation(),
            r.getLanguage(),
            Math.round(r.getScore() * 100),
            r.getCallers(),
            r.getCallees()
        );
    }

    private SearchResultDto synthesizedToDto(org.example.search.ResultSynthesizer.SynthesizedResult r) {
        return new SearchResultDto(
            r.symbolId(),
            r.name(),
            r.kind(),
            r.filePath(),
            r.startLine(),
            r.endLine(),
            r.signature(),
            r.documentation(),
            r.language(),
            Math.round(r.combinedScore() * 100),
            r.callers(),
            r.callees()
        );
    }

    private String truncate(String s, int len) {
        if (s == null) return "";
        return s.length() > len ? s.substring(0, len) + "..." : s;
    }

    // ===================== DTOs =====================

    public record SearchRequest(String query, int limit, Map<String, String> filters) {}

    public record SearchResultDto(
        String symbolId,
        String name,
        String kind,
        String filePath,
        Integer startLine,
        Integer endLine,
        String signature,
        String documentation,
        String language,
        int scorePercent,
        List<String> callers,
        List<String> callees
    ) {}

    public record SemanticSearchResponse(
        String query,
        int count,
        List<SearchResultDto> results
    ) {}

    public record AgentSearchResponse(
        String query,
        String intent,
        String strategy,
        String reasoning,
        int count,
        List<SearchResultDto> results,
        String summary,
        long executionTimeMs,
        List<String> executionLog
    ) {}

    public record ErrorResponse(String error) {}
}
