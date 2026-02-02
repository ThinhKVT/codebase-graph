package org.example.embedding;

/**
 * Exception thrown when embedding generation fails.
 */
public class EmbeddingException extends RuntimeException {

    public EmbeddingException(String message) {
        super(message);
    }

    public EmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }

    public static EmbeddingException serviceUnavailable(String service) {
        return new EmbeddingException("Embedding service unavailable: " + service);
    }

    public static EmbeddingException modelNotFound(String model) {
        return new EmbeddingException("Embedding model not found: " + model + 
            ". Please run: ollama pull " + model);
    }

    public static EmbeddingException requestFailed(String reason) {
        return new EmbeddingException("Embedding request failed: " + reason);
    }

    public static EmbeddingException timeout(int seconds) {
        return new EmbeddingException("Embedding request timed out after " + seconds + " seconds");
    }
}
