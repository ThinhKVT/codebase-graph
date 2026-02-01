package org.example.cli;

import org.example.agent.CodeSearchAgent;
import org.example.config.OllamaConfig;
import org.example.config.QdrantConfig;
import org.example.embedding.OllamaEmbeddingService;
import org.example.graph.Neo4jGraphStore;
import org.example.search.ResultSynthesizer;
import org.example.vector.QdrantVectorStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * CLI command for agent-powered code search.
 */
@Command(
    name = "agent",
    description = "Agent-powered intelligent code search (Plan → Execute → Synthesize → Output)",
    mixinStandardHelpOptions = true
)
public class AgentSearchCommand implements Callable<Integer> {

    @Parameters(
        index = "0",
        description = "Search query (natural language)",
        arity = "1..*"
    )
    private List<String> queryParts;

    @Option(
        names = {"-n", "--limit"},
        description = "Maximum number of results",
        defaultValue = "10"
    )
    private int limit;

    @Option(
        names = {"--neo4j-uri"},
        description = "Neo4j connection URI",
        defaultValue = "bolt://localhost:7687"
    )
    private String neo4jUri;

    @Option(
        names = {"--neo4j-user"},
        description = "Neo4j username",
        defaultValue = "neo4j"
    )
    private String neo4jUser;

    @Option(
        names = {"--neo4j-password"},
        description = "Neo4j password",
        defaultValue = "codebase123"
    )
    private String neo4jPassword;

    @Option(
        names = {"--qdrant-host"},
        description = "Qdrant host",
        defaultValue = "localhost"
    )
    private String qdrantHost;

    @Option(
        names = {"--qdrant-port"},
        description = "Qdrant gRPC port",
        defaultValue = "6334"
    )
    private int qdrantPort;

    @Option(
        names = {"--ollama-host"},
        description = "Ollama host",
        defaultValue = "localhost"
    )
    private String ollamaHost;

    @Option(
        names = {"--ollama-port"},
        description = "Ollama port",
        defaultValue = "11434"
    )
    private int ollamaPort;

    @Option(
        names = {"--collection"},
        description = "Qdrant collection name",
        defaultValue = "code_symbols"
    )
    private String collectionName;

    @Option(
        names = {"--verbose", "-v"},
        description = "Show execution log and detailed output"
    )
    private boolean verbose;

    @Option(
        names = {"--show-plan"},
        description = "Show the execution plan"
    )
    private boolean showPlan;

    @Override
    public Integer call() throws Exception {
        String query = String.join(" ", queryParts);
        
        // Initialize services
        OllamaConfig ollamaConfig = OllamaConfig.builder()
            .host(ollamaHost)
            .port(ollamaPort)
            .build();
        
        QdrantConfig qdrantConfig = QdrantConfig.builder()
            .host(qdrantHost)
            .grpcPort(qdrantPort)
            .collectionName(collectionName)
            .build();

        OllamaEmbeddingService embeddingService = new OllamaEmbeddingService(ollamaConfig);
        QdrantVectorStore vectorStore = new QdrantVectorStore(qdrantConfig);
        
        // Connect to services
        vectorStore.connect();
        
        Neo4jGraphStore graphStore = new Neo4jGraphStore(neo4jUri, neo4jUser, neo4jPassword);
        graphStore.connect();

        // Create agent
        CodeSearchAgent agent = new CodeSearchAgent(
            embeddingService, vectorStore, graphStore, collectionName);

        // Execute search
        System.out.println("Agent Search: \"" + query + "\"");
        System.out.println();

        CodeSearchAgent.AgentResult result = agent.search(query, limit);

        // Show plan if requested
        if (showPlan) {
            System.out.println("=== EXECUTION PLAN ===");
            System.out.println("Intent: " + result.plan().getIntent());
            System.out.println("Strategy: " + result.plan().getStrategy());
            System.out.println("Reasoning: " + result.plan().getReasoning());
            System.out.println("Steps:");
            for (var step : result.plan().getSteps()) {
                System.out.println("  " + step.order() + ". " + step.type() + ": " + step.description());
            }
            System.out.println();
        }

        // Show execution log if verbose
        if (verbose) {
            System.out.println("=== EXECUTION LOG ===");
            for (String log : result.executionLog()) {
                System.out.println(log);
            }
            System.out.println();
        }

        // Show summary
        System.out.println(result.summary());
        System.out.println("Execution time: " + result.executionTimeMs() + "ms");
        System.out.println();

        // Show results
        if (result.output().results().isEmpty()) {
            System.out.println("No results found.");
            System.out.println("Make sure you have embedded symbols using: embed <repository>");
            return 0;
        }

        for (int i = 0; i < result.output().results().size(); i++) {
            ResultSynthesizer.SynthesizedResult r = result.output().results().get(i);
            System.out.printf("%d. [%d%%] %s %s%n", 
                i + 1, Math.round(r.combinedScore() * 100), r.kind(), r.name());
            
            String location = r.filePath();
            if (r.startLine() != null && r.startLine() > 0) {
                location += ":" + r.startLine();
            }
            System.out.printf("   File: %s%n", location);
            
            if (r.signature() != null && !r.signature().isBlank()) {
                String sig = r.signature();
                if (sig.length() > 80) sig = sig.substring(0, 77) + "...";
                System.out.printf("   Signature: %s%n", sig);
            }
            
            if (verbose) {
                if (r.documentation() != null && !r.documentation().isBlank()) {
                    String doc = r.documentation();
                    if (doc.length() > 100) doc = doc.substring(0, 97) + "...";
                    System.out.printf("   Doc: %s%n", doc);
                }
                if (r.callers() != null && !r.callers().isEmpty()) {
                    System.out.printf("   Callers: %d%n", r.callers().size());
                }
                if (r.callees() != null && !r.callees().isEmpty()) {
                    System.out.printf("   Callees: %d%n", r.callees().size());
                }
            }
            System.out.println();
        }

        // Cleanup
        vectorStore.close();
        graphStore.close();

        return 0;
    }
}
