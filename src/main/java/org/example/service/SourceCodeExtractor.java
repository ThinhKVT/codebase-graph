package org.example.service;

import org.example.model.CodeSnippet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Extracts source code snippets from files using line ranges and optional context.
 * Tries multiple base paths (e.g. repository roots) to resolve relative file paths.
 */
public class SourceCodeExtractor {

    private static final Logger logger = LoggerFactory.getLogger(SourceCodeExtractor.class);

    private final List<Path> basePaths;

    public SourceCodeExtractor(List<Path> basePaths) {
        this.basePaths = basePaths != null && !basePaths.isEmpty()
            ? List.copyOf(basePaths)
            : List.of(Path.of("."));
    }

    /**
     * Resolve relative file path against base paths; returns first path that exists.
     */
    public Path resolvePath(String relativeFilePath) throws SourceNotFoundException {
        if (relativeFilePath == null || relativeFilePath.isBlank()) {
            throw new SourceNotFoundException("File path is empty");
        }
        String normalized = relativeFilePath.replace('\\', '/').replaceAll("^/", "");
        for (Path base : basePaths) {
            Path resolved = base.resolve(normalized).normalize();
            // Security: Ensure resolved path is within the base path
            if (!resolved.startsWith(base)) {
                continue; // Skip this base path, potential path traversal attempt
            }
            if (Files.isRegularFile(resolved)) {
                return resolved;
            }
        }
        throw new SourceNotFoundException("File not found in any base path: " + relativeFilePath);
    }

    /**
     * Extract code snippet for the given line range (1-based inclusive) with context lines.
     *
     * @param relativeFilePath path relative to one of the base paths
     * @param startLine        1-based start line (inclusive)
     * @param endLine          1-based end line (inclusive)
     * @param contextLines     number of lines before/after to include
     * @return CodeSnippet with source and context
     */
    public CodeSnippet extract(String relativeFilePath, int startLine, int endLine, int contextLines)
            throws SourceNotFoundException, IOException {
        Path path = resolvePath(relativeFilePath);
        return extractFromPath(path, startLine, endLine, contextLines);
    }

    /**
     * Extract code snippet from an absolute path.
     */
    public CodeSnippet extractFromPath(Path path, int startLine, int endLine, int contextLines)
            throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new SourceNotFoundException("File not found: " + path);
        }
        
        // Check file size to prevent memory exhaustion (10MB limit)
        long fileSize = Files.size(path);
        if (fileSize > 10_000_000) {
            throw new IOException("File too large to extract: " + path + " (" + fileSize + " bytes)");
        }
        
        List<String> allLines = Files.readAllLines(path);

        // Convert to 0-based indices; clamp to valid range
        int totalLines = allLines.size();
        int start0 = Math.max(0, startLine - 1);
        int end0 = Math.min(totalLines, endLine);
        if (start0 >= end0) {
            return new CodeSnippet("", "", "", startLine, endLine);
        }

        int contextStart = Math.max(0, start0 - contextLines);
        int contextEnd = Math.min(totalLines, end0 + contextLines);

        String sourceCode = String.join("\n", allLines.subList(start0, end0));
        String contextBefore = contextStart < start0
            ? String.join("\n", allLines.subList(contextStart, start0))
            : "";
        String contextAfter = end0 < contextEnd
            ? String.join("\n", allLines.subList(end0, contextEnd))
            : "";

        return new CodeSnippet(sourceCode, contextBefore, contextAfter, startLine, endLine);
    }

    public List<Path> getBasePaths() {
        return basePaths;
    }

    /**
     * Thrown when the source file cannot be found in any configured base path.
     */
    public static class SourceNotFoundException extends RuntimeException {
        public SourceNotFoundException(String message) {
            super(message);
        }

        public SourceNotFoundException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
