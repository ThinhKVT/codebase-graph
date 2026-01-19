package org.example.graph;

import java.util.HashMap;
import java.util.Map;

/**
 * Cypher query builders for common graph patterns.
 * Provides parameterized queries to prevent injection.
 */
public final class GraphQueries {

    private GraphQueries() {
        // Utility class
    }

    // ==================== Symbol Queries ====================

    /**
     * Find symbol by exact name.
     */
    public static QueryWithParams findSymbolsByName(String name) {
        String cypher = "MATCH (s:Symbol {name: $name}) RETURN s";
        return new QueryWithParams(cypher, Map.of("name", name));
    }

    /**
     * Find symbol by exact FQN.
     */
    public static QueryWithParams findSymbolByFQN(String fqn) {
        String cypher = "MATCH (s:Symbol {fullyQualifiedName: $fqn}) RETURN s";
        return new QueryWithParams(cypher, Map.of("fqn", fqn));
    }

    /**
     * Find symbols by name pattern (regex).
     */
    public static QueryWithParams findSymbolsByNamePattern(String pattern) {
        String cypher = "MATCH (s:Symbol) WHERE s.name =~ $pattern RETURN s";
        return new QueryWithParams(cypher, Map.of("pattern", pattern));
    }

    /**
     * Find symbols by kind.
     */
    public static QueryWithParams findSymbolsByKind(String kind) {
        String cypher = "MATCH (s:Symbol {kind: $kind}) RETURN s";
        return new QueryWithParams(cypher, Map.of("kind", kind));
    }

    /**
     * Find symbols in a file.
     */
    public static QueryWithParams findSymbolsByFile(String filePath) {
        String cypher = "MATCH (s:Symbol {filePath: $filePath}) RETURN s ORDER BY s.startLine";
        return new QueryWithParams(cypher, Map.of("filePath", filePath));
    }

    /**
     * Find symbols in package (by FQN prefix).
     */
    public static QueryWithParams findSymbolsByPackage(String packageName) {
        String cypher = "MATCH (s:Symbol) WHERE s.fullyQualifiedName STARTS WITH $prefix RETURN s";
        return new QueryWithParams(cypher, Map.of("prefix", packageName + "."));
    }

    // ==================== Reference Queries ====================

    /**
     * Find all references to a symbol.
     */
    public static QueryWithParams findReferencesToSymbol(String symbolId) {
        String cypher = """
            MATCH (from:Symbol)-[r:REFERENCES]->(to:Symbol {id: $symbolId})
            RETURN from.id AS fromSymbolId, to.id AS toSymbolId, 
                   r.kind AS kind, r.filePath AS filePath, 
                   r.line AS line, r.column AS column
            ORDER BY r.filePath, r.line
            """;
        return new QueryWithParams(cypher, Map.of("symbolId", symbolId));
    }

    /**
     * Find all references from a symbol.
     */
    public static QueryWithParams findReferencesFromSymbol(String symbolId) {
        String cypher = """
            MATCH (from:Symbol {id: $symbolId})-[r:REFERENCES]->(to:Symbol)
            RETURN from.id AS fromSymbolId, to.id AS toSymbolId, 
                   r.kind AS kind, r.filePath AS filePath, 
                   r.line AS line, r.column AS column
            ORDER BY r.filePath, r.line
            """;
        return new QueryWithParams(cypher, Map.of("symbolId", symbolId));
    }

    /**
     * Find references by kind.
     */
    public static QueryWithParams findReferencesByKind(String symbolId, String kind) {
        String cypher = """
            MATCH (from:Symbol)-[r:REFERENCES {kind: $kind}]->(to:Symbol {id: $symbolId})
            RETURN from.id AS fromSymbolId, to.id AS toSymbolId, 
                   r.kind AS kind, r.filePath AS filePath, 
                   r.line AS line, r.column AS column
            ORDER BY r.filePath, r.line
            """;
        return new QueryWithParams(cypher, Map.of("symbolId", symbolId, "kind", kind));
    }

    // ==================== Dependency Queries ====================

    /**
     * Find direct dependencies of a symbol.
     */
    public static QueryWithParams findDependencies(String symbolId) {
        String cypher = """
            MATCH (s:Symbol {id: $symbolId})-[r:REFERENCES]->(dep:Symbol)
            WHERE r.kind IN ['EXTENDS', 'IMPLEMENTS', 'IMPORT', 'TYPE_REFERENCE']
            RETURN DISTINCT dep
            """;
        return new QueryWithParams(cypher, Map.of("symbolId", symbolId));
    }

    /**
     * Find transitive dependencies with depth limit.
     */
    public static QueryWithParams findTransitiveDependencies(String symbolId, int depth) {
        String cypher = """
            MATCH path = (s:Symbol {id: $symbolId})-[:REFERENCES*1..%d]->(dep:Symbol)
            WHERE ALL(r IN relationships(path) WHERE r.kind IN ['EXTENDS', 'IMPLEMENTS', 'IMPORT', 'TYPE_REFERENCE'])
            RETURN DISTINCT dep, length(path) AS depth
            ORDER BY depth
            """.formatted(depth);
        return new QueryWithParams(cypher, Map.of("symbolId", symbolId));
    }

    /**
     * Find symbols that depend on this symbol (reverse dependencies).
     */
    public static QueryWithParams findDependents(String symbolId) {
        String cypher = """
            MATCH (dependent:Symbol)-[r:REFERENCES]->(s:Symbol {id: $symbolId})
            WHERE r.kind IN ['EXTENDS', 'IMPLEMENTS', 'IMPORT', 'TYPE_REFERENCE']
            RETURN DISTINCT dependent
            """;
        return new QueryWithParams(cypher, Map.of("symbolId", symbolId));
    }

    /**
     * Find transitive dependents with depth limit.
     */
    public static QueryWithParams findTransitiveDependents(String symbolId, int depth) {
        String cypher = """
            MATCH path = (dependent:Symbol)-[:REFERENCES*1..%d]->(s:Symbol {id: $symbolId})
            WHERE ALL(r IN relationships(path) WHERE r.kind IN ['EXTENDS', 'IMPLEMENTS', 'IMPORT', 'TYPE_REFERENCE'])
            RETURN DISTINCT dependent, length(path) AS depth
            ORDER BY depth
            """.formatted(depth);
        return new QueryWithParams(cypher, Map.of("symbolId", symbolId));
    }

    // ==================== Statistics Queries ====================

    /**
     * Count symbols by kind.
     */
    public static QueryWithParams countSymbolsByKind() {
        String cypher = "MATCH (s:Symbol) RETURN s.kind AS kind, count(s) AS count ORDER BY count DESC";
        return new QueryWithParams(cypher, Map.of());
    }

    /**
     * Find most referenced symbols.
     */
    public static QueryWithParams findMostReferencedSymbols(int limit) {
        String cypher = """
            MATCH (s:Symbol)<-[r:REFERENCES]-()
            RETURN s, count(r) AS refCount
            ORDER BY refCount DESC
            LIMIT $limit
            """;
        return new QueryWithParams(cypher, Map.of("limit", limit));
    }

    /**
     * Record containing a Cypher query and its parameters.
     */
    public record QueryWithParams(String cypher, Map<String, Object> params) {

        /**
         * Create a new query with additional parameters.
         */
        public QueryWithParams withParam(String key, Object value) {
            Map<String, Object> newParams = new HashMap<>(params);
            newParams.put(key, value);
            return new QueryWithParams(cypher, newParams);
        }
    }
}

