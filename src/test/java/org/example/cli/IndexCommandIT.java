package org.example.cli;

import org.example.graph.Neo4jGraphStore;
import org.example.model.Symbol;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import scip.Scip;

import java.io.*;
import java.nio.file.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for IndexCommand.
 */
@Testcontainers
class IndexCommandIT {

    @Container
    static Neo4jContainer<?> neo4j = new Neo4jContainer<>("neo4j:5.15.0")
            .withoutAuthentication();

    private Neo4jGraphStore store;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        store = new Neo4jGraphStore(neo4j.getBoltUrl(), "neo4j", "");
        store.connect();
        store.clearAll();
        store.createIndexes();

        // Create temp directory for test SCIP file
        tempDir = Files.createTempDirectory("index-test");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (store != null) store.close();
        // Clean up temp files
        if (tempDir != null) {
            Files.walk(tempDir)
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }

    @Test
    void testIndexWithScipFile() throws Exception {
        // Create a sample SCIP index file
        Path scipFile = createSampleScipFile();

        // Verify the file was created
        assertTrue(Files.exists(scipFile));

        // Parse and store manually (simulating what IndexCommand does)
        org.example.scip.ScipParser parser = new org.example.scip.ScipParser();
        Scip.Index index = parser.parse(scipFile);

        org.example.scip.ScipToGraphMapper mapper = new org.example.scip.ScipToGraphMapper();
        var result = mapper.map(index, tempDir.toString());

        // Store symbols
        store.saveSymbols(result.symbols());

        // Verify symbols were stored
        List<Symbol> symbols = store.findSymbolsByName("TestClass");
        assertEquals(1, symbols.size());
        assertEquals("TestClass", symbols.get(0).name());
    }

    @Test
    void testIndexCreatesRepository() throws Exception {
        Path scipFile = createSampleScipFile();

        org.example.scip.ScipParser parser = new org.example.scip.ScipParser();
        Scip.Index index = parser.parse(scipFile);

        org.example.scip.ScipToGraphMapper mapper = new org.example.scip.ScipToGraphMapper();
        var result = mapper.map(index, tempDir.toString());

        // Create and save repository
        org.example.model.Repository repo = org.example.model.Repository.builder()
            .id(tempDir.toString())
            .name("test-repo")
            .path(tempDir.toString())
            .language("java")
            .fileCount(result.sourceFiles().size())
            .symbolCount(result.symbols().size())
            .build();
        store.saveRepository(repo);

        // Verify repository was created
        var found = store.findRepositoryByPath(tempDir.toString());
        assertTrue(found.isPresent());
        assertEquals("test-repo", found.get().name());
    }

    private Path createSampleScipFile() throws IOException {
        // Create a minimal SCIP index
        Scip.Index index = Scip.Index.newBuilder()
            .setMetadata(Scip.Metadata.newBuilder()
                .setVersion(Scip.ProtocolVersion.UnspecifiedProtocolVersion)
                .setProjectRoot("file://" + tempDir)
                .build())
            .addDocuments(Scip.Document.newBuilder()
                .setRelativePath("src/TestClass.java")
                .setLanguage("java")
                .addSymbols(Scip.SymbolInformation.newBuilder()
                    .setSymbol("scip-java maven . . . com/example/TestClass#")
                    .setKind(Scip.SymbolInformation.Kind.Class)
                    .addDocumentation("A test class for integration testing")
                    .build())
                .addSymbols(Scip.SymbolInformation.newBuilder()
                    .setSymbol("scip-java maven . . . com/example/TestClass#doTest().")
                    .setKind(Scip.SymbolInformation.Kind.Method)
                    .build())
                .addOccurrences(Scip.Occurrence.newBuilder()
                    .setSymbol("scip-java maven . . . com/example/TestClass#")
                    .setSymbolRoles(1) // Definition
                    .addRange(5)
                    .addRange(0)
                    .build())
                .build())
            .build();

        // Write to file
        Path scipFile = tempDir.resolve("test-index.scip");
        try (OutputStream os = new FileOutputStream(scipFile.toFile())) {
            index.writeTo(os);
        }

        return scipFile;
    }
}

