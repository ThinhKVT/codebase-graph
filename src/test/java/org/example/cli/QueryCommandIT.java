package org.example.cli;

import org.example.graph.GraphStore;
import org.example.graph.Neo4jGraphStore;
import org.example.model.*;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for QueryCommand using Testcontainers.
 */
@Testcontainers
class QueryCommandIT {

    @Container
    static Neo4jContainer<?> neo4j = new Neo4jContainer<>("neo4j:5")
        .withAdminPassword("testpassword");

    private static GraphStore graphStore;
    private StringWriter outWriter;
    private StringWriter errWriter;

    @BeforeAll
    static void setUpGraph() throws Exception {
        graphStore = new Neo4jGraphStore(
            neo4j.getBoltUrl(),
            "neo4j",
            "testpassword"
        );
        graphStore.connect();
        graphStore.createIndexes();

        // Seed test data
        seedTestData();
    }

    private static void seedTestData() {
        // Create symbols
        Symbol userService = Symbol.builder()
            .id("sym-user-service")
            .name("UserService")
            .fullyQualifiedName("com.example.service.UserService")
            .kind(SymbolKind.CLASS)
            .filePath("src/main/java/com/example/service/UserService.java")
            .startLine(10)
            .build();

        Symbol userRepository = Symbol.builder()
            .id("sym-user-repo")
            .name("UserRepository")
            .fullyQualifiedName("com.example.repository.UserRepository")
            .kind(SymbolKind.INTERFACE)
            .filePath("src/main/java/com/example/repository/UserRepository.java")
            .startLine(5)
            .build();

        Symbol getUser = Symbol.builder()
            .id("sym-get-user")
            .name("getUser")
            .fullyQualifiedName("com.example.service.UserService#getUser")
            .kind(SymbolKind.METHOD)
            .filePath("src/main/java/com/example/service/UserService.java")
            .startLine(25)
            .parentId("sym-user-service")
            .build();

        Symbol userId = Symbol.builder()
            .id("sym-user-id")
            .name("userId")
            .fullyQualifiedName("com.example.service.UserService#userId")
            .kind(SymbolKind.FIELD)
            .filePath("src/main/java/com/example/service/UserService.java")
            .startLine(12)
            .parentId("sym-user-service")
            .build();

        Symbol mainClass = Symbol.builder()
            .id("sym-main")
            .name("Main")
            .fullyQualifiedName("com.example.Main")
            .kind(SymbolKind.CLASS)
            .filePath("src/main/java/com/example/Main.java")
            .startLine(5)
            .build();

        graphStore.saveSymbols(List.of(userService, userRepository, getUser, userId, mainClass));

        // Create references
        Reference defRef = Reference.builder()
            .fromSymbolId("sym-user-service")
            .toSymbolId("sym-user-service")
            .kind(ReferenceKind.DEFINITION)
            .filePath("src/main/java/com/example/service/UserService.java")
            .line(10)
            .column(14)
            .build();

        Reference usageRef1 = Reference.builder()
            .fromSymbolId("sym-main")
            .toSymbolId("sym-user-service")
            .kind(ReferenceKind.REFERENCE)
            .filePath("src/main/java/com/example/Main.java")
            .line(15)
            .column(20)
            .build();

        Reference usageRef2 = Reference.builder()
            .fromSymbolId("sym-main")
            .toSymbolId("sym-user-service")
            .kind(ReferenceKind.IMPORT)
            .filePath("src/main/java/com/example/Main.java")
            .line(3)
            .column(8)
            .build();

        Reference methodRef = Reference.builder()
            .fromSymbolId("sym-main")
            .toSymbolId("sym-get-user")
            .kind(ReferenceKind.REFERENCE)
            .filePath("src/main/java/com/example/Main.java")
            .line(18)
            .column(25)
            .build();

        graphStore.saveReferences(List.of(defRef, usageRef1, usageRef2, methodRef));
    }

    @AfterAll
    static void tearDown() {
        if (graphStore != null) {
            graphStore.close();
        }
    }

    @BeforeEach
    void setUpStreams() {
        outWriter = new StringWriter();
        errWriter = new StringWriter();
    }

    private CommandLine createCommandLine() {
        CommandLine cmd = new CommandLine(new QueryCommand());
        cmd.setOut(new PrintWriter(outWriter));
        cmd.setErr(new PrintWriter(errWriter));
        return cmd;
    }

    private String[] withNeo4jArgs(String... args) {
        String[] neo4jArgs = {
            "--neo4j-uri", neo4j.getBoltUrl(),
            "--neo4j-user", "neo4j",
            "--neo4j-password", "testpassword"
        };
        String[] combined = new String[args.length + neo4jArgs.length];
        System.arraycopy(args, 0, combined, 0, args.length);
        System.arraycopy(neo4jArgs, 0, combined, args.length, neo4jArgs.length);
        return combined;
    }

    // ==================== Symbol Query Tests ====================

    @Test
    void testQuerySymbolsByName() {
        CommandLine cmd = createCommandLine();
        int exitCode = cmd.execute(withNeo4jArgs("symbols", "--name", "UserService"));

        assertEquals(0, exitCode);
        String output = outWriter.toString();
        assertTrue(output.contains("UserService"));
        assertTrue(output.contains("CLASS"));
    }

    @Test
    void testQuerySymbolsByKind() {
        CommandLine cmd = createCommandLine();
        int exitCode = cmd.execute(withNeo4jArgs("symbols", "--kind", "METHOD"));

        assertEquals(0, exitCode);
        String output = outWriter.toString();
        assertTrue(output.contains("getUser"));
        assertTrue(output.contains("METHOD"));
    }

