package org.example.cli;

import org.example.api.ApiServer;
import org.example.config.OllamaConfig;
import org.example.config.QdrantConfig;
import org.example.embedding.EmbeddingService;
import org.example.embedding.OllamaEmbeddingService;
import org.example.graph.GraphStore;
import org.example.graph.Neo4jGraphStore;
import org.example.vector.QdrantVectorStore;
import org.example.vector.VectorStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * CLI command to start the REST API server.
 */
@Command(name = "serve", description = "Start the REST API server")
public class ServeCommand implements Callable<Integer> {

    @Option(names = {"-p", "--port"}, description = "Port to run the server on (default: ${DEFAULT-VALUE})", defaultValue = "8080")
    private int port;

    @Option(names = {"--neo4j-uri"}, description = "Neo4j connection URI", defaultValue = "bolt://localhost:7687")
    private String neo4jUri;

    @Option(names = {"--neo4j-user"}, description = "Neo4j username", defaultValue = "neo4j")
    private String neo4jUser;

    @Option(names = {"--neo4j-password"}, description = "Neo4j password", defaultValue = "password")
    private String neo4jPassword;

    @Option(names = {"--enable-semantic"}, description = "Enable semantic search endpoints (requires Qdrant and Ollama)")
    private boolean enableSemantic;

    @Option(names = {"--qdrant-host"}, description = "Qdrant host", defaultValue = "localhost")
    private String qdrantHost;

    @Option(names = {"--qdrant-port"}, description = "Qdrant HTTP port", defaultValue = "6333")
    private int qdrantPort;

    @Option(names = {"--ollama-host"}, description = "Ollama host", defaultValue = "localhost")
    private String ollamaHost;

    @Option(names = {"--ollama-port"}, description = "Ollama port", defaultValue = "11434")
    private int ollamaPort;

    @Option(names = {"--collection"}, description = "Qdrant collection name", defaultValue = "code_symbols")
    private String collectionName;

    @Override
    public Integer call() {
        System.out.println("Starting Codebase Knowledge Graph API server...");

        // Initialize graph store
        GraphStore graphStore = new Neo4jGraphStore(neo4jUri, neo4jUser, neo4jPassword);
        EmbeddingService embeddingService = null;
        VectorStore vectorStore = null;

        try {
            graphStore.connect();
            System.out.println("Connected to Neo4j at " + neo4jUri);

            // Create API server
            ApiServer server = new ApiServer(graphStore, port);

            // Enable semantic search if requested
            if (enableSemantic) {
                System.out.println("Enabling semantic search endpoints...");
                
                // Initialize Ollama
                OllamaConfig ollamaConfig = OllamaConfig.builder()
                    .host(ollamaHost)
                    .port(ollamaPort)
                    .build();
                embeddingService = new OllamaEmbeddingService(ollamaConfig);
                
                if (!embeddingService.isAvailable()) {
                    System.err.println("Warning: Ollama is not available at " + ollamaHost + ":" + ollamaPort);
                    System.err.println("Semantic search will be disabled.");
                } else {
                    System.out.println("Connected to Ollama at " + ollamaHost + ":" + ollamaPort);
                    
                    // Initialize Qdrant
                    QdrantConfig qdrantConfig = QdrantConfig.builder()
                        .host(qdrantHost)
                        .httpPort(qdrantPort)
                        .collectionName(collectionName)
                        .build();
                    vectorStore = new QdrantVectorStore(qdrantConfig);
                    vectorStore.connect();
                    System.out.println("Connected to Qdrant at " + qdrantHost + ":" + qdrantPort);
                    
                    // Enable semantic search on API server
                    server.withSemanticSearch(embeddingService, vectorStore, collectionName);
                    System.out.println("Semantic search enabled on collection: " + collectionName);
                }
            }

            server.start();

            System.out.println();
            System.out.println("API server is running on http://localhost:" + port);
            System.out.println("Press Ctrl+C to stop the server");
            System.out.println();
            System.out.println("Available endpoints:");
            System.out.println("  GET /health                      - Health check");
            System.out.println("  GET /stats                       - Graph statistics");
            System.out.println("  GET /symbols                     - List/search symbols");
            System.out.println("  GET /symbols?name=<name>         - Search by name");
            System.out.println("  GET /symbols?kind=<kind>         - Filter by kind");
            System.out.println("  GET /symbols?fqn=<fqn>           - Get by fully qualified name");
            System.out.println("  GET /symbols/{id}                - Get symbol by ID");
            System.out.println("  GET /symbols/{id}/references     - Get references to symbol");
            System.out.println("  GET /symbols/{id}/dependencies   - Get symbol dependencies");
            System.out.println("  GET /symbols/{id}/dependents     - Get symbols depending on this");
            
            if (enableSemantic && vectorStore != null) {
                System.out.println();
                System.out.println("Semantic Search endpoints:");
                System.out.println("  GET  /search/semantic?q=<query>  - Semantic code search");
                System.out.println("  POST /search/semantic            - Semantic search with body");
                System.out.println("  GET  /search/agent?q=<query>     - Agent-powered search");
                System.out.println("  POST /search/agent               - Agent search with body");
            }

            // Capture resources for shutdown hook
            final EmbeddingService finalEmbeddingService = embeddingService;
            final VectorStore finalVectorStore = vectorStore;

            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nShutting down...");
                server.stop();
                if (finalVectorStore != null) {
                    finalVectorStore.close();
                }
                graphStore.close();
                System.out.println("Server stopped.");
            }));

            // Keep the main thread alive
            Thread.currentThread().join();

            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            if (vectorStore != null) {
                vectorStore.close();
            }
            graphStore.close();
            return 1;
        }
    }
}

