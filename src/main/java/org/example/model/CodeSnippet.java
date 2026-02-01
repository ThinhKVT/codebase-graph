package org.example.model;

import java.util.Objects;

/**
 * Represents a snippet of source code with optional context lines.
 */
public record CodeSnippet(
    String sourceCode,
    String contextBefore,
    String contextAfter,
    int startLine,
    int endLine
) {
    public CodeSnippet {
        Objects.requireNonNull(sourceCode, "sourceCode cannot be null");
        Objects.requireNonNull(contextBefore, "contextBefore cannot be null");
        Objects.requireNonNull(contextAfter, "contextAfter cannot be null");
    }

    /**
     * Full content: context before + source + context after.
     */
    public String fullContent() {
        StringBuilder sb = new StringBuilder();
        if (!contextBefore.isEmpty()) {
            sb.append(contextBefore).append("\n");
        }
        sb.append(sourceCode);
        if (!contextAfter.isEmpty()) {
            sb.append("\n").append(contextAfter);
        }
        return sb.toString();
    }
}
