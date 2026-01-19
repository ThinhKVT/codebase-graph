package org.example.cli;

import org.example.graph.GraphStore;
import org.example.graph.Neo4jGraphStore;
import org.example.model.Reference;
import org.example.model.Symbol;
import org.example.model.SymbolKind;
import org.example.query.ReferenceQuery;
import org.example.query.SymbolQuery;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * Query command with subcommands for symbols, refs, and deps.
 */
@Command(
    name = "query",
    description = "Query the code graph",
    subcommands = {
        QueryCommand.SymbolsSubcommand.class,
        QueryCommand.RefsSubcommand.class,
        QueryCommand.DepsSubcommand.class
    }
)
public class QueryCommand implements Callable<Integer> {

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Show help")
    boolean help;

    @Override
    public Integer call() {
        System.out.println("Use a subcommand: symbols, refs, deps");
        System.out.println("Run 'query <subcommand> --help' for more info");
        return 0;
    }

    // Shared options for Neo4j connection
    static class Neo4jOptions {
        @Option(names = "--neo4j-uri", description = "Neo4j URI", defaultValue = "bolt://localhost:7687")
        String uri;

        @Option(names = "--neo4j-user", description = "Neo4j username", defaultValue = "neo4j")
        String username;

        @Option(names = "--neo4j-password", description = "Neo4j password", defaultValue = "password")
        String password;

        GraphStore createGraphStore() {
            return new Neo4jGraphStore(uri, username, password);
        }
    }

    // Shared output format option
    static class OutputOptions {
        @Option(names = {"-f", "--format"}, description = "Output format: table, json", defaultValue = "table")
        String format;

        boolean isJson() {
            return "json".equalsIgnoreCase(format);
        }
    }

    /**
     * T033: Query symbols subcommand
     */
    @Command(name = "symbols", description = "Search for symbols")
    static class SymbolsSubcommand implements Callable<Integer> {

        @CommandLine.Mixin
        Neo4jOptions neo4j = new Neo4jOptions();

        @CommandLine.Mixin
        OutputOptions output = new OutputOptions();

        @Option(names = {"-n", "--name"}, description = "Symbol name (supports * and ? wildcards)")
        String name;

        @Option(names = {"-k", "--kind"}, description = "Symbol kind: CLASS, METHOD, FIELD, etc.")
        String kind;

        @Option(names = {"--file"}, description = "File path (partial match)")
        String file;

        @Option(names = {"-p", "--package"}, description = "Package name prefix")
        String packageName;

        @Option(names = {"--fqn"}, description = "Exact fully qualified name")
        String fqn;

        @Option(names = {"-h", "--help"}, usageHelp = true, description = "Show help")
        boolean help;

