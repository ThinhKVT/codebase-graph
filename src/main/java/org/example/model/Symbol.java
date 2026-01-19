package org.example.model;

import java.util.Objects;

/**
 * Represents a symbol (class, method, field, etc.) in the codebase.
 */
public record Symbol(
    String id,
    String name,
    String fullyQualifiedName,
    SymbolKind kind,
    String filePath,
    int startLine,
    int endLine,
    String signature,
    String documentation,
    String parentId
) {
    public Symbol {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(kind, "kind cannot be null");
    }

    public static Symbol of(String id, String name, SymbolKind kind) {
        return new Symbol(id, name, id, kind, null, 0, 0, null, null, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String name;
        private String fullyQualifiedName;
        private SymbolKind kind;
        private String filePath;
        private int startLine;
        private int endLine;
        private String signature;
        private String documentation;
        private String parentId;

        public Builder id(String id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder fullyQualifiedName(String fqn) { this.fullyQualifiedName = fqn; return this; }
        public Builder kind(SymbolKind kind) { this.kind = kind; return this; }
        public Builder filePath(String filePath) { this.filePath = filePath; return this; }
        public Builder startLine(int startLine) { this.startLine = startLine; return this; }
        public Builder endLine(int endLine) { this.endLine = endLine; return this; }
        public Builder signature(String signature) { this.signature = signature; return this; }
        public Builder documentation(String documentation) { this.documentation = documentation; return this; }
        public Builder parentId(String parentId) { this.parentId = parentId; return this; }

        public Symbol build() {
            if (fullyQualifiedName == null) fullyQualifiedName = id;
            return new Symbol(id, name, fullyQualifiedName, kind, filePath, startLine, endLine, signature, documentation, parentId);
        }
    }
}

