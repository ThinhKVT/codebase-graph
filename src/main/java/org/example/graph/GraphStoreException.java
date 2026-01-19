package org.example.graph;

/**
 * Exception thrown when graph store operations fail.
 */
public class GraphStoreException extends RuntimeException {

    public GraphStoreException(String message) {
        super(message);
    }

    public GraphStoreException(String message, Throwable cause) {
        super(message, cause);
    }

    public static GraphStoreException connectionFailed(String uri, Throwable cause) {
        return new GraphStoreException(
            "Failed to connect to Neo4j at " + uri + ". Is the server running? Try: docker-compose up -d", cause);
    }

    public static GraphStoreException queryFailed(String query, Throwable cause) {
        return new GraphStoreException("Query failed: " + query, cause);
    }

    public static GraphStoreException notConnected() {
        return new GraphStoreException("Not connected to Neo4j. Call connect() first or check if server is running.");
    }
}

