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
 * Tests for DependencyQuery.
 */
class DependencyQueryTest {

    private GraphStore mockStore;
    private SymbolQuery mockSymbolQuery;
    private DependencyQuery query;

    @BeforeEach
    void setUp() {
        mockStore = mock(GraphStore.class);
        mockSymbolQuery = mock(SymbolQuery.class);
        query = new DependencyQuery(mockStore, mockSymbolQuery);
    }

    @Test
    void testFindDirectDependencies() {
        Symbol dep1 = Symbol.builder().id("dep1").name("Dependency1").kind(SymbolKind.CLASS).build();
        Symbol dep2 = Symbol.builder().id("dep2").name("Dependency2").kind(SymbolKind.INTERFACE).build();
        when(mockStore.findDependencies("sym1")).thenReturn(List.of(dep1, dep2));

        List<Symbol> deps = query.findDirectDependencies("sym1");

        assertEquals(2, deps.size());
        verify(mockStore).findDependencies("sym1");
    }

    @Test
    void testFindTransitiveDependencies() {
        Symbol dep1 = Symbol.builder().id("dep1").name("Level1").kind(SymbolKind.CLASS).build();
        Symbol dep2 = Symbol.builder().id("dep2").name("Level2").kind(SymbolKind.CLASS).build();
        when(mockStore.findTransitiveDependencies("sym1", 3)).thenReturn(List.of(dep1, dep2));

        List<Symbol> deps = query.findTransitiveDependencies("sym1", 3);

        assertEquals(2, deps.size());
        verify(mockStore).findTransitiveDependencies("sym1", 3);
    }

    @Test
    void testFindTransitiveDependencies_zeroDepth() {
        List<Symbol> deps = query.findTransitiveDependencies("sym1", 0);

        assertTrue(deps.isEmpty());
        verify(mockStore, never()).findTransitiveDependencies(any(), anyInt());
    }

    @Test
    void testFindDirectDependents() {
        Symbol dependent = Symbol.builder().id("d1").name("Consumer").kind(SymbolKind.CLASS).build();
        when(mockStore.findDependents("sym1")).thenReturn(List.of(dependent));

        List<Symbol> dependents = query.findDirectDependents("sym1");

        assertEquals(1, dependents.size());
        assertEquals("Consumer", dependents.get(0).name());
    }

    @Test
    void testFindTransitiveDependents() {
        Symbol d1 = Symbol.builder().id("d1").name("Consumer1").kind(SymbolKind.CLASS).build();
        when(mockStore.findTransitiveDependents("sym1", 2)).thenReturn(List.of(d1));

        List<Symbol> dependents = query.findTransitiveDependents("sym1", 2);

        assertEquals(1, dependents.size());
    }

    @Test
    void testFindDependenciesByFQN() {
        Symbol root = Symbol.builder()
            .id("root")
            .name("UserService")
            .fullyQualifiedName("com.example.UserService")
            .kind(SymbolKind.CLASS)
            .build();
        Symbol dep = Symbol.builder()
            .id("dep1")
            .name("UserRepository")
            .kind(SymbolKind.INTERFACE)
            .build();

        when(mockSymbolQuery.findByFQN("com.example.UserService")).thenReturn(Optional.of(root));
        when(mockStore.findDependencies("root")).thenReturn(List.of(dep));

        DependencyQuery.DependencyResult result = query.findDependencies("com.example.UserService", 1);

        assertFalse(result.isEmpty());
        assertEquals("UserService", result.rootSymbol().name());
        assertEquals(1, result.totalDependencies());
    }

    @Test
    void testFindDependenciesByName() {
        Symbol root = Symbol.builder()
            .id("root")
            .name("MyService")
            .kind(SymbolKind.CLASS)
            .build();

        when(mockSymbolQuery.findByFQN("MyService")).thenReturn(Optional.empty());
        when(mockSymbolQuery.findByName("MyService")).thenReturn(List.of(root));
        when(mockStore.findDependencies("root")).thenReturn(List.of());

        DependencyQuery.DependencyResult result = query.findDependencies("MyService", 1);

        assertFalse(result.isEmpty());
        assertEquals("MyService", result.rootSymbol().name());
    }

    @Test
    void testFindDependencies_symbolNotFound() {
        when(mockSymbolQuery.findByFQN("NonExistent")).thenReturn(Optional.empty());
        when(mockSymbolQuery.findByName("NonExistent")).thenReturn(List.of());

        DependencyQuery.DependencyResult result = query.findDependencies("NonExistent", 1);

        assertTrue(result.isEmpty());
    }

    @Test
    void testFindDependents_byFQN() {
        Symbol root = Symbol.builder()
            .id("root")
            .name("BaseClass")
            .fullyQualifiedName("com.example.BaseClass")
            .kind(SymbolKind.CLASS)
            .build();
        Symbol dependent = Symbol.builder()
            .id("child")
            .name("ChildClass")
            .kind(SymbolKind.CLASS)
            .build();

        when(mockSymbolQuery.findByFQN("com.example.BaseClass")).thenReturn(Optional.of(root));
        when(mockStore.findDependents("root")).thenReturn(List.of(dependent));

        DependencyQuery.DependencyResult result = query.findDependents("com.example.BaseClass", 1);

        assertFalse(result.isEmpty());
        assertEquals(1, result.totalDependencies());
    }

