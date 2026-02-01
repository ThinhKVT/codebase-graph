package org.example.cli;

import org.example.config.OllamaConfig;
import org.example.config.QdrantConfig;
import org.example.embedding.CodeEmbedder;
import org.example.embedding.OllamaEmbeddingService;
import org.example.graph.Neo4jGraphStore;
import org.example.model.Symbol;
import org.example.vector.QdrantVectorStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * CLI command to embed code symbols into the vector store.
 */
@Command(
    name = "embed",
    description = "Embed code symbols from Neo4j into Qdrant for semantic search",
    mixinStandardHelpOptions = true
)
public class EmbedCommand implements Callable<Integer> {

    @Parameters(
        index = "0",
        description = "Repository name to embed"
    )
    private String repository;

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
        names = {"--reindex"},
        description = "Delete and re-embed all symbols for this repository"
    )
    private boolean reindex;

    @Override
    public Integer call() throws Exception {
        System.out.println("Embedding symbols for repository: " + repository);

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
        
        // Check Ollama availability
        System.out.println("Checking Ollama service...");
        if (!embeddingService.isAvailable()) {
            System.err.println("ERROR: Ollama embedding model not available.");
            System.err.println("Please run: docker exec codebase-graph-ollama ollama pull nomic-embed-text");
            return 1;
        }
        System.out.println("  Model: " + embeddingService.getModelName() + " (dim=" + embeddingService.getDimension() + ")");

        // Connect to Neo4j
        System.out.println("Connecting to Neo4j...");
        Neo4jGraphStore graphStore = new Neo4jGraphStore(neo4jUri, neo4jUser, neo4jPassword);
        graphStore.connect();
        
        // Load symbols from Neo4j
        System.out.println("Loading symbols from Neo4j...");
        List<Symbol> allSymbols = graphStore.findSymbolsByNamePattern(".*");
        System.out.println("  Total symbols in graph: " + allSymbols.size());

        // Connect to Qdrant
        System.out.println("Connecting to Qdrant...");
        vectorStore.connect();

        // Create embedder
        CodeEmbedder embedder = new CodeEmbedder(embeddingService, vectorStore, collectionName);
        embedder.initialize();

        // Delete existing if reindex
        if (reindex) {
            System.out.println("Deleting existing embeddings for: " + repository);
            embedder.deleteRepository(repository);
        }

        // Embed symbols
        System.out.println("Embedding symbols...");
        int embedded = embedder.embedSymbols(allSymbols, repository);
        
        // Report
        long totalCount = embedder.getEmbeddedCount();
        System.out.println("\nEmbedding complete:");
        System.out.println("  Newly embedded: " + embedded);
        System.out.println("  Total in collection: " + totalCount);

        // Cleanup
        graphStore.close();
        vectorStore.close();

        return 0;
    }
}
