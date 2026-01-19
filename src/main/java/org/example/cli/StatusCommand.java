package org.example.cli;

import org.example.graph.GraphStoreException;
import org.example.graph.Neo4jGraphStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Properties;

/**
 * CLI command to check system status.
 */
@Command(name = "status", description = "Check system status: Neo4j connection, scip-java, and indexed repositories.")
public class StatusCommand implements Runnable {

    @Option(names = {"-v", "--verbose"}, description = "Show detailed status information")
    private boolean verbose;

    @Override
    public void run() {
        System.out.println("=== Codebase Knowledge Graph Status ===\n");
        checkNeo4jConnection();
        checkScipJavaInstallation();
        System.out.println();
    }

    private void checkNeo4jConnection() {
        System.out.print("Neo4j Server: ");
        try {
            Properties props = loadProperties();
            String uri = props.getProperty("neo4j.uri", "bolt://localhost:7687");
            String username = props.getProperty("neo4j.username", "neo4j");
            String password = props.getProperty("neo4j.password", "codebase123");

            try (var store = new Neo4jGraphStore(uri, username, password)) {
                store.connect();
                if (store.isConnected()) {
                    System.out.println("✅ Connected (" + uri + ")");
                    if (verbose) {
                        System.out.println("   Repositories: " + store.findAllRepositories().size());
                        System.out.println("   Symbols: " + store.countSymbols());
                        System.out.println("   References: " + store.countReferences());
                    }
                }
            }
        } catch (GraphStoreException e) {
            System.out.println("❌ Not connected");
            System.out.println("   Hint: Run 'docker-compose up -d' to start Neo4j");
        } catch (Exception e) {
            System.out.println("❌ Error: " + e.getMessage());
        }
    }

    private void checkScipJavaInstallation() {
        System.out.print("scip-java: ");
        try {
            ProcessBuilder pb = new ProcessBuilder("scip-java", "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("✅ Installed (" + output.toString().trim() + ")");
            } else {
                System.out.println("⚠️  Found but returned error");
            }
        } catch (IOException e) {
            System.out.println("❌ Not installed");
            System.out.println("   Hint: Install with 'cs install scip-java'");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("❌ Check interrupted");
        }
    }

    private Properties loadProperties() {
        Properties props = new Properties();
        try (var is = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (is != null) props.load(is);
        } catch (IOException e) { /* Use defaults */ }
        return props;
    }
}