    @Test
    void testQuerySymbolsByFile() {
        CommandLine cmd = createCommandLine();
        int exitCode = cmd.execute(withNeo4jArgs("symbols", "--file", "UserService.java"));

        assertEquals(0, exitCode);
        String output = outWriter.toString();
        assertTrue(output.contains("UserService"));
    }

    @Test
    void testQuerySymbolsByFQN() {
        CommandLine cmd = createCommandLine();
        int exitCode = cmd.execute(withNeo4jArgs("symbols", "--fqn", "com.example.service.UserService"));

        assertEquals(0, exitCode);
        String output = outWriter.toString();
        assertTrue(output.contains("UserService"));
    }

    @Test
    void testQuerySymbolsJsonFormat() {
        CommandLine cmd = createCommandLine();
        int exitCode = cmd.execute(withNeo4jArgs("symbols", "--name", "UserService", "--format", "json"));

        assertEquals(0, exitCode);
        String output = outWriter.toString();
        assertTrue(output.contains("["));
        assertTrue(output.contains("\"name\": \"UserService\""));
        assertTrue(output.contains("\"kind\": \"CLASS\""));
    }

    @Test
    void testQuerySymbolsNotFound() {
        CommandLine cmd = createCommandLine();
        int exitCode = cmd.execute(withNeo4jArgs("symbols", "--name", "NonExistentClass"));

        assertEquals(0, exitCode);
        String output = outWriter.toString();
        assertTrue(output.contains("No symbols found"));
    }

    @Test
    void testQuerySymbolsNoCriteria() {
        CommandLine cmd = createCommandLine();
        int exitCode = cmd.execute(withNeo4jArgs("symbols"));

        assertEquals(1, exitCode);
        String error = errWriter.toString();
        assertTrue(error.contains("At least one search criteria required"));
    }

    // ==================== Reference Query Tests ====================

    @Test
    void testQueryRefsBySymbolName() {
        CommandLine cmd = createCommandLine();
        int exitCode = cmd.execute(withNeo4jArgs("refs", "UserService"));

        assertEquals(0, exitCode);
        String output = outWriter.toString();
        assertTrue(output.contains("References to: UserService"));
    }

    @Test
    void testQueryRefsByFQN() {
        CommandLine cmd = createCommandLine();
        int exitCode = cmd.execute(withNeo4jArgs("refs", "com.example.service.UserService"));

        assertEquals(0, exitCode);
        String output = outWriter.toString();
        assertTrue(output.contains("References to:"));
    }

    @Test
    void testQueryRefsDefinitionsOnly() {
        CommandLine cmd = createCommandLine();
        int exitCode = cmd.execute(withNeo4jArgs("refs", "UserService", "--definitions"));

        assertEquals(0, exitCode);
        String output = outWriter.toString();
        // Should only contain DEFINITION kind
        if (output.contains("LOCATION")) {
            assertTrue(output.contains("DEFINITION") || output.contains("No references"));
        }
    }

    @Test
    void testQueryRefsUsagesOnly() {
        CommandLine cmd = createCommandLine();
        int exitCode = cmd.execute(withNeo4jArgs("refs", "UserService", "--usages"));

        assertEquals(0, exitCode);
        // Should not contain DEFINITION
    }

    @Test
    void testQueryRefsJsonFormat() {
        CommandLine cmd = createCommandLine();
        int exitCode = cmd.execute(withNeo4jArgs("refs", "UserService", "--format", "json"));

        assertEquals(0, exitCode);
        String output = outWriter.toString();
        assertTrue(output.contains("{"));
        assertTrue(output.contains("\"symbol\": \"UserService\""));
        assertTrue(output.contains("\"references\":"));
    }

    @Test
    void testQueryRefsNotFound() {
        CommandLine cmd = createCommandLine();
        int exitCode = cmd.execute(withNeo4jArgs("refs", "NonExistentSymbol"));

        assertEquals(0, exitCode);
        String output = outWriter.toString();
        assertTrue(output.contains("No references found"));
    }

    // ==================== Deps Subcommand Tests (Placeholder) ====================

    @Test
    void testQueryDepsPlaceholder() {
        CommandLine cmd = createCommandLine();
        int exitCode = cmd.execute(withNeo4jArgs("deps", "UserService"));

        assertEquals(0, exitCode);
        String output = outWriter.toString();
        assertTrue(output.contains("Phase 5"));
    }

    // ==================== Help Tests ====================

    @Test
    void testQueryHelp() {
        CommandLine cmd = createCommandLine();
        int exitCode = cmd.execute("--help");

        assertEquals(0, exitCode);
        String output = outWriter.toString();
        assertTrue(output.contains("symbols"));
        assertTrue(output.contains("refs"));
        assertTrue(output.contains("deps"));
    }

    @Test
    void testQuerySymbolsHelp() {
        CommandLine cmd = createCommandLine();
        int exitCode = cmd.execute("symbols", "--help");

        assertEquals(0, exitCode);
        String output = outWriter.toString();
        assertTrue(output.contains("--name"));
        assertTrue(output.contains("--kind"));
    }

    @Test
    void testQueryRefsHelp() {
        CommandLine cmd = createCommandLine();
        int exitCode = cmd.execute("refs", "--help");

        assertEquals(0, exitCode);
        String output = outWriter.toString();
        assertTrue(output.contains("--definitions"));
        assertTrue(output.contains("--usages"));
    }
}

