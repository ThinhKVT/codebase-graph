package org.example.query;

import org.example.graph.GraphStore;
import org.example.model.Reference;
import org.example.model.ReferenceKind;
import org.example.model.Symbol;
import org.example.model.SymbolKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for ReferenceQuery.
 */
class ReferenceQueryTest {

    private GraphStore mockStore;
    private SymbolQuery mockSymbolQuery;
    private ReferenceQuery query;

    @BeforeEach
    void setUp() {
        mockStore = mock(GraphStore.class);
        mockSymbolQuery = mock(SymbolQuery.class);
        query = new ReferenceQuery(mockStore, mockSymbolQuery);
    }

    @Test
    void testFindReferencesTo() {
        Reference ref = Reference.of("from1", "target", ReferenceKind.REFERENCE, "src/Test.java", 10, 5);
        when(mockStore.findReferencesToSymbol("target")).thenReturn(List.of(ref));

        List<Reference> results = query.findReferencesTo("target");

        assertEquals(1, results.size());
        assertEquals("target", results.get(0).toSymbolId());
        verify(mockStore).findReferencesToSymbol("target");
    }

    @Test
    void testFindReferencesFrom() {
        Reference ref = Reference.of("source", "dep1", ReferenceKind.IMPORT);
        when(mockStore.findReferencesFromSymbol("source")).thenReturn(List.of(ref));

        List<Reference> results = query.findReferencesFrom("source");

        assertEquals(1, results.size());
        assertEquals("source", results.get(0).fromSymbolId());
    }

    @Test
    void testFindReferencesByFQN() {
        Symbol symbol = Symbol.builder()
            .id("sym1")
            .name("UserService")
            .fullyQualifiedName("com.example.UserService")
            .kind(SymbolKind.CLASS)
            .build();
        Reference ref = Reference.of("caller", "sym1", ReferenceKind.REFERENCE, "src/Main.java", 20, 10);

        when(mockSymbolQuery.findByFQN("com.example.UserService")).thenReturn(Optional.of(symbol));
        when(mockStore.findReferencesToSymbol("sym1")).thenReturn(List.of(ref));

        List<ReferenceQuery.ReferenceResult> results = query.findReferences("com.example.UserService");

        assertEquals(1, results.size());
        assertEquals("sym1", results.get(0).targetSymbol().id());
        assertEquals(ReferenceKind.REFERENCE, results.get(0).kind());
    }

    @Test
    void testFindReferencesByName() {
        Symbol symbol = Symbol.builder()
            .id("sym1")
            .name("MyClass")
            .kind(SymbolKind.CLASS)
            .build();
        Reference ref = Reference.of("caller", "sym1", ReferenceKind.REFERENCE, "src/Test.java", 15, 8);

        when(mockSymbolQuery.findByFQN("MyClass")).thenReturn(Optional.empty());
        when(mockSymbolQuery.findByName("MyClass")).thenReturn(List.of(symbol));
        when(mockStore.findReferencesToSymbol("sym1")).thenReturn(List.of(ref));

        List<ReferenceQuery.ReferenceResult> results = query.findReferences("MyClass");

        assertEquals(1, results.size());
    }

    @Test
    void testFindReferencesByNameWithPatternFallback() {
        Symbol symbol = Symbol.builder()
            .id("sym1")
            .name("FooService")
            .kind(SymbolKind.CLASS)
            .build();

        when(mockSymbolQuery.findByFQN("Foo")).thenReturn(Optional.empty());
        when(mockSymbolQuery.findByName("Foo")).thenReturn(List.of());
        when(mockSymbolQuery.findByPattern("*Foo*")).thenReturn(List.of(symbol));
        when(mockStore.findReferencesToSymbol("sym1")).thenReturn(List.of());

        List<ReferenceQuery.ReferenceResult> results = query.findReferences("Foo");

        // Should still work even if no references found
        assertTrue(results.isEmpty());
        verify(mockSymbolQuery).findByPattern("*Foo*");
    }

