package org.example.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.config.OllamaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Embedding service implementation using Ollama local LLM.
 * 
 * <p>Uses the nomic-embed-text model by default (768 dimensions).</p>
 * 
 * <p>Setup: {@code docker exec codebase-graph-ollama ollama pull nomic-embed-text}</p>
 */
public class OllamaEmbeddingService implements EmbeddingService {

    private static final Logger logger = LoggerFactory.getLogger(OllamaEmbeddingService.class);
    private static final int NOMIC_EMBED_DIMENSION = 768;

    private final OllamaConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OllamaEmbeddingService(OllamaConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.objectMapper = new ObjectMapper();
        logger.info("Initialized Ollama embedding service: {} (model: {})", 
            config.getBaseUrl(), config.getModel());
    }

    public OllamaEmbeddingService() {
        this(OllamaConfig.fromEnv());
    }

    @Override
    public float[] embed(String text) throws EmbeddingException {
        if (text == null || text.isBlank()) {
            throw new EmbeddingException("Cannot embed null or empty text");
        }

        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                "model", config.getModel(),
                "prompt", text
            ));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getEmbeddingUrl()))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                String error = parseError(response.body());
                if (error.contains("model") && error.contains("not found")) {
                    throw EmbeddingException.modelNotFound(config.getModel());
                }
                throw EmbeddingException.requestFailed("HTTP " + response.statusCode() + ": " + error);
            }

            return parseEmbedding(response.body());

        } catch (IOException e) {
            throw new EmbeddingException("Failed to connect to Ollama: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmbeddingException("Embedding request interrupted", e);
        }
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) throws EmbeddingException {
        List<float[]> embeddings = new ArrayList<>();
        
        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            try {
                float[] embedding = embed(text);
                embeddings.add(embedding);
                
                if ((i + 1) % 10 == 0) {
                    logger.debug("Embedded {}/{} texts", i + 1, texts.size());
                }
            } catch (EmbeddingException e) {
                logger.warn("Failed to embed text at index {}: {}", i, e.getMessage());
                // Add zero vector as placeholder for failed embeddings
                embeddings.add(new float[getDimension()]);
            }
        }
        
        return embeddings;
    }

    @Override
    public int getDimension() {
        return NOMIC_EMBED_DIMENSION;
    }

    @Override
    public String getModelName() {
        return config.getModel();
    }

    @Override
    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getBaseUrl() + "/api/tags"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // Check if model is available
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode models = root.get("models");
                if (models != null && models.isArray()) {
                    for (JsonNode model : models) {
                        String name = model.get("name").asText();
                        if (name.startsWith(config.getModel())) {
                            return true;
                        }
                    }
                }
                logger.warn("Ollama is running but model {} is not installed", config.getModel());
                return false;
            }
            return false;
        } catch (Exception e) {
            logger.debug("Ollama health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Pull the embedding model if not already available.
     *
     * @return true if model is now available
     */
    public boolean ensureModelAvailable() {
        if (isAvailable()) {
            return true;
        }

        logger.info("Pulling Ollama model: {}", config.getModel());
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                "name", config.getModel()
            ));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getBaseUrl() + "/api/pull"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(10)) // Model download can take time
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                logger.info("Successfully pulled model: {}", config.getModel());
                return true;
            } else {
                logger.error("Failed to pull model: {}", response.body());
                return false;
            }
        } catch (Exception e) {
            logger.error("Failed to pull model: {}", e.getMessage());
            return false;
        }
    }

    private float[] parseEmbedding(String responseBody) throws EmbeddingException {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode embeddingNode = root.get("embedding");
            
            if (embeddingNode == null || !embeddingNode.isArray()) {
                throw new EmbeddingException("Invalid response: no embedding array found");
            }

            float[] embedding = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                embedding[i] = (float) embeddingNode.get(i).asDouble();
            }
            
            return embedding;
        } catch (IOException e) {
            throw new EmbeddingException("Failed to parse embedding response", e);
        }
    }

    private String parseError(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode errorNode = root.get("error");
            if (errorNode != null) {
                return errorNode.asText();
            }
            return responseBody;
        } catch (Exception e) {
            return responseBody;
        }
    }
}
