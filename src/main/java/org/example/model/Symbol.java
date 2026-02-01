package org.example.model;

import java.util.Objects;

/**
 * Represents a symbol (class, method, field, etc.) in the codebase.
 * 
 * <p>Extended with additional metadata for language-specific features:</p>
 * <ul>
 *   <li>displayName - Human-readable name from SCIP display_name</li>
 *   <li>typeId - Type reference for fields/parameters/return types</li>
 *   <li>Modifiers - isStatic, isAbstract, isFinal</li>
 *   <li>visibility - PUBLIC, PRIVATE, PROTECTED, PACKAGE</li>
 *   <li>Flags - isGenerated, isTest</li>
 * </ul>
 */
public record Symbol(
    // Core identity
    String id,
    String name,
    String fullyQualifiedName,
    SymbolKind kind,
    
    // Location
    String filePath,
    int startLine,
    int endLine,
    
    // Documentation
    String signature,
    String documentation,
    
    // Hierarchy
    String parentId,
    
    // Extended metadata
    String displayName,      // Human-readable name (from SCIP display_name)
    String typeId,           // Type reference (for fields, parameters, return types)
    
    // Modifiers
    boolean isStatic,
    boolean isAbstract,
    boolean isFinal,
    String visibility,       // PUBLIC, PRIVATE, PROTECTED, PACKAGE
    
    // Flags
    boolean isGenerated,
    boolean isTest
) {
    public Symbol {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(kind, "kind cannot be null");
    }

    /**
     * Create a minimal symbol with just id, name, and kind.
     */
    public static Symbol of(String id, String name, SymbolKind kind) {
        return new Symbol(id, name, id, kind, null, 0, 0, null, null, null,
            null, null, false, false, false, null, false, false);
    }

    /**
     * Create a builder for constructing Symbol instances.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get the effective display name (displayName if set, otherwise name).
     */
    public String getEffectiveDisplayName() {
        return displayName != null && !displayName.isEmpty() ? displayName : name;
    }

    /**
     * Check if this symbol has type information.
     */
    public boolean hasType() {
        return typeId != null && !typeId.isEmpty();
    }

    /**
     * Check if this is a public symbol.
     */
    public boolean isPublic() {
        return "PUBLIC".equalsIgnoreCase(visibility);
    }

    /**
     * Check if this is a private symbol.
     */
    public boolean isPrivate() {
        return "PRIVATE".equalsIgnoreCase(visibility);
    }

    /**
     * Builder for Symbol record.
     */
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
        private String displayName;
        private String typeId;
        private boolean isStatic;
        private boolean isAbstract;
        private boolean isFinal;
        private String visibility;
        private boolean isGenerated;
        private boolean isTest;

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
        public Builder displayName(String displayName) { this.displayName = displayName; return this; }
        public Builder typeId(String typeId) { this.typeId = typeId; return this; }
        public Builder isStatic(boolean isStatic) { this.isStatic = isStatic; return this; }
        public Builder isAbstract(boolean isAbstract) { this.isAbstract = isAbstract; return this; }
        public Builder isFinal(boolean isFinal) { this.isFinal = isFinal; return this; }
        public Builder visibility(String visibility) { this.visibility = visibility; return this; }
        public Builder isGenerated(boolean isGenerated) { this.isGenerated = isGenerated; return this; }
        public Builder isTest(boolean isTest) { this.isTest = isTest; return this; }

        public Symbol build() {
            if (fullyQualifiedName == null) fullyQualifiedName = id;
            return new Symbol(
                id, name, fullyQualifiedName, kind,
                filePath, startLine, endLine,
                signature, documentation, parentId,
                displayName, typeId,
                isStatic, isAbstract, isFinal, visibility,
                isGenerated, isTest
            );
        }
    }
}
