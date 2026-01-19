package org.example.model;

import java.util.Objects;

/**
 * Represents a reference from one symbol to another.
 */
public record Reference(
    String fromSymbolId,
    String toSymbolId,
    ReferenceKind kind,
    String filePath,
    int line,
    int column
) {
    public Reference {
        Objects.requireNonNull(fromSymbolId, "fromSymbolId cannot be null");
        Objects.requireNonNull(toSymbolId, "toSymbolId cannot be null");
        Objects.requireNonNull(kind, "kind cannot be null");
    }

    public static Reference of(String fromSymbolId, String toSymbolId, ReferenceKind kind) {
        return new Reference(fromSymbolId, toSymbolId, kind, null, 0, 0);
    }

    public static Reference of(String fromSymbolId, String toSymbolId, ReferenceKind kind, String filePath, int line, int column) {
        return new Reference(fromSymbolId, toSymbolId, kind, filePath, line, column);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String fromSymbolId;
        private String toSymbolId;
        private ReferenceKind kind;
        private String filePath;
        private int line;
        private int column;

        public Builder fromSymbolId(String fromSymbolId) { this.fromSymbolId = fromSymbolId; return this; }
        public Builder toSymbolId(String toSymbolId) { this.toSymbolId = toSymbolId; return this; }
        public Builder kind(ReferenceKind kind) { this.kind = kind; return this; }
        public Builder filePath(String filePath) { this.filePath = filePath; return this; }
        public Builder line(int line) { this.line = line; return this; }
        public Builder column(int column) { this.column = column; return this; }

        public Reference build() {
            return new Reference(fromSymbolId, toSymbolId, kind, filePath, line, column);
        }
    }
}

