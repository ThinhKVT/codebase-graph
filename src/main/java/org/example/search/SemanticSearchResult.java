package org.example.search;

import java.util.List;

/**
 * Result from semantic search, enriched with graph context.
 */
public class SemanticSearchResult {

    private final String id;
    private final float score;
    private final String symbolId;
    private final String repository;
    private final String filePath;
    private final String name;
    private final String kind;
    private final String signature;
    private final String documentation;
    private final Integer startLine;
    private final Integer endLine;
    private final String language;
    
    // Graph context enrichment
    private final List<String> callers;
    private final List<String> callees;

    private SemanticSearchResult(Builder builder) {
        this.id = builder.id;
        this.score = builder.score;
        this.symbolId = builder.symbolId;
        this.repository = builder.repository;
        this.filePath = builder.filePath;
        this.name = builder.name;
        this.kind = builder.kind;
        this.signature = builder.signature;
        this.documentation = builder.documentation;
        this.startLine = builder.startLine;
        this.endLine = builder.endLine;
        this.language = builder.language;
        this.callers = builder.callers;
        this.callees = builder.callees;
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public String getId() { return id; }
    public float getScore() { return score; }
    public String getSymbolId() { return symbolId; }
    public String getRepository() { return repository; }
    public String getFilePath() { return filePath; }
    public String getName() { return name; }
    public String getKind() { return kind; }
    public String getSignature() { return signature; }
    public String getDocumentation() { return documentation; }
    public Integer getStartLine() { return startLine; }
    public Integer getEndLine() { return endLine; }
    public String getLanguage() { return language; }
    public List<String> getCallers() { return callers; }
    public List<String> getCallees() { return callees; }

    /**
     * Get a formatted location string for display.
     */
    public String getLocation() {
        if (filePath == null) return null;
        if (startLine != null) {
            return filePath + ":" + startLine;
        }
        return filePath;
    }

    /**
     * Get similarity score as percentage.
     */
    public int getScorePercent() {
        return Math.round(score * 100);
    }

    @Override
    public String toString() {
        return String.format("[%.0f%%] %s %s (%s)",
            score * 100, kind, name, getLocation());
    }

    public static class Builder {
        private String id;
        private float score;
        private String symbolId;
        private String repository;
        private String filePath;
        private String name;
        private String kind;
        private String signature;
        private String documentation;
        private Integer startLine;
        private Integer endLine;
        private String language;
        private List<String> callers;
        private List<String> callees;

        public Builder id(String id) { this.id = id; return this; }
        public Builder score(float score) { this.score = score; return this; }
        public Builder symbolId(String symbolId) { this.symbolId = symbolId; return this; }
        public Builder repository(String repository) { this.repository = repository; return this; }
        public Builder filePath(String filePath) { this.filePath = filePath; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder kind(String kind) { this.kind = kind; return this; }
        public Builder signature(String signature) { this.signature = signature; return this; }
        public Builder documentation(String documentation) { this.documentation = documentation; return this; }
        public Builder startLine(Integer startLine) { this.startLine = startLine; return this; }
        public Builder endLine(Integer endLine) { this.endLine = endLine; return this; }
        public Builder language(String language) { this.language = language; return this; }
        public Builder callers(List<String> callers) { this.callers = callers; return this; }
        public Builder callees(List<String> callees) { this.callees = callees; return this; }

        public SemanticSearchResult build() {
            return new SemanticSearchResult(this);
        }
    }
}