    @Test
    void testDependencyTreeWithDepth() {
        Symbol root = Symbol.builder().id("root").name("Root").kind(SymbolKind.CLASS).build();
        Symbol level1 = Symbol.builder().id("l1").name("Level1").kind(SymbolKind.CLASS).build();
        Symbol level2 = Symbol.builder().id("l2").name("Level2").kind(SymbolKind.CLASS).build();

        when(mockSymbolQuery.findByFQN("Root")).thenReturn(Optional.of(root));
        when(mockStore.findDependencies("root")).thenReturn(List.of(level1));
        when(mockStore.findDependencies("l1")).thenReturn(List.of(level2));
        when(mockStore.findDependencies("l2")).thenReturn(List.of());

        DependencyQuery.DependencyResult result = query.findDependencies("Root", 2);

        assertFalse(result.isEmpty());
        assertEquals(2, result.totalDependencies()); // level1 + level2
        assertEquals(1, result.tree().children().size()); // level1
        assertEquals(1, result.tree().children().get(0).children().size()); // level2
    }

    @Test
    void testDependencyTreeCycleDetection() {
        Symbol a = Symbol.builder().id("a").name("A").kind(SymbolKind.CLASS).build();
        Symbol b = Symbol.builder().id("b").name("B").kind(SymbolKind.CLASS).build();

        when(mockSymbolQuery.findByFQN("A")).thenReturn(Optional.of(a));
        // A -> B -> A (cycle)
        when(mockStore.findDependencies("a")).thenReturn(List.of(b));
        when(mockStore.findDependencies("b")).thenReturn(List.of(a));

        DependencyQuery.DependencyResult result = query.findDependencies("A", 3);

        assertFalse(result.isEmpty());
        // Should detect cycle and not infinite loop
        var bNode = result.tree().children().get(0);
        assertEquals("B", bNode.symbol().name());
        // A appears again but marked as cycle
        if (!bNode.children().isEmpty()) {
            assertTrue(bNode.children().get(0).isCycle());
        }
    }

    @Test
    void testFlattenDependencies() {
        Symbol root = Symbol.builder().id("root").name("Root").kind(SymbolKind.CLASS).build();
        Symbol dep1 = Symbol.builder().id("d1").name("Dep1").kind(SymbolKind.CLASS).build();
        Symbol dep2 = Symbol.builder().id("d2").name("Dep2").kind(SymbolKind.CLASS).build();

        when(mockSymbolQuery.findByFQN("Root")).thenReturn(Optional.of(root));
        when(mockStore.findDependencies("root")).thenReturn(List.of(dep1, dep2));
        when(mockStore.findDependencies("d1")).thenReturn(List.of());
        when(mockStore.findDependencies("d2")).thenReturn(List.of());

        DependencyQuery.DependencyResult result = query.findDependencies("Root", 1);
        List<Symbol> flat = query.flattenDependencies(result.tree());

        assertEquals(2, flat.size());
        assertTrue(flat.stream().anyMatch(s -> s.name().equals("Dep1")));
        assertTrue(flat.stream().anyMatch(s -> s.name().equals("Dep2")));
    }

    @Test
    void testDependencyNodeToTreeString() {
        Symbol root = Symbol.builder().id("root").name("Root").kind(SymbolKind.CLASS).build();
        Symbol child = Symbol.builder().id("child").name("Child").kind(SymbolKind.INTERFACE).build();

        DependencyQuery.DependencyNode childNode = new DependencyQuery.DependencyNode(child, List.of(), false);
        DependencyQuery.DependencyNode rootNode = new DependencyQuery.DependencyNode(root, List.of(childNode), false);

        String tree = rootNode.toTreeString();

        assertTrue(tree.contains("Root"));
        assertTrue(tree.contains("Child"));
        assertTrue(tree.contains("CLASS"));
        assertTrue(tree.contains("INTERFACE"));
    }

    @Test
    void testDependencyNodeToJsonString() {
        Symbol root = Symbol.builder()
            .id("root")
            .name("Root")
            .fullyQualifiedName("com.example.Root")
            .kind(SymbolKind.CLASS)
            .build();

        DependencyQuery.DependencyNode node = new DependencyQuery.DependencyNode(root, List.of(), false);
        String json = node.toJsonString();

        assertTrue(json.contains("\"name\": \"Root\""));
        assertTrue(json.contains("\"fqn\": \"com.example.Root\""));
        assertTrue(json.contains("\"kind\": \"CLASS\""));
        assertTrue(json.contains("\"dependencies\": []"));
    }

    @Test
    void testDependencyResultTotalDependencies() {
        Symbol root = Symbol.builder().id("root").name("Root").kind(SymbolKind.CLASS).build();
        Symbol d1 = Symbol.builder().id("d1").name("D1").kind(SymbolKind.CLASS).build();
        Symbol d2 = Symbol.builder().id("d2").name("D2").kind(SymbolKind.CLASS).build();
        Symbol d3 = Symbol.builder().id("d3").name("D3").kind(SymbolKind.CLASS).build();

        DependencyQuery.DependencyNode n3 = new DependencyQuery.DependencyNode(d3, List.of(), false);
        DependencyQuery.DependencyNode n2 = new DependencyQuery.DependencyNode(d2, List.of(n3), false);
        DependencyQuery.DependencyNode n1 = new DependencyQuery.DependencyNode(d1, List.of(), false);
        DependencyQuery.DependencyNode rootNode = new DependencyQuery.DependencyNode(root, List.of(n1, n2), false);

        DependencyQuery.DependencyResult result = new DependencyQuery.DependencyResult(root, rootNode, 2);

        assertEquals(3, result.totalDependencies()); // d1, d2, d3
    }

    @Test
    void testEmptyResult() {
        DependencyQuery.DependencyResult empty = DependencyQuery.DependencyResult.empty("unknown");

        assertTrue(empty.isEmpty());
        assertEquals(0, empty.totalDependencies());
    }
}

