package org.example.config;

/**
 * Configuration for Ollama embedding service.
 */
public class OllamaConfig {
    
    private final String host;
    private final int port;
    private final String model;
    private final int timeoutSeconds;

    public static final String DEFAULT_MODEL = "nomic-embed-text";
    public static final int DEFAULT_TIMEOUT = 60;

    private OllamaConfig(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.model = builder.model;
        this.timeoutSeconds = builder.timeoutSeconds;
    }

    public static OllamaConfig fromEnv() {
        return builder()
            .host(getEnv("OLLAMA_HOST", "localhost"))
            .port(Integer.parseInt(getEnv("OLLAMA_PORT", "11434")))
            .model(getEnv("OLLAMA_MODEL", DEFAULT_MODEL))
            .timeoutSeconds(Integer.parseInt(getEnv("OLLAMA_TIMEOUT", String.valueOf(DEFAULT_TIMEOUT))))
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
    public int getPort() { return port; }
    public String getModel() { return model; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    
    public String getBaseUrl() {
        return "http://" + host + ":" + port;
    }
    
    public String getEmbeddingUrl() {
        return getBaseUrl() + "/api/embeddings";
    }

    public static class Builder {
        private String host = "localhost";
        private int port = 11434;
        private String model = DEFAULT_MODEL;
        private int timeoutSeconds = DEFAULT_TIMEOUT;

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder timeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        public OllamaConfig build() {
            return new OllamaConfig(this);
        }
    }

    @Override
    public String toString() {
        return "OllamaConfig{" +
            "host='" + host + '\'' +
            ", port=" + port +
            ", model='" + model + '\'' +
            ", timeoutSeconds=" + timeoutSeconds +
            '}';
    }
}
