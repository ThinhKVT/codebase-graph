package org.example.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents an indexed repository.
 */
public record Repository(
    String id,
    String name,
    String path,
    String language,
    Instant lastIndexedAt,
    int fileCount,
    int symbolCount
) {
    public Repository {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(path, "path cannot be null");
    }

    public static Repository of(String id, String name, String path) {
        return new Repository(id, name, path, null, null, 0, 0);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String name;
        private String path;
        private String language;
        private Instant lastIndexedAt;
        private int fileCount;
        private int symbolCount;

        public Builder id(String id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder path(String path) { this.path = path; return this; }
        public Builder language(String language) { this.language = language; return this; }
        public Builder lastIndexedAt(Instant lastIndexedAt) { this.lastIndexedAt = lastIndexedAt; return this; }
        public Builder fileCount(int fileCount) { this.fileCount = fileCount; return this; }
        public Builder symbolCount(int symbolCount) { this.symbolCount = symbolCount; return this; }

        public Repository build() {
            return new Repository(id, name, path, language, lastIndexedAt, fileCount, symbolCount);
        }
    }
}

