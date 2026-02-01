package org.example.vector;

/**
 * Exception thrown when vector store operations fail.
 */
public class VectorStoreException extends RuntimeException {

    public VectorStoreException(String message) {
        super(message);
    }

    public VectorStoreException(String message, Throwable cause) {
        super(message, cause);
    }

    public static VectorStoreException connectionFailed(String host, Throwable cause) {
        return new VectorStoreException("Failed to connect to vector store at " + host, cause);
    }

    public static VectorStoreException notConnected() {
        return new VectorStoreException("Not connected to vector store");
    }

    public static VectorStoreException collectionNotFound(String collection) {
        return new VectorStoreException("Collection not found: " + collection);
    }

    public static VectorStoreException operationFailed(String operation, String reason) {
        return new VectorStoreException("Vector store operation failed [" + operation + "]: " + reason);
    }
}
