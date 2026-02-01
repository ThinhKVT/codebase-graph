package org.example.cli;

import org.example.scip.storage.IndexMetadata;
import org.example.scip.storage.ScipIndexStorage;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.util.List;

/**
 * CLI command to list SCIP indices.
 */
@Command(name = "list-indices", description = "List stored SCIP indices.")
public class ListIndicesCommand implements Runnable {

    @Parameters(index = "0", arity = "0..1", description = "Project name to filter (optional)")
    private String projectName;

    @Option(names = {"--storage"}, description = "Path to SCIP index storage directory")
    private File storagePath;

    @Option(names = {"-a", "--all"}, description = "Show all indices including failed ones")
    private boolean showAll;

    @Option(names = {"--limit"}, description = "Maximum number of indices to show", defaultValue = "10")
    private int limit;

    @Override
    public void run() {
        ScipIndexStorage storage = storagePath != null 
            ? new ScipIndexStorage(storagePath.toPath())
            : new ScipIndexStorage();

        List<IndexMetadata> indices;
        if (projectName != null && !projectName.isBlank()) {
            indices = storage.listIndices(projectName);
            System.out.println("=== Indices for project: " + projectName + " ===");
        } else {
            indices = storage.listAllIndices();
            System.out.println("=== All Stored Indices ===");
        }

        if (!showAll) {
            indices = indices.stream()
                .filter(m -> !m.isFailed())
                .toList();
        }

        if (indices.isEmpty()) {
            System.out.println("No indices found.");
            return;
        }

        System.out.println();
        System.out.printf("%-35s %-12s %-8s %-8s %-8s %s%n",
            "ID", "STATUS", "DOCS", "SYMBOLS", "REFS", "TIME");
        System.out.println("-".repeat(90));

        int shown = 0;
        for (IndexMetadata meta : indices) {
            if (shown >= limit) {
                System.out.println("... and " + (indices.size() - limit) + " more");
                break;
            }

            String statusIcon = switch (meta.status()) {
                case CREATED -> "📝";
                case PARSED -> "📖";
                case MAPPED -> "🗺️";
                case STORED -> "✅";
                case FAILED -> "❌";
            };

            System.out.printf("%-35s %s %-10s %-8d %-8d %-8d %s%n",
                meta.id(),
                statusIcon,
                meta.status(),
                meta.documentCount(),
                meta.symbolCount(),
                meta.occurrenceCount(),
                meta.getFormattedTime()
            );
            shown++;
        }

        System.out.println();
        System.out.println("Storage: " + storage.getStorageRoot());
    }
}
