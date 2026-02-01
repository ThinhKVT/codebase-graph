package org.example.embedding;

import org.example.model.Symbol;
import org.example.vector.VectorPoint;
import org.example.vector.VectorStore;
import org.example.vector.VectorStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles embedding code symbols into the vector store.
 * 
 * <p>Implements chunking strategy:</p>
 * <ul>
 *   <li>MVP1: Method-level and Class-level chunks</li>
 *   <li>Future: Statement-level, File-level</li>
 * </ul>
 */
public class CodeEmbedder {

    private static final Logger logger = LoggerFactory.getLogger(CodeEmbedder.class);
    private static final int BATCH_SIZE = 50;

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final String collectionName;

    public CodeEmbedder(EmbeddingService embeddingService, VectorStore vectorStore, 
            String collectionName) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.collectionName = collectionName;
    }

    /**
     * Initialize the vector store collection if needed.
     */
    public void initialize() throws VectorStoreException {
        if (!vectorStore.isConnected()) {
            vectorStore.connect();
        }
        vectorStore.ensureCollection(collectionName, embeddingService.getDimension());
        logger.info("Initialized collection: {} (dimension: {})", 
            collectionName, embeddingService.getDimension());
    }

    /**
     * Embed a list of symbols into the vector store.
     *
     * @param symbols List of code symbols to embed
     * @param repository Repository name for filtering
     * @return Number of symbols successfully embedded
     */
    public int embedSymbols(List<Symbol> symbols, String repository) {
        if (symbols.isEmpty()) {
            return 0;
        }

        logger.info("Embedding {} symbols from repository: {}", symbols.size(), repository);
        
        // Filter symbols suitable for embedding (methods, classes, functions)
        List<Symbol> embeddableSymbols = symbols.stream()
            .filter(this::isEmbeddable)
            .collect(Collectors.toList());

        logger.info("Found {} embeddable symbols (methods, classes, functions)", 
            embeddableSymbols.size());

        int embedded = 0;
        List<VectorPoint> batch = new ArrayList<>();

        for (Symbol symbol : embeddableSymbols) {
            try {
                String textToEmbed = buildEmbeddingText(symbol);
                float[] vector = embeddingService.embed(textToEmbed);
                
                VectorPoint point = buildVectorPoint(symbol, vector, repository);
                batch.add(point);

                if (batch.size() >= BATCH_SIZE) {
                    vectorStore.upsertBatch(collectionName, batch);
                    embedded += batch.size();
                    logger.debug("Embedded batch of {} symbols ({}/{})", 
                        batch.size(), embedded, embeddableSymbols.size());
                    batch.clear();
                }
            } catch (Exception e) {
                logger.warn("Failed to embed symbol {}: {}", symbol.id(), e.getMessage());
            }
        }

        // Flush remaining
        if (!batch.isEmpty()) {
            try {
                vectorStore.upsertBatch(collectionName, batch);
                embedded += batch.size();
            } catch (VectorStoreException e) {
                logger.error("Failed to flush final batch: {}", e.getMessage());
            }
        }

        logger.info("Successfully embedded {} symbols", embedded);
        return embedded;
    }

    /**
     * Delete all embeddings for a repository.
     */
    public void deleteRepository(String repository) throws VectorStoreException {
        logger.info("Deleting embeddings for repository: {}", repository);
        vectorStore.deleteByFilter(collectionName, Map.of("repository", repository));
    }

    /**
     * Get count of embedded symbols.
     */
    public long getEmbeddedCount() throws VectorStoreException {
        return vectorStore.count(collectionName);
    }

    /**
     * Check if a symbol should be embedded.
     * MVP1: Only methods and classes.
     */
    private boolean isEmbeddable(Symbol symbol) {
        if (symbol == null || symbol.kind() == null) {
            return false;
        }
        
        String kind = symbol.kind().name();
        return kind.equals("METHOD") || 
               kind.equals("FUNCTION") || 
               kind.equals("CLASS") || 
               kind.equals("INTERFACE") ||
               kind.equals("CONSTRUCTOR");
    }

    /**
     * Build the text representation for embedding.
     * 
     * Format: [kind] name: signature\ndocumentation\ncode_snippet
     */
    private String buildEmbeddingText(Symbol symbol) {
        StringBuilder sb = new StringBuilder();
        
        // Type prefix
        sb.append("[").append(symbol.kind().name()).append("] ");
        
        // Name
        sb.append(symbol.displayName() != null ? symbol.displayName() : symbol.id());
        
        // Signature if available
        if (symbol.signature() != null && !symbol.signature().isBlank()) {
            sb.append("\n").append(symbol.signature());
        }
        
        // Documentation if available
        if (symbol.documentation() != null && !symbol.documentation().isBlank()) {
            sb.append("\n").append(symbol.documentation());
        }
        
        // File context
        if (symbol.filePath() != null) {
            sb.append("\nFile: ").append(symbol.filePath());
        }
        
        return sb.toString();
    }

    /**
     * Build a VectorPoint from a symbol.
     */
    private VectorPoint buildVectorPoint(Symbol symbol, float[] vector, String repository) {
        return VectorPoint.builder()
            .id(generatePointId(symbol, repository))
            .vector(vector)
            .symbolId(symbol.id())
            .repository(repository)
            .filePath(symbol.filePath())
            .symbolName(symbol.displayName() != null ? symbol.displayName() : extractName(symbol.id()))
            .symbolKind(symbol.kind().name())
            .signature(symbol.signature())
            .documentation(symbol.documentation())
            .startLine(symbol.startLine())
            .endLine(symbol.endLine())
            .language(detectLanguage(symbol.filePath()))
            .build();
    }

    /**
     * Generate a unique point ID.
     */
    private String generatePointId(Symbol symbol, String repository) {
        // Use hash to create deterministic but shorter ID
        String combined = repository + ":" + symbol.id();
        return UUID.nameUUIDFromBytes(combined.getBytes()).toString();
    }

    /**
     * Extract simple name from qualified ID.
     */
    private String extractName(String symbolId) {
        if (symbolId == null) return "unknown";
        int lastDot = symbolId.lastIndexOf('.');
        int lastSlash = symbolId.lastIndexOf('/');
        int lastHash = symbolId.lastIndexOf('#');
        int lastSeparator = Math.max(Math.max(lastDot, lastSlash), lastHash);
        return lastSeparator >= 0 ? symbolId.substring(lastSeparator + 1) : symbolId;
    }

    /**
     * Detect language from file path.
     */
    private String detectLanguage(String filePath) {
        if (filePath == null) return "unknown";
        if (filePath.endsWith(".java")) return "java";
        if (filePath.endsWith(".py")) return "python";
        if (filePath.endsWith(".go")) return "go";
        if (filePath.endsWith(".ts") || filePath.endsWith(".tsx")) return "typescript";
        if (filePath.endsWith(".js") || filePath.endsWith(".jsx")) return "javascript";
        return "unknown";
    }
}
