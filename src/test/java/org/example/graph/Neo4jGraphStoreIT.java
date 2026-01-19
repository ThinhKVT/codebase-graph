package org.example.graph;

import org.example.model.*;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Neo4jGraphStore using Testcontainers.
 */
@Testcontainers
class Neo4jGraphStoreIT {

    @Container
    static Neo4jContainer<?> neo4j = new Neo4jContainer<>("neo4j:5.15.0")
            .withoutAuthentication();

    private Neo4jGraphStore store;

    @BeforeEach
    void setUp() {
        store = new Neo4jGraphStore(neo4j.getBoltUrl(), "neo4j", "");
        store.connect();
        store.clearAll();
        store.createIndexes();
    }

    @AfterEach
    void tearDown() {
        if (store != null) store.close();
    }

    @Test
    void testConnection() {
        assertTrue(store.isConnected());
    }

    @Test
    void testSaveAndFindRepository() {
        Repository repo = Repository.of("repo1", "my-project", "/path/to/project");
        store.saveRepository(repo);

        var found = store.findRepositoryById("repo1");
        assertTrue(found.isPresent());
        assertEquals("my-project", found.get().name());
    }

    @Test
    void testSaveAndFindSymbol() {
        Symbol symbol = Symbol.builder()
                .id("org.example.MyClass")
                .name("MyClass")
                .fullyQualifiedName("org.example.MyClass")
                .kind(SymbolKind.CLASS)
                .filePath("/src/MyClass.java")
                .startLine(10)
                .endLine(50)
                .build();

        store.saveSymbol(symbol);

        var found = store.findSymbolById("org.example.MyClass");
        assertTrue(found.isPresent());
        assertEquals("MyClass", found.get().name());
        assertEquals(SymbolKind.CLASS, found.get().kind());
    }

    @Test
    void testSaveSymbolsBatch() {
        List<Symbol> symbols = List.of(
                Symbol.of("sym1", "Class1", SymbolKind.CLASS),
                Symbol.of("sym2", "Class2", SymbolKind.CLASS),
                Symbol.of("sym3", "method1", SymbolKind.METHOD)
        );

        store.saveSymbols(symbols);
        assertEquals(3, store.countSymbols());
    }

    @Test
    void testFindSymbolsByName() {
        store.saveSymbol(Symbol.of("sym1", "UserService", SymbolKind.CLASS));
        store.saveSymbol(Symbol.of("sym2", "UserService", SymbolKind.INTERFACE));

        List<Symbol> found = store.findSymbolsByName("UserService");
        assertEquals(2, found.size());
    }

    @Test
    void testSaveAndFindReferences() {
        store.saveSymbol(Symbol.of("classA", "ClassA", SymbolKind.CLASS));
        store.saveSymbol(Symbol.of("classB", "ClassB", SymbolKind.CLASS));

        Reference ref = Reference.of("classA", "classB", ReferenceKind.EXTENDS, "/src/ClassA.java", 5, 20);
        store.saveReference(ref);

        List<Reference> refs = store.findReferencesToSymbol("classB");
        assertEquals(1, refs.size());
        assertEquals("classA", refs.get(0).fromSymbolId());
    }

    @Test
    void testFindDependencies() {
        store.saveSymbol(Symbol.of("classA", "ClassA", SymbolKind.CLASS));
        store.saveSymbol(Symbol.of("classB", "ClassB", SymbolKind.CLASS));

        store.saveReference(Reference.of("classA", "classB", ReferenceKind.EXTENDS));

        List<Symbol> deps = store.findDependencies("classA");
        assertEquals(1, deps.size());
        assertEquals("ClassB", deps.get(0).name());
    }

    @Test
    void testFindTransitiveDependencies() {
        store.saveSymbol(Symbol.of("A", "A", SymbolKind.CLASS));
        store.saveSymbol(Symbol.of("B", "B", SymbolKind.CLASS));
        store.saveSymbol(Symbol.of("C", "C", SymbolKind.CLASS));

        store.saveReference(Reference.of("A", "B", ReferenceKind.EXTENDS));
        store.saveReference(Reference.of("B", "C", ReferenceKind.EXTENDS));

        List<Symbol> depth1 = store.findTransitiveDependencies("A", 1);
        assertEquals(1, depth1.size());

        List<Symbol> depth2 = store.findTransitiveDependencies("A", 2);
        assertEquals(2, depth2.size());
    }

    @Test
    void testCountSymbols() {
        assertEquals(0, store.countSymbols());
        store.saveSymbols(List.of(Symbol.of("sym1", "A", SymbolKind.CLASS), Symbol.of("sym2", "B", SymbolKind.CLASS)));
        assertEquals(2, store.countSymbols());
    }
}

