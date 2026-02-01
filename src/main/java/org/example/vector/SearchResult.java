package org.example.vector;

import java.util.Map;

/**
 * Represents a search result from the vector store.
 */
public class SearchResult {

    private final String id;
    private final float score;
    private final Map<String, Object> payload;

    public SearchResult(String id, float score, Map<String, Object> payload) {
        this.id = id;
        this.score = score;
        this.payload = payload;
    }

    public String getId() {
        return id;
    }

    public float getScore() {
        return score;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    // Convenience accessors for code symbol payloads
    public String getSymbolId() {
        return getPayloadString("symbol_id");
    }

    public String getRepository() {
        return getPayloadString("repository");
    }

    public String getFilePath() {
        return getPayloadString("file_path");
    }

    public String getName() {
        return getPayloadString("name");
    }

    public String getKind() {
        return getPayloadString("kind");
    }

    public String getCodeContent() {
        return getPayloadString("code_content");
    }

    public String getSignature() {
        return getPayloadString("signature");
    }

    public String getDocumentation() {
        return getPayloadString("documentation");
    }

    public Integer getStartLine() {
        return getPayloadInt("start_line");
    }

    public Integer getEndLine() {
        return getPayloadInt("end_line");
    }

    public String getParentName() {
        return getPayloadString("parent_name");
    }

    public String getLanguage() {
        return getPayloadString("language");
    }

    private String getPayloadString(String key) {
        Object value = payload.get(key);
        return value != null ? value.toString() : null;
    }

    private Integer getPayloadInt(String key) {
        Object value = payload.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    @Override
    public String toString() {
        return "SearchResult{" +
            "id='" + id + '\'' +
            ", score=" + score +
            ", name='" + getName() + '\'' +
            ", kind='" + getKind() + '\'' +
            ", file='" + getFilePath() + '\'' +
            '}';
    }
}
