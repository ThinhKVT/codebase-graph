package org.example.query;

import org.example.graph.GraphStore;
import org.example.model.Symbol;
import org.example.model.SymbolKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Query service for finding symbols in the graph.
 * Supports name, FQN, kind, file, and pattern matching.
 */
public class SymbolQuery {

    private static final Logger logger = LoggerFactory.getLogger(SymbolQuery.class);

    private final GraphStore graphStore;

    public SymbolQuery(GraphStore graphStore) {
        this.graphStore = graphStore;
    }

    /**
     * Find symbols by exact name.
     */
    public List<Symbol> findByName(String name) {
        logger.debug("Finding symbols by name: {}", name);
        return graphStore.findSymbolsByName(name);
    }

    /**
     * Find symbol by exact fully qualified name.
     */
    public Optional<Symbol> findByFQN(String fullyQualifiedName) {
        logger.debug("Finding symbol by FQN: {}", fullyQualifiedName);
        return graphStore.findSymbolByFQN(fullyQualifiedName);
    }

    /**
     * Find symbols by name pattern with wildcard support.
     * Supports '*' for any characters and '?' for single character.
     */
    public List<Symbol> findByPattern(String pattern) {
        logger.debug("Finding symbols by pattern: {}", pattern);
        // Convert glob pattern to regex-like pattern for Neo4j
        String regexPattern = globToRegex(pattern);
        return graphStore.findSymbolsByNamePattern(regexPattern);
    }

    /**
     * Find symbols by kind.
     */
    public List<Symbol> findByKind(SymbolKind kind) {
        logger.debug("Finding symbols by kind: {}", kind);
        return graphStore.findSymbolsByKind(kind);
    }

    /**
     * Find symbols defined in a specific file.
     */
    public List<Symbol> findByFile(String filePath) {
        logger.debug("Finding symbols in file: {}", filePath);
        return graphStore.findSymbolsByFile(filePath);
    }

    /**
     * Find symbols matching multiple criteria.
     */
    public List<Symbol> find(SymbolSearchCriteria criteria) {
        logger.debug("Finding symbols with criteria: {}", criteria);

        List<Symbol> results = null;

        // Start with the most restrictive criteria
        if (criteria.fullyQualifiedName() != null) {
            Optional<Symbol> symbol = findByFQN(criteria.fullyQualifiedName());
            results = symbol.map(List::of).orElse(List.of());
        } else if (criteria.name() != null) {
            if (criteria.name().contains("*") || criteria.name().contains("?")) {
                results = findByPattern(criteria.name());
            } else {
                results = findByName(criteria.name());
            }
        } else if (criteria.filePath() != null) {
            results = findByFile(criteria.filePath());
        } else if (criteria.kind() != null) {
            results = findByKind(criteria.kind());
        } else {
            // No criteria - return empty (or could return all)
            logger.warn("No search criteria specified");
            return List.of();
        }

        // Apply additional filters
        return filterResults(results, criteria);
    }

    private List<Symbol> filterResults(List<Symbol> results, SymbolSearchCriteria criteria) {
        return results.stream()
            .filter(s -> criteria.kind() == null || s.kind() == criteria.kind())
            .filter(s -> criteria.filePath() == null || matchesPath(s.filePath(), criteria.filePath()))
            .filter(s -> criteria.packageName() == null || matchesPackage(s.fullyQualifiedName(), criteria.packageName()))
            .collect(Collectors.toList());
    }

    private boolean matchesPath(String symbolPath, String filterPath) {
        if (symbolPath == null) return false;
        // Support partial path matching
        return symbolPath.contains(filterPath) || symbolPath.endsWith(filterPath);
    }

    private boolean matchesPackage(String fqn, String packageName) {
        if (fqn == null) return false;
        return fqn.startsWith(packageName + ".") || fqn.startsWith(packageName + "/");
    }

    /**
     * Convert glob pattern (* and ?) to regex pattern.
     */
    private String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder();
        for (char c : glob.toCharArray()) {
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append(".");
                case '.' -> regex.append("\\.");
                case '\\' -> regex.append("\\\\");
                default -> regex.append(c);
            }
        }
        return regex.toString();
    }

    /**
     * Search criteria record for flexible symbol queries.
     */
    public record SymbolSearchCriteria(
        String name,
        String fullyQualifiedName,
        SymbolKind kind,
        String filePath,
        String packageName
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String name;
            private String fullyQualifiedName;
            private SymbolKind kind;
            private String filePath;
            private String packageName;

            public Builder name(String name) { this.name = name; return this; }
            public Builder fullyQualifiedName(String fqn) { this.fullyQualifiedName = fqn; return this; }
            public Builder kind(SymbolKind kind) { this.kind = kind; return this; }
            public Builder filePath(String filePath) { this.filePath = filePath; return this; }
            public Builder packageName(String packageName) { this.packageName = packageName; return this; }

            public SymbolSearchCriteria build() {
                return new SymbolSearchCriteria(name, fullyQualifiedName, kind, filePath, packageName);
            }
        }
    }
}

