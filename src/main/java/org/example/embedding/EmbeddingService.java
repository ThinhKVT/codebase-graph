package org.example.embedding;

import java.util.List;

/**
 * Service interface for generating text embeddings.
 */
public interface EmbeddingService {

    /**
     * Generate embedding vector for a single text.
     *
     * @param text The text to embed
     * @return Embedding vector as float array
     * @throws EmbeddingException if embedding generation fails
     */
    float[] embed(String text) throws EmbeddingException;

    /**
     * Generate embeddings for multiple texts in batch.
     *
     * @param texts List of texts to embed
     * @return List of embedding vectors
     * @throws EmbeddingException if embedding generation fails
     */
    List<float[]> embedBatch(List<String> texts) throws EmbeddingException;

    /**
     * Get the dimension of the embedding vectors.
     *
     * @return Vector dimension
     */
    int getDimension();

    /**
     * Get the name/identifier of the embedding model.
     *
     * @return Model name
     */
    String getModelName();

    /**
     * Check if the embedding service is available.
     *
     * @return true if service is healthy and ready
     */
    boolean isAvailable();
}
