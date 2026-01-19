package org.example.query;

import org.example.graph.GraphStore;
import org.example.model.Symbol;
import org.example.model.SymbolKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Query service for finding dependencies between symbols.
 * Supports direct and transitive dependency queries.
 */
public class DependencyQuery {

    private static final Logger logger = LoggerFactory.getLogger(DependencyQuery.class);

    private final GraphStore graphStore;
    private final SymbolQuery symbolQuery;

    public DependencyQuery(GraphStore graphStore) {
        this.graphStore = graphStore;
        this.symbolQuery = new SymbolQuery(graphStore);
    }

    public DependencyQuery(GraphStore graphStore, SymbolQuery symbolQuery) {
        this.graphStore = graphStore;
        this.symbolQuery = symbolQuery;
    }

    /**
     * Find direct dependencies of a symbol.
     */
    public List<Symbol> findDirectDependencies(String symbolId) {
        logger.debug("Finding direct dependencies for: {}", symbolId);
        return graphStore.findDependencies(symbolId);
    }

    /**
     * Find transitive dependencies with configurable depth.
     * @param symbolId The symbol to find dependencies for
     * @param depth Maximum depth to traverse (1 = direct only)
     * @return List of dependent symbols
     */
    public List<Symbol> findTransitiveDependencies(String symbolId, int depth) {
        logger.debug("Finding transitive dependencies for: {} with depth: {}", symbolId, depth);
        if (depth < 1) {
            return List.of();
        }
        return graphStore.findTransitiveDependencies(symbolId, depth);
    }

    /**
     * Find dependencies by symbol name or FQN.
     */
    public DependencyResult findDependencies(String symbolNameOrFQN, int depth) {
        logger.debug("Finding dependencies for: {} with depth: {}", symbolNameOrFQN, depth);

        // Resolve symbol
        Optional<Symbol> symbol = symbolQuery.findByFQN(symbolNameOrFQN);
        if (symbol.isEmpty()) {
            List<Symbol> byName = symbolQuery.findByName(symbolNameOrFQN);
            if (!byName.isEmpty()) {
                symbol = Optional.of(byName.get(0));
            }
        }

        if (symbol.isEmpty()) {
            logger.warn("Symbol not found: {}", symbolNameOrFQN);
            return DependencyResult.empty(symbolNameOrFQN);
        }

        Symbol rootSymbol = symbol.get();
        DependencyNode rootNode = buildDependencyTree(rootSymbol, depth, new HashSet<>());

        return new DependencyResult(rootSymbol, rootNode, depth);
    }

    /**
     * Find symbols that depend on the given symbol (reverse dependencies).
     */
    public List<Symbol> findDirectDependents(String symbolId) {
        logger.debug("Finding direct dependents for: {}", symbolId);
        return graphStore.findDependents(symbolId);
    }

    /**
     * Find transitive dependents with configurable depth.
     */
    public List<Symbol> findTransitiveDependents(String symbolId, int depth) {
        logger.debug("Finding transitive dependents for: {} with depth: {}", symbolId, depth);
        if (depth < 1) {
            return List.of();
        }
        return graphStore.findTransitiveDependents(symbolId, depth);
    }

    /**
     * Find dependents by symbol name or FQN.
     */
    public DependencyResult findDependents(String symbolNameOrFQN, int depth) {
        logger.debug("Finding dependents for: {} with depth: {}", symbolNameOrFQN, depth);

        Optional<Symbol> symbol = symbolQuery.findByFQN(symbolNameOrFQN);
        if (symbol.isEmpty()) {
            List<Symbol> byName = symbolQuery.findByName(symbolNameOrFQN);
            if (!byName.isEmpty()) {
                symbol = Optional.of(byName.get(0));
            }
        }

        if (symbol.isEmpty()) {
            logger.warn("Symbol not found: {}", symbolNameOrFQN);
            return DependencyResult.empty(symbolNameOrFQN);
        }

        Symbol rootSymbol = symbol.get();
        DependencyNode rootNode = buildDependentTree(rootSymbol, depth, new HashSet<>());

        return new DependencyResult(rootSymbol, rootNode, depth);
    }

    /**
     * Build a dependency tree starting from the given symbol.
     */
    private DependencyNode buildDependencyTree(Symbol symbol, int remainingDepth, Set<String> visited) {
        if (visited.contains(symbol.id())) {
            // Cycle detected
            return new DependencyNode(symbol, List.of(), true);
        }

        if (remainingDepth <= 0) {
            return new DependencyNode(symbol, List.of(), false);
        }

        visited.add(symbol.id());

        List<Symbol> deps = graphStore.findDependencies(symbol.id());
        List<DependencyNode> children = deps.stream()
            .map(dep -> buildDependencyTree(dep, remainingDepth - 1, new HashSet<>(visited)))
            .collect(Collectors.toList());

        return new DependencyNode(symbol, children, false);
    }

