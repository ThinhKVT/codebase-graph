package org.example.cli;

import org.example.config.OllamaConfig;
import org.example.config.QdrantConfig;
import org.example.embedding.OllamaEmbeddingService;
import org.example.graph.Neo4jGraphStore;
import org.example.search.SemanticSearchResult;
import org.example.search.SemanticSearchService;
import org.example.vector.QdrantVectorStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * CLI command for semantic code search.
 */
@Command(
    name = "search",
    description = "Semantic search for code symbols",
    mixinStandardHelpOptions = true
)
public class SearchCommand implements Callable<Integer> {

    @Parameters(
        index = "0",
        description = "Search query (natural language or code)",
        arity = "1..*"
    )
    private List<String> queryParts;

    @Option(
        names = {"-n", "--limit"},
        description = "Maximum number of results",
        defaultValue = "10"
    )
    private int limit;

    @Option(
        names = {"-r", "--repository"},
        description = "Filter by repository name"
    )
    private String repository;

    @Option(
        names = {"-l", "--language"},
        description = "Filter by language (java, python, go, etc.)"
    )
    private String language;

    @Option(
        names = {"-k", "--kind"},
        description = "Filter by symbol kind (METHOD, CLASS, FUNCTION, etc.)"
    )
    private String kind;

    @Option(
        names = {"--neo4j-uri"},
        description = "Neo4j connection URI",
        defaultValue = "bolt://localhost:7687"
    )
    private String neo4jUri;

    @Option(
        names = {"--neo4j-user"},
        description = "Neo4j username",
        defaultValue = "neo4j"
    )
    private String neo4jUser;

    @Option(
        names = {"--neo4j-password"},
        description = "Neo4j password",
        defaultValue = "codebase123"
    )
    private String neo4jPassword;

    @Option(
        names = {"--qdrant-host"},
        description = "Qdrant host",
        defaultValue = "localhost"
    )
    private String qdrantHost;

    @Option(
        names = {"--qdrant-port"},
        description = "Qdrant gRPC port",
        defaultValue = "6334"
    )
    private int qdrantPort;

    @Option(
        names = {"--ollama-host"},
        description = "Ollama host",
        defaultValue = "localhost"
    )
    private String ollamaHost;

    @Option(
        names = {"--ollama-port"},
        description = "Ollama port",
        defaultValue = "11434"
    )
    private int ollamaPort;

    @Option(
        names = {"--collection"},
        description = "Qdrant collection name",
        defaultValue = "code_symbols"
    )
    private String collectionName;

    @Option(
        names = {"--verbose", "-v"},
        description = "Show detailed output including callers/callees"
    )
    private boolean verbose;

    @Override
    public Integer call() throws Exception {
        String query = String.join(" ", queryParts);
        
        // Initialize services
        OllamaConfig ollamaConfig = OllamaConfig.builder()
            .host(ollamaHost)
            .port(ollamaPort)
            .build();
        
        QdrantConfig qdrantConfig = QdrantConfig.builder()
            .host(qdrantHost)
            .grpcPort(qdrantPort)
            .collectionName(collectionName)
            .build();

        OllamaEmbeddingService embeddingService = new OllamaEmbeddingService(ollamaConfig);
        QdrantVectorStore vectorStore = new QdrantVectorStore(qdrantConfig);
        
        // Connect to Qdrant
        vectorStore.connect();
        
        // Connect to Neo4j for enrichment (optional)
        Neo4jGraphStore graphStore = null;
        try {
            graphStore = new Neo4jGraphStore(neo4jUri, neo4jUser, neo4jPassword);
            graphStore.connect();
        } catch (Exception e) {
            System.err.println("Warning: Could not connect to Neo4j for enrichment: " + e.getMessage());
        }

        // Create search service
        SemanticSearchService searchService = new SemanticSearchService(
            embeddingService, vectorStore, graphStore, collectionName);

        // Build filters
        SemanticSearchService.SearchFilters filters = null;
        if (repository != null || language != null || kind != null) {
            filters = new SemanticSearchService.SearchFilters();
            if (repository != null) filters = SemanticSearchService.SearchFilters.forRepository(repository);
            if (language != null) filters = filters != null ? filters.language(language) : new SemanticSearchService.SearchFilters();
            if (kind != null) filters = filters != null ? filters.kind(kind) : new SemanticSearchService.SearchFilters();
        }

        // Perform search
        System.out.println("Searching: \"" + query + "\"");
        System.out.println();

        List<SemanticSearchResult> results = searchService.search(query, limit, filters);

        if (results.isEmpty()) {
            System.out.println("No results found.");
            System.out.println("Make sure you have embedded symbols using: embed <repository>");
            return 0;
        }

        // Print results
        for (int i = 0; i < results.size(); i++) {
            SemanticSearchResult result = results.get(i);
            System.out.printf("%d. [%d%%] %s %s%n", 
                i + 1, result.getScorePercent(), result.getKind(), result.getName());
            System.out.printf("   File: %s%n", result.getLocation());
            
            if (result.getSignature() != null && !result.getSignature().isBlank()) {
                String sig = result.getSignature();
                if (sig.length() > 80) sig = sig.substring(0, 77) + "...";
                System.out.printf("   Signature: %s%n", sig);
            }
            
            if (verbose) {
                if (result.getDocumentation() != null && !result.getDocumentation().isBlank()) {
                    String doc = result.getDocumentation();
                    if (doc.length() > 100) doc = doc.substring(0, 97) + "...";
                    System.out.printf("   Doc: %s%n", doc);
                }
                if (result.getCallers() != null && !result.getCallers().isEmpty()) {
                    System.out.printf("   Callers: %s%n", result.getCallers());
                }
                if (result.getCallees() != null && !result.getCallees().isEmpty()) {
                    System.out.printf("   Callees: %s%n", result.getCallees());
                }
            }
            System.out.println();
        }

        // Cleanup
        vectorStore.close();
        if (graphStore != null) graphStore.close();

        return 0;
    }
}