        @Override
        public Integer call() {
            if (name == null && kind == null && file == null && packageName == null && fqn == null) {
                System.err.println("Error: At least one search criteria required (--name, --kind, --file, --package, --fqn)");
                return 1;
            }

            try (GraphStore store = neo4j.createGraphStore()) {
                store.connect();
                SymbolQuery query = new SymbolQuery(store);

                SymbolQuery.SymbolSearchCriteria criteria = SymbolQuery.SymbolSearchCriteria.builder()
                    .name(name)
                    .fullyQualifiedName(fqn)
                    .kind(kind != null ? SymbolKind.valueOf(kind.toUpperCase()) : null)
                    .filePath(file)
                    .packageName(packageName)
                    .build();

                List<Symbol> symbols = query.find(criteria);

                if (symbols.isEmpty()) {
                    System.out.println("No symbols found");
                    return 0;
                }

                if (output.isJson()) {
                    printSymbolsJson(symbols);
                } else {
                    printSymbolsTable(symbols);
                }

                return 0;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }

        private void printSymbolsTable(List<Symbol> symbols) {
            System.out.printf("%-40s %-12s %s%n", "NAME", "KIND", "LOCATION");
            System.out.println("-".repeat(80));
            for (Symbol s : symbols) {
                String location = s.filePath() != null
                    ? s.filePath() + ":" + s.startLine()
                    : "<unknown>";
                System.out.printf("%-40s %-12s %s%n",
                    truncate(s.name(), 40),
                    s.kind(),
                    truncate(location, 40));
            }
            System.out.println("-".repeat(80));
            System.out.printf("Total: %d symbols%n", symbols.size());
        }

        private void printSymbolsJson(List<Symbol> symbols) {
            System.out.println("[");
            for (int i = 0; i < symbols.size(); i++) {
                Symbol s = symbols.get(i);
                System.out.printf("  {\"name\": \"%s\", \"kind\": \"%s\", \"fqn\": \"%s\", \"file\": \"%s\", \"line\": %d}%s%n",
                    escape(s.name()),
                    s.kind(),
                    escape(s.fullyQualifiedName()),
                    escape(s.filePath()),
                    s.startLine(),
                    i < symbols.size() - 1 ? "," : "");
            }
            System.out.println("]");
        }

        private String truncate(String s, int max) {
            if (s == null) return "";
            return s.length() > max ? s.substring(0, max - 3) + "..." : s;
        }

        private String escape(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }

    /**
     * T034: Query refs subcommand
     */
    @Command(name = "refs", description = "Find all references to a symbol")
    static class RefsSubcommand implements Callable<Integer> {

        @CommandLine.Mixin
        Neo4jOptions neo4j = new Neo4jOptions();

        @CommandLine.Mixin
        OutputOptions output = new OutputOptions();

        @Parameters(index = "0", description = "Symbol name or FQN to find references for")
        String symbol;

        @Option(names = {"-k", "--kind"}, description = "Reference kind filter: DEFINITION, REFERENCE, IMPORT, etc.")
        String kind;

        @Option(names = {"--definitions"}, description = "Show only definitions")
        boolean definitionsOnly;

        @Option(names = {"--usages"}, description = "Show only usages (non-definition references)")
        boolean usagesOnly;

        @Option(names = {"-h", "--help"}, usageHelp = true, description = "Show help")
        boolean help;

        @Override
        public Integer call() {
            try (GraphStore store = neo4j.createGraphStore()) {
                store.connect();
                ReferenceQuery query = new ReferenceQuery(store);

                List<ReferenceQuery.ReferenceResult> results;

                if (definitionsOnly) {
                    results = query.findDefinitions(symbol);
                } else if (usagesOnly) {
                    results = query.findUsages(symbol);
                } else if (kind != null) {
                    results = query.findReferences(symbol,
                        org.example.model.ReferenceKind.valueOf(kind.toUpperCase()));
                } else {
                    results = query.findReferences(symbol);
                }

                if (results.isEmpty()) {
                    System.out.println("No references found for: " + symbol);
                    return 0;
                }

                if (output.isJson()) {
                    printRefsJson(results);
                } else {
                    printRefsTable(results);
                }

                return 0;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }

        private void printRefsTable(List<ReferenceQuery.ReferenceResult> results) {
            System.out.printf("References to: %s%n%n", symbol);
            System.out.printf("%-50s %-15s%n", "LOCATION", "KIND");
            System.out.println("-".repeat(70));
            for (ReferenceQuery.ReferenceResult r : results) {
                System.out.printf("%-50s %-15s%n",
                    truncate(r.location(), 50),
                    r.kind());
            }
            System.out.println("-".repeat(70));
            System.out.printf("Total: %d references%n", results.size());
        }

        private void printRefsJson(List<ReferenceQuery.ReferenceResult> results) {
            System.out.println("{");
            System.out.printf("  \"symbol\": \"%s\",%n", escape(symbol));
            System.out.println("  \"references\": [");
            for (int i = 0; i < results.size(); i++) {
                ReferenceQuery.ReferenceResult r = results.get(i);
                System.out.printf("    {\"file\": \"%s\", \"line\": %d, \"column\": %d, \"kind\": \"%s\"}%s%n",
                    escape(r.filePath()),
                    r.line(),
                    r.column(),
                    r.kind(),
                    i < results.size() - 1 ? "," : "");
            }
            System.out.println("  ]");
            System.out.println("}");
        }

        private String truncate(String s, int max) {
            if (s == null) return "";
            return s.length() > max ? s.substring(0, max - 3) + "..." : s;
        }

        private String escape(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }

    /**
     * T040 & T041: Deps subcommand - Query dependencies and dependents
     */
    @Command(name = "deps", description = "Query dependencies of a symbol")
    static class DepsSubcommand implements Callable<Integer> {

        @CommandLine.Mixin
        Neo4jOptions neo4j = new Neo4jOptions();

        @CommandLine.Mixin
        OutputOptions output = new OutputOptions();

        @Parameters(index = "0", description = "Symbol name or FQN")
        String symbol;

        @Option(names = {"-d", "--depth"}, description = "Transitive dependency depth (default: 1)", defaultValue = "1")
        int depth;

        @Option(names = {"--reverse", "-r"}, description = "Show dependents (what depends on this symbol)")
        boolean reverse;

        @Option(names = {"--tree"}, description = "Show as tree view (default for table format)")
        boolean tree;

        @Option(names = {"--flat"}, description = "Show as flat list")
        boolean flat;

        @Option(names = {"-h", "--help"}, usageHelp = true, description = "Show help")
        boolean help;

        @Override
        public Integer call() {
            try (GraphStore store = neo4j.createGraphStore()) {
                store.connect();
                org.example.query.DependencyQuery query = new org.example.query.DependencyQuery(store);

                org.example.query.DependencyQuery.DependencyResult result;
                String queryType;

                if (reverse) {
                    result = query.findDependents(symbol, depth);
                    queryType = "Dependents of";
                } else {
                    result = query.findDependencies(symbol, depth);
                    queryType = "Dependencies of";
                }

                if (result.isEmpty()) {
                    System.out.println("Symbol not found: " + symbol);
                    return 0;
                }

                if (result.tree().children().isEmpty()) {
                    System.out.printf("%s %s: (none)%n", queryType, result.rootSymbol().name());
                    return 0;
                }

                if (output.isJson()) {
                    printDepsJson(result, queryType);
                } else if (flat) {
                    printDepsFlat(result, queryType, query);
                } else {
                    printDepsTree(result, queryType);
                }

                return 0;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }

        private void printDepsTree(org.example.query.DependencyQuery.DependencyResult result, String queryType) {
            System.out.printf("%s %s (depth: %d)%n%n", queryType, result.rootSymbol().name(), depth);

            // Print root
            System.out.printf("%s (%s)%n", result.rootSymbol().name(), result.rootSymbol().kind());

            // Print children as tree
            var children = result.tree().children();
            for (int i = 0; i < children.size(); i++) {
                boolean isLast = (i == children.size() - 1);
                printTreeNode(children.get(i), "", isLast);
            }

            System.out.println();
            System.out.printf("Total: %d %s%n", result.totalDependencies(),
                reverse ? "dependents" : "dependencies");
        }

        private void printTreeNode(org.example.query.DependencyQuery.DependencyNode node, String prefix, boolean isLast) {
            System.out.print(prefix);
            System.out.print(isLast ? "└── " : "├── ");
            System.out.printf("%s (%s)", node.symbol().name(), node.symbol().kind());
            if (node.isCycle()) {
                System.out.print(" [CYCLE]");
            }
            System.out.println();

            var children = node.children();
            for (int i = 0; i < children.size(); i++) {
                boolean childIsLast = (i == children.size() - 1);
                String childPrefix = prefix + (isLast ? "    " : "│   ");
                printTreeNode(children.get(i), childPrefix, childIsLast);
            }
        }

        private void printDepsFlat(org.example.query.DependencyQuery.DependencyResult result,
                                   String queryType, org.example.query.DependencyQuery query) {
            System.out.printf("%s %s (depth: %d)%n%n", queryType, result.rootSymbol().name(), depth);
            System.out.printf("%-40s %-12s %s%n", "NAME", "KIND", "FILE");
            System.out.println("-".repeat(80));

            var deps = query.flattenDependencies(result.tree());
            for (var dep : deps) {
                System.out.printf("%-40s %-12s %s%n",
                    truncate(dep.name(), 40),
                    dep.kind(),
                    truncate(dep.filePath(), 30));
            }

            System.out.println("-".repeat(80));
            System.out.printf("Total: %d %s%n", deps.size(),
                reverse ? "dependents" : "dependencies");
        }

        private void printDepsJson(org.example.query.DependencyQuery.DependencyResult result, String queryType) {
            System.out.println("{");
            System.out.printf("  \"symbol\": \"%s\",%n", escape(result.rootSymbol().name()));
            System.out.printf("  \"fqn\": \"%s\",%n", escape(result.rootSymbol().fullyQualifiedName()));
            System.out.printf("  \"queryType\": \"%s\",%n", reverse ? "dependents" : "dependencies");
            System.out.printf("  \"depth\": %d,%n", depth);
            System.out.printf("  \"total\": %d,%n", result.totalDependencies());
            System.out.print("  \"tree\": ");
            if (result.tree() != null) {
                System.out.println(result.tree().toJsonString().replace("\n", "\n  "));
            } else {
                System.out.println("null");
            }
            System.out.println("}");
        }

        private String truncate(String s, int max) {
            if (s == null) return "";
            return s.length() > max ? s.substring(0, max - 3) + "..." : s;
        }

        private String escape(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}