    /**
     * Build a dependent tree (reverse dependencies) starting from the given symbol.
     */
    private DependencyNode buildDependentTree(Symbol symbol, int remainingDepth, Set<String> visited) {
        if (visited.contains(symbol.id())) {
            return new DependencyNode(symbol, List.of(), true);
        }

        if (remainingDepth <= 0) {
            return new DependencyNode(symbol, List.of(), false);
        }

        visited.add(symbol.id());

        List<Symbol> dependents = graphStore.findDependents(symbol.id());
        List<DependencyNode> children = dependents.stream()
            .map(dep -> buildDependentTree(dep, remainingDepth - 1, new HashSet<>(visited)))
            .collect(Collectors.toList());

        return new DependencyNode(symbol, children, false);
    }

    /**
     * Get flat list of all dependencies from the tree.
     */
    public List<Symbol> flattenDependencies(DependencyNode node) {
        List<Symbol> result = new ArrayList<>();
        flattenNode(node, result, new HashSet<>());
        // Remove the root node itself
        if (!result.isEmpty()) {
            result.remove(0);
        }
        return result;
    }

    private void flattenNode(DependencyNode node, List<Symbol> result, Set<String> seen) {
        if (seen.contains(node.symbol().id())) {
            return;
        }
        seen.add(node.symbol().id());
        result.add(node.symbol());
        for (DependencyNode child : node.children()) {
            flattenNode(child, result, seen);
        }
    }

    /**
     * Result record containing dependency information.
     */
    public record DependencyResult(
        Symbol rootSymbol,
        DependencyNode tree,
        int depth
    ) {
        public static DependencyResult empty(String symbolName) {
            return new DependencyResult(null, null, 0);
        }

        public boolean isEmpty() {
            return rootSymbol == null;
        }

        public int totalDependencies() {
            if (tree == null) return 0;
            return countNodes(tree) - 1; // Exclude root
        }

        private int countNodes(DependencyNode node) {
            int count = 1;
            for (DependencyNode child : node.children()) {
                count += countNodes(child);
            }
            return count;
        }
    }

    /**
     * Node in the dependency tree.
     */
    public record DependencyNode(
        Symbol symbol,
        List<DependencyNode> children,
        boolean isCycle
    ) {
        /**
         * Format as tree string.
         */
        public String toTreeString() {
            StringBuilder sb = new StringBuilder();
            buildTreeString(sb, "", true);
            return sb.toString();
        }

        private void buildTreeString(StringBuilder sb, String prefix, boolean isLast) {
            sb.append(prefix);
            sb.append(isLast ? "└── " : "├── ");
            sb.append(symbol.name());
            if (symbol.kind() != null) {
                sb.append(" (").append(symbol.kind()).append(")");
            }
            if (isCycle) {
                sb.append(" [CYCLE]");
            }
            sb.append("\n");

            for (int i = 0; i < children.size(); i++) {
                boolean last = (i == children.size() - 1);
                String childPrefix = prefix + (isLast ? "    " : "│   ");
                children.get(i).buildTreeString(sb, childPrefix, last);
            }
        }

        /**
         * Format as JSON-like structure.
         */
        public String toJsonString() {
            StringBuilder sb = new StringBuilder();
            buildJsonString(sb, 0);
            return sb.toString();
        }

        private void buildJsonString(StringBuilder sb, int indent) {
            String pad = "  ".repeat(indent);
            sb.append(pad).append("{\n");
            sb.append(pad).append("  \"name\": \"").append(escapeJson(symbol.name())).append("\",\n");
            sb.append(pad).append("  \"fqn\": \"").append(escapeJson(symbol.fullyQualifiedName())).append("\",\n");
            sb.append(pad).append("  \"kind\": \"").append(symbol.kind()).append("\",\n");
            if (isCycle) {
                sb.append(pad).append("  \"cycle\": true,\n");
            }
            sb.append(pad).append("  \"dependencies\": [");
            if (children.isEmpty()) {
                sb.append("]\n");
            } else {
                sb.append("\n");
                for (int i = 0; i < children.size(); i++) {
                    children.get(i).buildJsonString(sb, indent + 2);
                    if (i < children.size() - 1) {
                        sb.append(",");
                    }
                    sb.append("\n");
                }
                sb.append(pad).append("  ]\n");
            }
            sb.append(pad).append("}");
        }

        private String escapeJson(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}

