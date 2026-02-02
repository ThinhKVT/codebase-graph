package org.example.search;

import org.example.embedding.EmbeddingService;
import org.example.graph.GraphStore;
import org.example.model.Symbol;
import org.example.vector.SearchResult;
import org.example.vector.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Semantic search service implementing Qdrant-first, Neo4j-enrich strategy.
 * 
 * <p>Search flow:</p>
 * <ol>
 *   <li>Embed query text</li>
 *   <li>Search Qdrant for similar vectors</li>
 *   <li>Enrich results with Neo4j graph context</li>
 *   <li>Return unified results</li>
 * </ol>
 */
public class SemanticSearchService {

    private static final Logger logger = LoggerFactory.getLogger(SemanticSearchService.class);
    private static final int DEFAULT_LIMIT = 10;

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final GraphStore graphStore;
    private final String collectionName;

    public SemanticSearchService(EmbeddingService embeddingService, 
            VectorStore vectorStore, GraphStore graphStore, String collectionName) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.graphStore = graphStore;
        this.collectionName = collectionName;
    }

    /**
     * Search for code semantically similar to the query.
     *
     * @param query Natural language query or code snippet
     * @return List of search results
     */
    public List<SemanticSearchResult> search(String query) {
        return search(query, DEFAULT_LIMIT, null);
    }

    /**
     * Search with limit.
     */
    public List<SemanticSearchResult> search(String query, int limit) {
        return search(query, limit, null);
    }

    /**
     * Search with filters.
     */
    public List<SemanticSearchResult> search(String query, int limit, SearchFilters filters) {
        logger.info("Semantic search: \"{}\" (limit={})", truncate(query, 50), limit);
        
        try {
            // 1. Embed the query
            float[] queryVector = embeddingService.embed(query);
            
            // 2. Build filter map
            Map<String, Object> filterMap = filters != null ? filters.toMap() : null;
            
            // 3. Search Qdrant
            List<SearchResult> vectorResults = vectorStore.search(
                collectionName, queryVector, limit, filterMap);
            
            logger.debug("Found {} vector matches", vectorResults.size());
            
            // 4. Enrich with Neo4j context
            List<SemanticSearchResult> enrichedResults = vectorResults.stream()
                .map(this::enrichWithGraphContext)
                .collect(Collectors.toList());
            
            return enrichedResults;
            
        } catch (Exception e) {
            logger.error("Semantic search failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Search for symbols related to a specific symbol (find similar patterns).
     */
    public List<SemanticSearchResult> findSimilar(String symbolId, int limit) {
        try {
            // Get symbol from graph
            Optional<Symbol> symbolOpt = graphStore.findSymbolById(symbolId);
            if (symbolOpt.isEmpty()) {
                logger.warn("Symbol not found: {}", symbolId);
                return Collections.emptyList();
            }
            
            Symbol symbol = symbolOpt.get();
            
            // Build query from symbol
            String query = buildSymbolQuery(symbol);
            
            return search(query, limit + 1, null).stream()
                .filter(r -> !r.getSymbolId().equals(symbolId)) // Exclude self
                .limit(limit)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            logger.error("Find similar failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Enrich a vector search result with graph context.
     */
    private SemanticSearchResult enrichWithGraphContext(SearchResult vectorResult) {
        SemanticSearchResult.Builder builder = SemanticSearchResult.builder()
            .id(vectorResult.getId())
            .score(vectorResult.getScore())
            .symbolId(vectorResult.getSymbolId())
            .repository(vectorResult.getRepository())
            .filePath(vectorResult.getFilePath())
            .name(vectorResult.getName())
            .kind(vectorResult.getKind())
            .signature(vectorResult.getSignature())
            .documentation(vectorResult.getDocumentation())
            .startLine(vectorResult.getStartLine())
            .endLine(vectorResult.getEndLine())
            .language(vectorResult.getLanguage());

        // Try to enrich with graph context
        String symbolId = vectorResult.getSymbolId();
        if (symbolId != null && graphStore != null) {
            try {
                // Get dependents (callers)
                List<Symbol> dependents = graphStore.findDependents(symbolId);
                builder.callers(dependents.stream()
                    .limit(3)
                    .map(Symbol::id)
                    .collect(Collectors.toList()));
                
                // Get dependencies (callees)
                List<Symbol> dependencies = graphStore.findDependencies(symbolId);
                builder.callees(dependencies.stream()
                    .limit(3)
                    .map(Symbol::id)
                    .collect(Collectors.toList()));
                    
            } catch (Exception e) {
                logger.debug("Could not enrich with graph context: {}", e.getMessage());
            }
        }

        return builder.build();
    }

    private String buildSymbolQuery(Symbol symbol) {
        StringBuilder sb = new StringBuilder();
        if (symbol.displayName() != null) {
            sb.append(symbol.displayName()).append(" ");
        }
        if (symbol.signature() != null) {
            sb.append(symbol.signature()).append(" ");
        }
        if (symbol.documentation() != null) {
            sb.append(symbol.documentation());
        }
        return sb.toString().trim();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }

    /**
     * Filters for semantic search.
     */
    public static class SearchFilters {
        private String repository;
        private String language;
        private String kind;

        public static SearchFilters forRepository(String repository) {
            SearchFilters f = new SearchFilters();
            f.repository = repository;
            return f;
        }

        public SearchFilters language(String language) {
            this.language = language;
            return this;
        }

        public SearchFilters kind(String kind) {
            this.kind = kind;
            return this;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            if (repository != null) map.put("repository", repository);
            if (language != null) map.put("language", language);
            if (kind != null) map.put("kind", kind);
            return map.isEmpty() ? null : map;
        }
    }
}