    @Test
    void testFindDefinitions() {
        Symbol symbol = Symbol.builder()
            .id("sym1")
            .name("method")
            .kind(SymbolKind.METHOD)
            .build();
        Reference defRef = Reference.of("sym1", "sym1", ReferenceKind.DEFINITION, "src/Class.java", 10, 5);
        Reference callRef = Reference.of("caller", "sym1", ReferenceKind.REFERENCE, "src/Other.java", 20, 10);

        when(mockSymbolQuery.findByFQN("method")).thenReturn(Optional.of(symbol));
        when(mockStore.findReferencesToSymbol("sym1")).thenReturn(List.of(defRef, callRef));

        List<ReferenceQuery.ReferenceResult> results = query.findDefinitions("method");

        assertEquals(1, results.size());
        assertEquals(ReferenceKind.DEFINITION, results.get(0).kind());
    }

    @Test
    void testFindUsages() {
        Symbol symbol = Symbol.builder()
            .id("sym1")
            .name("method")
            .kind(SymbolKind.METHOD)
            .build();
        Reference defRef = Reference.of("sym1", "sym1", ReferenceKind.DEFINITION, "src/Class.java", 10, 5);
        Reference callRef = Reference.of("caller", "sym1", ReferenceKind.REFERENCE, "src/Other.java", 20, 10);

        when(mockSymbolQuery.findByFQN("method")).thenReturn(Optional.of(symbol));
        when(mockStore.findReferencesToSymbol("sym1")).thenReturn(List.of(defRef, callRef));

        List<ReferenceQuery.ReferenceResult> results = query.findUsages("method");

        assertEquals(1, results.size());
        assertEquals(ReferenceKind.REFERENCE, results.get(0).kind());
    }

    @Test
    void testFindImports() {
        Symbol symbol = Symbol.builder()
            .id("sym1")
            .name("ArrayList")
            .kind(SymbolKind.CLASS)
            .build();
        Reference importRef = Reference.of("file1", "sym1", ReferenceKind.IMPORT, "src/Main.java", 3, 1);

        when(mockSymbolQuery.findByFQN("ArrayList")).thenReturn(Optional.of(symbol));
        when(mockStore.findReferencesToSymbol("sym1")).thenReturn(List.of(importRef));

        List<ReferenceQuery.ReferenceResult> results = query.findImports("ArrayList");

        assertEquals(1, results.size());
        assertEquals(ReferenceKind.IMPORT, results.get(0).kind());
    }

    @Test
    void testCountReferences() {
        Reference ref1 = Reference.of("a", "target", ReferenceKind.REFERENCE);
        Reference ref2 = Reference.of("b", "target", ReferenceKind.REFERENCE);
        Reference ref3 = Reference.of("c", "target", ReferenceKind.IMPORT);
        when(mockStore.findReferencesToSymbol("target")).thenReturn(List.of(ref1, ref2, ref3));

        int count = query.countReferences("target");

        assertEquals(3, count);
    }

    @Test
    void testReferenceResultLocation() {
        Symbol symbol = Symbol.builder().id("s").name("test").kind(SymbolKind.METHOD).build();
        ReferenceQuery.ReferenceResult result = new ReferenceQuery.ReferenceResult(
            symbol, ReferenceKind.REFERENCE, "src/Main.java", 25, 10, null);

        assertEquals("src/Main.java:25:10", result.location());
    }

    @Test
    void testReferenceResultToDisplayString() {
        Symbol symbol = Symbol.builder().id("s").name("test").kind(SymbolKind.METHOD).build();
        ReferenceQuery.ReferenceResult result = new ReferenceQuery.ReferenceResult(
            symbol, ReferenceKind.REFERENCE, "src/Main.java", 25, 10, "    userService.test();");

        String display = result.toDisplayString();

        assertTrue(display.contains("src/Main.java:25:10"));
        assertTrue(display.contains("REFERENCE"));
        assertTrue(display.contains("userService.test()"));
    }

    @Test
    void testFindReferencesWithContext() {
        Symbol symbol = Symbol.builder()
            .id("sym1")
            .name("process")
            .kind(SymbolKind.METHOD)
            .filePath("src/Service.java")
            .build();
        Reference ref = Reference.of("caller", "sym1", ReferenceKind.REFERENCE, "src/Main.java", 30, 15);

        when(mockStore.findReferencesToSymbol("sym1")).thenReturn(List.of(ref));

        List<ReferenceQuery.ReferenceResult> results = query.findReferencesWithContext(symbol);

        assertEquals(1, results.size());
        assertEquals(symbol, results.get(0).targetSymbol());
        assertEquals("src/Main.java", results.get(0).filePath());
        assertEquals(30, results.get(0).line());
        assertEquals(15, results.get(0).column());
    }
}

