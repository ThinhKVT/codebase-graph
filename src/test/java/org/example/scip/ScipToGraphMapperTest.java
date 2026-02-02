package org.example.scip;

import org.example.model.*;
import org.junit.jupiter.api.Test;
import scip.Scip;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ScipToGraphMapper.
 */
class ScipToGraphMapperTest {

    private final ScipToGraphMapper mapper = new ScipToGraphMapper();

    @Test
    void testMapEmptyIndex() {
        Scip.Index index = Scip.Index.newBuilder().build();

        var result = mapper.map(index, "/repo");

        assertEquals(0, result.documentsProcessed());
        assertEquals(0, result.symbolsProcessed());
        assertTrue(result.symbols().isEmpty());
        assertTrue(result.references().isEmpty());
    }

    @Test
    void testMapSingleClass() {
        Scip.Index index = Scip.Index.newBuilder()
            .addDocuments(Scip.Document.newBuilder()
                .setRelativePath("src/com/example/MyClass.java")
                .setLanguage("java")
                .addSymbols(Scip.SymbolInformation.newBuilder()
                    .setSymbol("scip-java maven . . . com/example/MyClass#")
                    .setKind(Scip.SymbolInformation.Kind.Class)
                    .addDocumentation("A sample class")
                    .build())
                .build())
            .build();

        var result = mapper.map(index, "/repo");

        assertEquals(1, result.documentsProcessed());
        assertEquals(1, result.symbolsProcessed());
        assertEquals(1, result.sourceFiles().size());
        assertEquals(1, result.symbols().size());

        Symbol symbol = result.symbols().get(0);
        assertEquals("MyClass", symbol.name());
        assertEquals(SymbolKind.CLASS, symbol.kind());
        assertEquals("src/com/example/MyClass.java", symbol.filePath());
    }

    @Test
    void testMapMethod() {
        Scip.Index index = Scip.Index.newBuilder()
            .addDocuments(Scip.Document.newBuilder()
                .setRelativePath("src/Example.java")
                .setLanguage("java")
                .addSymbols(Scip.SymbolInformation.newBuilder()
                    .setSymbol("scip-java maven . . . com/example/Example#doSomething().")
                    .setKind(Scip.SymbolInformation.Kind.Method)
                    .build())
                .build())
            .build();

        var result = mapper.map(index, "/repo");

        assertEquals(1, result.symbols().size());
        Symbol symbol = result.symbols().get(0);
        assertEquals("doSomething", symbol.name());
        assertEquals(SymbolKind.METHOD, symbol.kind());
    }

    @Test
    void testMapField() {
        Scip.Index index = Scip.Index.newBuilder()
            .addDocuments(Scip.Document.newBuilder()
                .setRelativePath("src/Test.java")
                .addSymbols(Scip.SymbolInformation.newBuilder()
                    .setSymbol("com/example/Test#myField.")
                    .setKind(Scip.SymbolInformation.Kind.Field)
                    .build())
                .build())
            .build();

        var result = mapper.map(index, "/repo");

        assertEquals(1, result.symbols().size());
        assertEquals("myField", result.symbols().get(0).name());
        assertEquals(SymbolKind.FIELD, result.symbols().get(0).kind());
    }

    @Test
    void testMapOccurrence() {
        // Need to define symbols first, then add occurrences
        // The mapper now requires: 1) enclosing symbol (definition), 2) target symbol exists
        Scip.Index index = Scip.Index.newBuilder()
            .addDocuments(Scip.Document.newBuilder()
                .setRelativePath("src/Test.java")
                .setLanguage("java")
                // Define the symbols
                .addSymbols(Scip.SymbolInformation.newBuilder()
                    .setSymbol("scip-java maven . . . com/example/Test#main().")
                    .setKind(Scip.SymbolInformation.Kind.Method)
                    .build())
                .addSymbols(Scip.SymbolInformation.newBuilder()
                    .setSymbol("scip-java maven . . . com/example/Foo#bar().")
                    .setKind(Scip.SymbolInformation.Kind.Method)
                    .build())
                // Add occurrences: first a definition (to set enclosing symbol), then a reference
                .addOccurrences(Scip.Occurrence.newBuilder()
                    .setSymbol("scip-java maven . . . com/example/Test#main().")
                    .setSymbolRoles(1) // Definition - sets enclosing symbol
                    .addRange(5)
                    .addRange(0)
                    .build())
                .addOccurrences(Scip.Occurrence.newBuilder()
                    .setSymbol("scip-java maven . . . com/example/Foo#bar().")
                    .setSymbolRoles(0) // Reference
                    .addRange(10)  // line 10
                    .addRange(5)   // column 5
                    .build())
                .build())
            .build();

        var result = mapper.map(index, "/repo");

        // Filter for CALL references (strategy may add CONTAINS relationships)
        var callRefs = result.references().stream()
            .filter(r -> r.kind() == ReferenceKind.CALL)
            .toList();
        
        assertEquals(1, callRefs.size());
        Reference ref = callRefs.get(0);
        // Method reference should be CALL (not generic REFERENCE)
        assertEquals(ReferenceKind.CALL, ref.kind());
        assertEquals(11, ref.line()); // 1-based
        assertEquals(6, ref.column()); // 1-based
        assertEquals("scip-java maven . . . com/example/Test#main().", ref.fromSymbolId());
        assertEquals("scip-java maven . . . com/example/Foo#bar().", ref.toSymbolId());
    }

