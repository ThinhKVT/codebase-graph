package org.example.cli;

import org.example.graph.GraphStore;
import org.example.graph.GraphStoreException;
import org.example.graph.Neo4jGraphStore;
import org.example.model.LanguageSupport;
import org.example.model.Repository;
import org.example.scip.*;
import org.example.scip.storage.IndexMetadata;
import org.example.scip.storage.IndexStatus;
import org.example.scip.storage.ScipIndexStorage;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import scip.Scip;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * CLI command to index a repository.
 */
@Command(name = "index", description = "Index a repository to extract symbols and references.")
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

    @Option(names = {"-l", "--language"}, 
            description = "Language to index (java, python, go, typescript). If omitted, detected from repo files.")
    private String language;

    @Option(names = {"--storage"}, description = "Path to SCIP index storage directory")
    private File storagePath;

    @Option(names = {"--neo4j-uri"}, description = "Neo4j connection URI", defaultValue = "bolt://localhost:7687")
    private String neo4jUri;

    @Option(names = {"--neo4j-user"}, description = "Neo4j username", defaultValue = "neo4j")
    private String neo4jUser;

    @Option(names = {"--neo4j-password"}, description = "Neo4j password", defaultValue = "password")
    private String neo4jPassword;

    // Storage and metadata tracking
    private ScipIndexStorage storage;
    private IndexMetadata metadata;

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

            // Initialize storage
            initializeStorage();

            // Detect language
            LanguageSupport lang = detectLanguage(repoPath);

            System.out.println("=== Indexing Repository ===");
            System.out.println("Name: " + repoName);
            System.out.println("Path: " + repoPath);
            System.out.println("Language: " + lang.getLanguageId());
            System.out.println("Storage: " + storage.getStorageRoot());
            System.out.println("Neo4j: " + neo4jUri);
            System.out.println();

            // Create metadata for this indexing session
            metadata = storage.createMetadata(repoName, repoPath.toString(), lang);
            if (verbose) {
                System.out.println("Index ID: " + metadata.id());
            }

            // Select runner (by option or auto-detect)
            ScipRunner runner = ScipRunnerFactory.create(language, repoPath);

            // Step 1: Check prerequisites
            checkPrerequisites(runner);

            // Step 2: Run scip or use existing file
            Path indexFile = runScip(runner, repoPath, lang);

            // Step 3: Parse SCIP index
            Scip.Index index = parseScipIndex(indexFile);

            // Step 4: Map to domain model (using language-specific strategy)
            ScipToGraphMapper.MappingResult mappingResult = mapToGraph(index, repoPath.toString(), lang);

            // Step 5: Store in Neo4j
            storeInNeo4j(repoName, repoPath.toString(), mappingResult, lang);

            // Final status
            System.out.println();
            System.out.println("✅ Indexing complete!");
            System.out.println("   Index ID: " + metadata.id());
            System.out.println("   Documents: " + mappingResult.documentsProcessed());
            System.out.println("   Symbols: " + mappingResult.symbolsProcessed());
            System.out.println("   References: " + mappingResult.occurrencesProcessed());

        } catch (ScipException e) {
            handleError("SCIP Error", e);
        } catch (GraphStoreException e) {
            handleError("Database Error", e);
            System.err.println("   Hint: Is Neo4j running? Try: docker-compose up -d");
        } catch (Exception e) {
            handleError("Error", e);
        }
    }

    private void initializeStorage() {
        if (storagePath != null) {
            storage = new ScipIndexStorage(storagePath.toPath());
        } else {
            storage = new ScipIndexStorage();
        }
    }

    private LanguageSupport detectLanguage(Path repoPath) {
        if (language != null && !language.isBlank()) {
            return LanguageSupport.fromString(language);
        }
        String detected = LanguageDetector.detectLanguage(repoPath);
        return LanguageSupport.fromString(detected);
    }

    private void handleError(String type, Exception e) {
        System.err.println("❌ " + type + ": " + e.getMessage());
        if (metadata != null) {
            metadata = metadata.withError(e.getMessage());
            storage.saveMetadata(metadata);
        }
        if (verbose) {
            e.printStackTrace();
        }
        System.exit(1);
    }

    private void checkPrerequisites(ScipRunner runner) throws ScipException {
        System.out.print("Checking prerequisites... ");

        // Check scip tool if we need to run it
        if (!skipScip && scipFile == null) {
            if (!runner.isToolInstalled()) {
                System.out.println("❌");
                throw ScipException.notInstalled();
            }
            String version = runner.getToolVersion();
            metadata = metadata.withIndexerVersion(version);
            if (verbose) {
                System.out.println(version);
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

    private Path runScip(ScipRunner runner, Path repoPath, LanguageSupport lang) throws ScipException {
        // Use existing file if specified
        if (scipFile != null) {
            System.out.println("Using existing SCIP file: " + scipFile);
            // Update metadata to point to the existing file
            metadata = new IndexMetadata(
                metadata.id(), metadata.projectName(), metadata.projectPath(),
                lang, metadata.indexedAt(), scipFile.toPath(),
                0, 0, 0, 0, metadata.indexerVersion(),
                IndexStatus.CREATED, null
            );
            storage.saveMetadata(metadata);
            return scipFile.toPath();
        }

        if (skipScip) {
            Path defaultFile = repoPath.resolve("index.scip");
            System.out.println("Using existing SCIP file: " + defaultFile);
            metadata = new IndexMetadata(
                metadata.id(), metadata.projectName(), metadata.projectPath(),
                lang, metadata.indexedAt(), defaultFile,
                0, 0, 0, 0, metadata.indexerVersion(),
                IndexStatus.CREATED, null
            );
            storage.saveMetadata(metadata);
            return defaultFile;
        }

        // Generate path in storage and run indexer
        Path indexFile = metadata.scipFilePath();
        System.out.print("Running " + lang.getIndexerTool() + "... ");
        
        // Run the indexer with output to storage path
        runner.runIndex(indexFile);
        
        // Update metadata with file size
        long fileSize = 0;
        try {
            fileSize = Files.size(indexFile);
        } catch (Exception e) {
            // Ignore
        }
        
        metadata = metadata.withFileSize(fileSize).withStatus(IndexStatus.CREATED);
        storage.saveMetadata(metadata);
        
        System.out.println("✅");

        if (verbose) {
            System.out.println("   Generated: " + indexFile);
            System.out.println("   Size: " + formatSize(fileSize));
        }

        return indexFile;
    }

    private Scip.Index parseScipIndex(Path indexFile) throws ScipException {
        System.out.print("Parsing SCIP index... ");
        ScipParser parser = new ScipParser();
        Scip.Index index = parser.parse(indexFile);
        
        // Update metadata with counts
        int docs = index.getDocumentsCount();
        int symbols = parser.countSymbols(index);
        int occurrences = parser.countOccurrences(index);
        
        metadata = metadata.withCounts(docs, symbols, occurrences)
                          .withStatus(IndexStatus.PARSED);
        storage.saveMetadata(metadata);
        
        System.out.println("✅");

        if (verbose) {
            System.out.println("   Documents: " + docs);
            System.out.println("   Symbols: " + symbols);
            System.out.println("   Occurrences: " + occurrences);
        }

        return index;
    }

    private ScipToGraphMapper.MappingResult mapToGraph(Scip.Index index, String repoPath, 
                                                        LanguageSupport lang) {
        System.out.print("Mapping to graph model... ");
        
        // Use language-specific mapper
        ScipToGraphMapper mapper = ScipToGraphMapper.forLanguage(lang);
        var result = mapper.map(index, repoPath);
        
        // Update status
        metadata = metadata.withStatus(IndexStatus.MAPPED);
        storage.saveMetadata(metadata);
        
        System.out.println("✅");
        return result;
    }

    private void storeInNeo4j(String repoName, String repoPath, 
                             ScipToGraphMapper.MappingResult result,
                             LanguageSupport lang) {
        System.out.print("Storing in Neo4j... ");

        try (GraphStore store = new Neo4jGraphStore(neo4jUri, neo4jUser, neo4jPassword)) {
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
                .language(lang.getLanguageId())
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

            // Update final status
            metadata = metadata.withStatus(IndexStatus.STORED);
            storage.saveMetadata(metadata);

            System.out.println("✅");

            if (verbose) {
                System.out.println("   Repository: " + repoName);
                System.out.println("   Files: " + result.sourceFiles().size());
                System.out.println("   Symbols: " + result.symbols().size());
                System.out.println("   References: " + result.references().size());
            }
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
