package org.example.query;

import org.example.graph.GraphStore;
import org.example.model.Reference;
import org.example.model.ReferenceKind;
import org.example.model.Symbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Query service for finding references between symbols.
 * Supports finding all usages of a symbol with filtering by kind.
 */
public class ReferenceQuery {

    private static final Logger logger = LoggerFactory.getLogger(ReferenceQuery.class);

    private final GraphStore graphStore;
    private final SymbolQuery symbolQuery;

    public ReferenceQuery(GraphStore graphStore) {
        this.graphStore = graphStore;
        this.symbolQuery = new SymbolQuery(graphStore);
    }

    public ReferenceQuery(GraphStore graphStore, SymbolQuery symbolQuery) {
        this.graphStore = graphStore;
        this.symbolQuery = symbolQuery;
    }

    /**
     * Find all references to a symbol by its ID.
     */
    public List<Reference> findReferencesTo(String symbolId) {
        logger.debug("Finding references to symbol: {}", symbolId);
        return graphStore.findReferencesToSymbol(symbolId);
    }

    /**
     * Find all references from a symbol by its ID.
     */
    public List<Reference> findReferencesFrom(String symbolId) {
        logger.debug("Finding references from symbol: {}", symbolId);
        return graphStore.findReferencesFromSymbol(symbolId);
    }

    /**
     * Find references to a symbol by name or FQN.
     * First tries exact FQN match, then falls back to name search.
     */
    public List<ReferenceResult> findReferences(String symbolNameOrFQN) {
        logger.debug("Finding references for: {}", symbolNameOrFQN);

        // Try FQN first
        Optional<Symbol> symbol = symbolQuery.findByFQN(symbolNameOrFQN);

        if (symbol.isPresent()) {
            return findReferencesWithContext(symbol.get());
        }

        // Try name search
        List<Symbol> symbols = symbolQuery.findByName(symbolNameOrFQN);
        if (symbols.isEmpty()) {
            // Try pattern search
            symbols = symbolQuery.findByPattern("*" + symbolNameOrFQN + "*");
        }

        return symbols.stream()
            .flatMap(s -> findReferencesWithContext(s).stream())
            .collect(Collectors.toList());
    }

    /**
     * Find references to a symbol with full context information.
     */
    public List<ReferenceResult> findReferencesWithContext(Symbol symbol) {
        List<Reference> refs = graphStore.findReferencesToSymbol(symbol.id());

        return refs.stream()
            .map(ref -> new ReferenceResult(
                symbol,
                ref.kind(),
                ref.filePath(),
                ref.line(),
                ref.column(),
                getContext(ref.filePath(), ref.line())
            ))
            .collect(Collectors.toList());
    }

    /**
     * Find references filtered by reference kind.
     */
    public List<ReferenceResult> findReferences(String symbolNameOrFQN, ReferenceKind kind) {
        return findReferences(symbolNameOrFQN).stream()
            .filter(r -> r.kind() == kind)
            .collect(Collectors.toList());
    }

    /**
     * Find all definitions of a symbol.
     */
    public List<ReferenceResult> findDefinitions(String symbolNameOrFQN) {
        return findReferences(symbolNameOrFQN, ReferenceKind.DEFINITION);
    }

    /**
     * Find all call sites (non-definition references) of a symbol.
     */
    public List<ReferenceResult> findUsages(String symbolNameOrFQN) {
        return findReferences(symbolNameOrFQN).stream()
            .filter(r -> r.kind() != ReferenceKind.DEFINITION)
            .collect(Collectors.toList());
    }

    /**
     * Find import statements for a symbol.
     */
    public List<ReferenceResult> findImports(String symbolNameOrFQN) {
        return findReferences(symbolNameOrFQN, ReferenceKind.IMPORT);
    }

    /**
     * Count total references to a symbol.
     */
    public int countReferences(String symbolId) {
        return graphStore.findReferencesToSymbol(symbolId).size();
    }

    /**
     * Get context (source line) for a reference location.
     * This is a placeholder - in real implementation would read from file.
     */
    private String getContext(String filePath, int line) {
        // TODO: Read actual source line from file
        return null;
    }

    /**
     * Result record containing reference information with context.
     */
    public record ReferenceResult(
        Symbol targetSymbol,
        ReferenceKind kind,
        String filePath,
        int line,
        int column,
        String context
    ) {
        /**
         * Format as "file:line:column"
         */
        public String location() {
            return String.format("%s:%d:%d", filePath != null ? filePath : "<unknown>", line, column);
        }

        /**
         * Format for display.
         */
        public String toDisplayString() {
            StringBuilder sb = new StringBuilder();
            sb.append(location());
            sb.append(" [").append(kind.name()).append("]");
            if (context != null && !context.isBlank()) {
                sb.append(" ").append(context.trim());
            }
            return sb.toString();
        }
    }
}