    @Test
    void testMapDefinitionOccurrence() {
        // Definition occurrences are now used to track enclosing symbol, not create references
        // Test that symbols are properly tracked
        Scip.Index index = Scip.Index.newBuilder()
            .addDocuments(Scip.Document.newBuilder()
                .setRelativePath("src/Test.java")
                .addSymbols(Scip.SymbolInformation.newBuilder()
                    .setSymbol("test#classA")
                    .setKind(Scip.SymbolInformation.Kind.Class)
                    .build())
                .addSymbols(Scip.SymbolInformation.newBuilder()
                    .setSymbol("test#classB")
                    .setKind(Scip.SymbolInformation.Kind.Class)
                    .build())
                .addOccurrences(Scip.Occurrence.newBuilder()
                    .setSymbol("test#classA")
                    .setSymbolRoles(1) // Definition
                    .build())
                .addOccurrences(Scip.Occurrence.newBuilder()
                    .setSymbol("test#classB")
                    .setSymbolRoles(0) // Reference from classA to classB
                    .build())
                .build())
            .build();

        var result = mapper.map(index, "/repo");

        // Should have 1 reference: classA -> classB
        assertEquals(1, result.references().size());
        assertEquals("test#classA", result.references().get(0).fromSymbolId());
        assertEquals("test#classB", result.references().get(0).toSymbolId());
        // Class reference should be TYPE_REF (not generic REFERENCE)
        assertEquals(ReferenceKind.TYPE_REF, result.references().get(0).kind());
    }

    @Test
    void testMapImportOccurrence() {
        // Import occurrences need enclosing symbol and target symbol to be defined
        Scip.Index index = Scip.Index.newBuilder()
            .addDocuments(Scip.Document.newBuilder()
                .setRelativePath("src/Test.java")
                .addSymbols(Scip.SymbolInformation.newBuilder()
                    .setSymbol("test#MyClass")
                    .setKind(Scip.SymbolInformation.Kind.Class)
                    .build())
                .addSymbols(Scip.SymbolInformation.newBuilder()
                    .setSymbol("java/util/List#")
                    .setKind(Scip.SymbolInformation.Kind.Interface)
                    .build())
                .addOccurrences(Scip.Occurrence.newBuilder()
                    .setSymbol("test#MyClass")
                    .setSymbolRoles(1) // Definition - sets enclosing symbol
                    .build())
                .addOccurrences(Scip.Occurrence.newBuilder()
                    .setSymbol("java/util/List#")
                    .setSymbolRoles(2) // Import
                    .build())
                .build())
            .build();

        var result = mapper.map(index, "/repo");

        assertEquals(1, result.references().size());
        assertEquals(ReferenceKind.IMPORT, result.references().get(0).kind());
        assertEquals("test#MyClass", result.references().get(0).fromSymbolId());
        assertEquals("java/util/List#", result.references().get(0).toSymbolId());
    }

    @Test
    void testMapMultipleDocuments() {
        Scip.Index index = Scip.Index.newBuilder()
            .addDocuments(Scip.Document.newBuilder()
                .setRelativePath("src/A.java")
                .addSymbols(Scip.SymbolInformation.newBuilder()
                    .setSymbol("A#")
                    .setKind(Scip.SymbolInformation.Kind.Class)
                    .build())
                .build())
            .addDocuments(Scip.Document.newBuilder()
                .setRelativePath("src/B.java")
                .addSymbols(Scip.SymbolInformation.newBuilder()
                    .setSymbol("B#")
                    .setKind(Scip.SymbolInformation.Kind.Class)
                    .build())
                .build())
            .build();

        var result = mapper.map(index, "/repo");

        assertEquals(2, result.documentsProcessed());
        assertEquals(2, result.symbols().size());
        assertEquals(2, result.sourceFiles().size());
    }

    @Test
    void testSymbolKindMapping() {
        // Test all symbol kinds are mapped correctly
        Scip.Index index = Scip.Index.newBuilder()
            .addDocuments(Scip.Document.newBuilder()
                .setRelativePath("src/Test.java")
                .addSymbols(Scip.SymbolInformation.newBuilder()
                    .setSymbol("pkg#")
                    .setKind(Scip.SymbolInformation.Kind.Package)
                    .build())
                .addSymbols(Scip.SymbolInformation.newBuilder()
                    .setSymbol("iface#")
                    .setKind(Scip.SymbolInformation.Kind.Interface)
                    .build())
                .addSymbols(Scip.SymbolInformation.newBuilder()
                    .setSymbol("enum#")
                    .setKind(Scip.SymbolInformation.Kind.Enum)
                    .build())
                .addSymbols(Scip.SymbolInformation.newBuilder()
                    .setSymbol("ctor#")
                    .setKind(Scip.SymbolInformation.Kind.Constructor)
                    .build())
                .build())
            .build();

        var result = mapper.map(index, "/repo");

        assertEquals(4, result.symbols().size());
        assertTrue(result.symbols().stream().anyMatch(s -> s.kind() == SymbolKind.PACKAGE));
        assertTrue(result.symbols().stream().anyMatch(s -> s.kind() == SymbolKind.INTERFACE));
        assertTrue(result.symbols().stream().anyMatch(s -> s.kind() == SymbolKind.ENUM));
        assertTrue(result.symbols().stream().anyMatch(s -> s.kind() == SymbolKind.CONSTRUCTOR));
    }
}
