package org.example.config;

/**
 * Configuration for Qdrant vector database connection.
 */
public class QdrantConfig {
    
    private final String host;
    private final int grpcPort;
    private final int httpPort;
    private final String collectionName;
    private final int vectorSize;

    public static final String DEFAULT_COLLECTION = "code_symbols";
    public static final int DEFAULT_VECTOR_SIZE = 768; // nomic-embed-text dimension

    private QdrantConfig(Builder builder) {
        this.host = builder.host;
        this.grpcPort = builder.grpcPort;
        this.httpPort = builder.httpPort;
        this.collectionName = builder.collectionName;
        this.vectorSize = builder.vectorSize;
    }

    public static QdrantConfig fromEnv() {
        return builder()
            .host(getEnv("QDRANT_HOST", "localhost"))
            .grpcPort(Integer.parseInt(getEnv("QDRANT_PORT", "6334")))
            .httpPort(Integer.parseInt(getEnv("QDRANT_HTTP_PORT", "6333")))
            .collectionName(getEnv("QDRANT_COLLECTION", DEFAULT_COLLECTION))
            .vectorSize(Integer.parseInt(getEnv("QDRANT_VECTOR_SIZE", String.valueOf(DEFAULT_VECTOR_SIZE))))
            .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null ? value : defaultValue;
    }

    // Getters
    public String getHost() { return host; }
    public int getGrpcPort() { return grpcPort; }
    public int getHttpPort() { return httpPort; }
    public String getCollectionName() { return collectionName; }
    public int getVectorSize() { return vectorSize; }
    
    public String getGrpcAddress() {
        return host + ":" + grpcPort;
    }
    
    public String getHttpUrl() {
        return "http://" + host + ":" + httpPort;
    }

    public static class Builder {
        private String host = "localhost";
        private int grpcPort = 6334;
        private int httpPort = 6333;
        private String collectionName = DEFAULT_COLLECTION;
        private int vectorSize = DEFAULT_VECTOR_SIZE;

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder grpcPort(int grpcPort) {
            this.grpcPort = grpcPort;
            return this;
        }

        public Builder httpPort(int httpPort) {
            this.httpPort = httpPort;
            return this;
        }

        public Builder collectionName(String collectionName) {
            this.collectionName = collectionName;
            return this;
        }

        public Builder vectorSize(int vectorSize) {
            this.vectorSize = vectorSize;
            return this;
        }

        public QdrantConfig build() {
            return new QdrantConfig(this);
        }
    }

    @Override
    public String toString() {
        return "QdrantConfig{" +
            "host='" + host + '\'' +
            ", grpcPort=" + grpcPort +
            ", httpPort=" + httpPort +
            ", collectionName='" + collectionName + '\'' +
            ", vectorSize=" + vectorSize +
            '}';
    }
}
