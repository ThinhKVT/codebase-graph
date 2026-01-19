package org.example.cli;

import org.example.api.ApiServer;
import org.example.graph.GraphStore;
import org.example.graph.Neo4jGraphStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * CLI command to start the REST API server.
 */
@Command(name = "serve", description = "Start the REST API server")
public class ServeCommand implements Callable<Integer> {

    @Option(names = {"-p", "--port"}, description = "Port to run the server on (default: ${DEFAULT-VALUE})", defaultValue = "8080")
    private int port;

    @Option(names = {"--neo4j-uri"}, description = "Neo4j connection URI", defaultValue = "bolt://localhost:7687")
    private String neo4jUri;

    @Option(names = {"--neo4j-user"}, description = "Neo4j username", defaultValue = "neo4j")
    private String neo4jUser;

    @Option(names = {"--neo4j-password"}, description = "Neo4j password", defaultValue = "password")
    private String neo4jPassword;

    @Override
    public Integer call() {
        System.out.println("Starting Codebase Knowledge Graph API server...");

        // Initialize graph store
        GraphStore graphStore = new Neo4jGraphStore(neo4jUri, neo4jUser, neo4jPassword);

        try {
            graphStore.connect();
            System.out.println("Connected to Neo4j at " + neo4jUri);

            // Create and start API server
            ApiServer server = new ApiServer(graphStore, port);
            server.start();

            System.out.println();
            System.out.println("API server is running on http://localhost:" + port);
            System.out.println("Press Ctrl+C to stop the server");
            System.out.println();
            System.out.println("Available endpoints:");
            System.out.println("  GET /health                      - Health check");
            System.out.println("  GET /stats                       - Graph statistics");
            System.out.println("  GET /symbols                     - List/search symbols");
            System.out.println("  GET /symbols?name=<name>         - Search by name");
            System.out.println("  GET /symbols?kind=<kind>         - Filter by kind");
            System.out.println("  GET /symbols?fqn=<fqn>           - Get by fully qualified name");
            System.out.println("  GET /symbols/{id}                - Get symbol by ID");
            System.out.println("  GET /symbols/{id}/references     - Get references to symbol");
            System.out.println("  GET /symbols/{id}/dependencies   - Get symbol dependencies");
            System.out.println("  GET /symbols/{id}/dependents     - Get symbols depending on this");

            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nShutting down...");
                server.stop();
                graphStore.close();
                System.out.println("Server stopped.");
            }));

            // Keep the main thread alive
            Thread.currentThread().join();

            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            graphStore.close();
            return 1;
        }
    }
}

