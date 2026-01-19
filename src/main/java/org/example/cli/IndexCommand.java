package org.example.cli;

import org.example.graph.GraphStore;
import org.example.graph.GraphStoreException;
import org.example.graph.Neo4jGraphStore;
import org.example.model.Repository;
import org.example.scip.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import scip.Scip;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Properties;

/**
 * CLI command to index a repository.
 */
@Command(name = "index", description = "Index a Java repository to extract symbols and references.")
public class IndexCommand implements Runnable {

    @Parameters(index = "0", description = "Path to the repository to index")
    private File repositoryPath;

    @Option(names = {"-n", "--name"}, description = "Repository name (defaults to directory name)")
    private String repositoryName;

    @Option(names = {"--clean"}, description = "Clear existing data for this repository before indexing")
    private boolean clean;

    @Option(names = {"-v", "--verbose"}, description = "Show detailed progress")
    private boolean verbose;

    @Option(names = {"--skip-scip"}, description = "Skip scip execution, use existing .scip file")
    private boolean skipScip;

    @Option(names = {"--scip-file"}, description = "Path to existing .scip file (implies --skip-scip)")
    private File scipFile;

    @Option(names = {"-l", "--language"}, description = "Language to index (java, python, typescript). If omitted, detected from repo files.")
    private String language;

    @Override
    public void run() {
        try {
            // Validate repository path
            if (!repositoryPath.exists()) {
                System.err.println("Error: Repository path does not exist: " + repositoryPath);
                System.exit(1);
            }
            if (!repositoryPath.isDirectory()) {
                System.err.println("Error: Path is not a directory: " + repositoryPath);
                System.exit(1);
            }

            String repoName = repositoryName != null ? repositoryName : repositoryPath.getName();
            Path repoPath = repositoryPath.toPath().toAbsolutePath();

            System.out.println("=== Indexing Repository ===");
            System.out.println("Name: " + repoName);
            System.out.println("Path: " + repoPath);
            System.out.println();

            // Select runner (by option or auto-detect)
            ScipRunner runner = org.example.scip.ScipRunnerFactory.create(language, repoPath);

            // Step 1: Check prerequisites (uses runner)
            checkPrerequisites(runner);

            // Step 2: Run scip or use existing file
            Path indexFile = runScip(runner, repoPath);

            // Step 3: Parse SCIP index
            Scip.Index index = parseScipIndex(indexFile);

            // Step 4: Map to domain model
            ScipToGraphMapper.MappingResult mappingResult = mapToGraph(index, repoPath.toString());

            // Step 5: Store in Neo4j
            storeInNeo4j(repoName, repoPath.toString(), mappingResult);

            System.out.println();
            System.out.println("✅ Indexing complete!");
            System.out.println("   Documents: " + mappingResult.documentsProcessed());
            System.out.println("   Symbols: " + mappingResult.symbolsProcessed());
            System.out.println("   References: " + mappingResult.occurrencesProcessed());

        } catch (ScipException e) {
            System.err.println("❌ SCIP Error: " + e.getMessage());
            if (verbose) e.printStackTrace();
            System.exit(1);
        } catch (GraphStoreException e) {
            System.err.println("❌ Database Error: " + e.getMessage());
            System.err.println("   Hint: Is Neo4j running? Try: docker-compose up -d");
            if (verbose) e.printStackTrace();
            System.exit(1);
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            if (verbose) e.printStackTrace();
            System.exit(1);
        }
    }

    private void checkPrerequisites(ScipRunner runner) throws ScipException {
        System.out.print("Checking prerequisites... ");

        // Check scip tool if we need to run it
        if (!skipScip && scipFile == null) {
            if (!runner.isToolInstalled()) {
                System.out.println("❌");
                throw ScipException.notInstalled();
            }
            if (verbose) {
                System.out.println(runner.getToolVersion());
            }
        }

        // Check if it's a valid project for the selected language
        if (!runner.isValidProject()) {
            System.out.println("⚠️");
            System.out.println("Warning: No language-specific build files found. The indexer may fail.");
        } else if (verbose) {
            System.out.println("Build tool: " + runner.detectBuildTool());
        }

        System.out.println("✅");
    }

    private Path runScip(ScipRunner runner, Path repoPath) throws ScipException {
        // Use existing file if specified
        if (scipFile != null) {
            System.out.println("Using existing SCIP file: " + scipFile);
            return scipFile.toPath();
        }

        if (skipScip) {
            Path defaultFile = repoPath.resolve("index.scip");
            System.out.println("Using existing SCIP file: " + defaultFile);
            return defaultFile;
        }

        System.out.print("Running scip... ");
        Path indexFile = runner.runIndex();
        System.out.println("✅");

        if (verbose) {
            System.out.println("   Generated: " + indexFile);
        }

        return indexFile;
    }

    private Scip.Index parseScipIndex(Path indexFile) throws ScipException {
        System.out.print("Parsing SCIP index... ");
        ScipParser parser = new ScipParser();
        Scip.Index index = parser.parse(indexFile);
        System.out.println("✅");

        if (verbose) {
            System.out.println("   Documents: " + index.getDocumentsCount());
            System.out.println("   Symbols: " + parser.countSymbols(index));
            System.out.println("   Occurrences: " + parser.countOccurrences(index));
        }

        return index;
    }

    private ScipToGraphMapper.MappingResult mapToGraph(Scip.Index index, String repoPath) {
        System.out.print("Mapping to graph model... ");
        ScipToGraphMapper mapper = new ScipToGraphMapper();
        var result = mapper.map(index, repoPath);
        System.out.println("✅");
        return result;
    }

    private void storeInNeo4j(String repoName, String repoPath, ScipToGraphMapper.MappingResult result) {
        System.out.print("Storing in Neo4j... ");

        Properties props = loadProperties();
        String uri = props.getProperty("neo4j.uri", "bolt://localhost:7687");
        String username = props.getProperty("neo4j.username", "neo4j");
        String password = props.getProperty("neo4j.password", "codebase123");

        try (GraphStore store = new Neo4jGraphStore(uri, username, password)) {
            store.connect();

            // Create indexes on first run
            store.createIndexes();

            // Clean existing data if requested
            String repoId = repoPath; // Use path as ID
            if (clean) {
                store.deleteRepository(repoId);
            }

            // Save repository
            Repository repo = Repository.builder()
                .id(repoId)
                .name(repoName)
                .path(repoPath)
                .language(language == null ? "java" : language)
                .lastIndexedAt(Instant.now())
                .fileCount(result.sourceFiles().size())
                .symbolCount(result.symbols().size())
                .build();
            store.saveRepository(repo);

            // Save source files
            for (var sourceFile : result.sourceFiles()) {
                store.saveSourceFile(sourceFile, repoId);
            }

            // Save symbols in batches
            if (!result.symbols().isEmpty()) {
                store.saveSymbols(result.symbols());
            }

            // Save references in batches
            if (!result.references().isEmpty()) {
                store.saveReferences(result.references());
            }

            System.out.println("✅");

            if (verbose) {
                System.out.println("   Repository: " + repoName);
                System.out.println("   Files: " + result.sourceFiles().size());
                System.out.println("   Symbols: " + result.symbols().size());
                System.out.println("   References: " + result.references().size());
            }
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
