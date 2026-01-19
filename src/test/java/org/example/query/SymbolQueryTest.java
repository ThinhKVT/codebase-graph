package org.example.query;

import org.example.graph.GraphStore;
import org.example.model.Symbol;
import org.example.model.SymbolKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for SymbolQuery.
 */
class SymbolQueryTest {

    private GraphStore mockStore;
    private SymbolQuery query;

    @BeforeEach
    void setUp() {
        mockStore = mock(GraphStore.class);
        query = new SymbolQuery(mockStore);
    }

    @Test
    void testFindByName() {
        Symbol symbol = Symbol.builder()
            .id("sym1")
            .name("MyClass")
            .kind(SymbolKind.CLASS)
            .build();
        when(mockStore.findSymbolsByName("MyClass")).thenReturn(List.of(symbol));

        List<Symbol> results = query.findByName("MyClass");

        assertEquals(1, results.size());
        assertEquals("MyClass", results.get(0).name());
        verify(mockStore).findSymbolsByName("MyClass");
    }

    @Test
    void testFindByFQN() {
        Symbol symbol = Symbol.builder()
            .id("sym1")
            .name("MyClass")
            .fullyQualifiedName("com.example.MyClass")
            .kind(SymbolKind.CLASS)
            .build();
        when(mockStore.findSymbolByFQN("com.example.MyClass")).thenReturn(Optional.of(symbol));

        Optional<Symbol> result = query.findByFQN("com.example.MyClass");

        assertTrue(result.isPresent());
        assertEquals("com.example.MyClass", result.get().fullyQualifiedName());
    }

    @Test
    void testFindByFQN_notFound() {
        when(mockStore.findSymbolByFQN("nonexistent")).thenReturn(Optional.empty());

        Optional<Symbol> result = query.findByFQN("nonexistent");

        assertFalse(result.isPresent());
    }

    @Test
    void testFindByPattern() {
        Symbol s1 = Symbol.builder().id("s1").name("UserService").kind(SymbolKind.CLASS).build();
        Symbol s2 = Symbol.builder().id("s2").name("UserRepository").kind(SymbolKind.CLASS).build();
        when(mockStore.findSymbolsByNamePattern("User.*")).thenReturn(List.of(s1, s2));

        List<Symbol> results = query.findByPattern("User*");

        assertEquals(2, results.size());
        verify(mockStore).findSymbolsByNamePattern("User.*");
    }

    @Test
    void testFindByKind() {
        Symbol s1 = Symbol.builder().id("s1").name("method1").kind(SymbolKind.METHOD).build();
        Symbol s2 = Symbol.builder().id("s2").name("method2").kind(SymbolKind.METHOD).build();
        when(mockStore.findSymbolsByKind(SymbolKind.METHOD)).thenReturn(List.of(s1, s2));

        List<Symbol> results = query.findByKind(SymbolKind.METHOD);

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(s -> s.kind() == SymbolKind.METHOD));
    }

    @Test
    void testFindByFile() {
        Symbol s1 = Symbol.builder()
            .id("s1")
            .name("MyClass")
            .kind(SymbolKind.CLASS)
            .filePath("src/MyClass.java")
            .build();
        when(mockStore.findSymbolsByFile("src/MyClass.java")).thenReturn(List.of(s1));

        List<Symbol> results = query.findByFile("src/MyClass.java");

        assertEquals(1, results.size());
        assertEquals("src/MyClass.java", results.get(0).filePath());
    }

    @Test
    void testFindWithCriteria_byName() {
        Symbol symbol = Symbol.builder().id("s1").name("Foo").kind(SymbolKind.CLASS).build();
        when(mockStore.findSymbolsByName("Foo")).thenReturn(List.of(symbol));

        var criteria = SymbolQuery.SymbolSearchCriteria.builder()
            .name("Foo")
            .build();

        List<Symbol> results = query.find(criteria);

        assertEquals(1, results.size());
    }

    @Test
    void testFindWithCriteria_byNameWithWildcard() {
        Symbol symbol = Symbol.builder().id("s1").name("FooService").kind(SymbolKind.CLASS).build();
        when(mockStore.findSymbolsByNamePattern("Foo.*")).thenReturn(List.of(symbol));

        var criteria = SymbolQuery.SymbolSearchCriteria.builder()
            .name("Foo*")
            .build();

        List<Symbol> results = query.find(criteria);

        assertEquals(1, results.size());
    }

    @Test
    void testFindWithCriteria_byFQN() {
        Symbol symbol = Symbol.builder()
            .id("s1")
            .name("Bar")
            .fullyQualifiedName("com.example.Bar")
            .kind(SymbolKind.CLASS)
            .build();
        when(mockStore.findSymbolByFQN("com.example.Bar")).thenReturn(Optional.of(symbol));

        var criteria = SymbolQuery.SymbolSearchCriteria.builder()
            .fullyQualifiedName("com.example.Bar")
            .build();

        List<Symbol> results = query.find(criteria);

        assertEquals(1, results.size());
        assertEquals("com.example.Bar", results.get(0).fullyQualifiedName());
    }

    @Test
    void testFindWithCriteria_filterByKind() {
        Symbol classSymbol = Symbol.builder().id("s1").name("Foo").kind(SymbolKind.CLASS).build();
        Symbol methodSymbol = Symbol.builder().id("s2").name("Foo").kind(SymbolKind.METHOD).build();
        when(mockStore.findSymbolsByName("Foo")).thenReturn(List.of(classSymbol, methodSymbol));

        var criteria = SymbolQuery.SymbolSearchCriteria.builder()
            .name("Foo")
            .kind(SymbolKind.CLASS)
            .build();

        List<Symbol> results = query.find(criteria);

        assertEquals(1, results.size());
        assertEquals(SymbolKind.CLASS, results.get(0).kind());
    }

    @Test
    void testFindWithCriteria_filterByPackage() {
        Symbol s1 = Symbol.builder()
            .id("s1")
            .name("Service")
            .fullyQualifiedName("com.example.service.Service")
            .kind(SymbolKind.CLASS)
            .build();
        Symbol s2 = Symbol.builder()
            .id("s2")
            .name("Service")
            .fullyQualifiedName("org.other.Service")
            .kind(SymbolKind.CLASS)
            .build();
        when(mockStore.findSymbolsByName("Service")).thenReturn(List.of(s1, s2));

        var criteria = SymbolQuery.SymbolSearchCriteria.builder()
            .name("Service")
            .packageName("com.example")
            .build();

        List<Symbol> results = query.find(criteria);

        assertEquals(1, results.size());
        assertTrue(results.get(0).fullyQualifiedName().startsWith("com.example"));
    }

    @Test
    void testFindWithNoCriteria_returnsEmpty() {
        var criteria = SymbolQuery.SymbolSearchCriteria.builder().build();

        List<Symbol> results = query.find(criteria);

        assertTrue(results.isEmpty());
    }

    @Test
    void testGlobToRegex() {
        // Test through findByPattern which uses globToRegex internally
        when(mockStore.findSymbolsByNamePattern(".*Service")).thenReturn(List.of());
        query.findByPattern("*Service");
        verify(mockStore).findSymbolsByNamePattern(".*Service");

        when(mockStore.findSymbolsByNamePattern("User.")).thenReturn(List.of());
        query.findByPattern("User?");
        verify(mockStore).findSymbolsByNamePattern("User.");
    }
}

