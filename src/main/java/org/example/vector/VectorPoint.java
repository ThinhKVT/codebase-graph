package org.example.vector;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a point in the vector store with ID, vector, and payload.
 */
public class VectorPoint {

    private final String id;
    private final float[] vector;
    private final Map<String, Object> payload;

    public VectorPoint(String id, float[] vector, Map<String, Object> payload) {
        this.id = id;
        this.vector = vector;
        this.payload = payload != null ? payload : new HashMap<>();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getId() {
        return id;
    }

    public float[] getVector() {
        return vector;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public Object getPayloadValue(String key) {
        return payload.get(key);
    }

    public String getPayloadString(String key) {
        Object value = payload.get(key);
        return value != null ? value.toString() : null;
    }

    public Integer getPayloadInt(String key) {
        Object value = payload.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    public Boolean getPayloadBoolean(String key) {
        Object value = payload.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return null;
    }

    public static class Builder {
        private String id;
        private float[] vector;
        private Map<String, Object> payload = new HashMap<>();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder vector(float[] vector) {
            this.vector = vector;
            return this;
        }

        public Builder payload(Map<String, Object> payload) {
            this.payload = payload;
            return this;
        }

        public Builder addPayload(String key, Object value) {
            this.payload.put(key, value);
            return this;
        }

        // Convenience methods for code symbol payload
        public Builder symbolId(String symbolId) {
            return addPayload("symbol_id", symbolId);
        }

        public Builder repository(String repository) {
            return addPayload("repository", repository);
        }

        public Builder filePath(String filePath) {
            return addPayload("file_path", filePath);
        }

        public Builder symbolName(String name) {
            return addPayload("name", name);
        }

        public Builder symbolKind(String kind) {
            return addPayload("kind", kind);
        }

        public Builder codeContent(String content) {
            return addPayload("code_content", content);
        }

        public Builder signature(String signature) {
            return addPayload("signature", signature);
        }

        public Builder documentation(String doc) {
            return addPayload("documentation", doc);
        }

        public Builder startLine(int line) {
            return addPayload("start_line", line);
        }

        public Builder endLine(int line) {
            return addPayload("end_line", line);
        }

        public Builder parentName(String parentName) {
            return addPayload("parent_name", parentName);
        }

        public Builder language(String language) {
            return addPayload("language", language);
        }

        public VectorPoint build() {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Point ID is required");
            }
            if (vector == null || vector.length == 0) {
                throw new IllegalArgumentException("Vector is required");
            }
            return new VectorPoint(id, vector, payload);
        }
    }

    @Override
    public String toString() {
        return "VectorPoint{" +
            "id='" + id + '\'' +
            ", vectorDim=" + (vector != null ? vector.length : 0) +
            ", payloadKeys=" + payload.keySet() +
            '}';
    }
}
