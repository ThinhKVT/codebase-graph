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
        Scip.Index index = Scip.Index.newBuilder()
            .addDocuments(Scip.Document.newBuilder()
                .setRelativePath("src/Test.java")
                .setLanguage("java")
                .addOccurrences(Scip.Occurrence.newBuilder()
                    .setSymbol("scip-java maven . . . com/example/Foo#bar().")
                    .setSymbolRoles(0) // Reference
                    .addRange(10)  // line 10
                    .addRange(5)   // column 5
                    .build())
                .build())
            .build();

        var result = mapper.map(index, "/repo");

        assertEquals(1, result.references().size());
        Reference ref = result.references().get(0);
        assertEquals(ReferenceKind.REFERENCE, ref.kind());
        assertEquals(11, ref.line()); // 1-based
        assertEquals(6, ref.column()); // 1-based
    }

    @Test
    void testMapDefinitionOccurrence() {
        Scip.Index index = Scip.Index.newBuilder()
            .addDocuments(Scip.Document.newBuilder()
                .setRelativePath("src/Test.java")
                .addOccurrences(Scip.Occurrence.newBuilder()
                    .setSymbol("test#symbol")
                    .setSymbolRoles(1) // Definition
                    .build())
                .build())
            .build();

        var result = mapper.map(index, "/repo");

        assertEquals(1, result.references().size());
        assertEquals(ReferenceKind.DEFINITION, result.references().get(0).kind());
    }

    @Test
    void testMapImportOccurrence() {
        Scip.Index index = Scip.Index.newBuilder()
            .addDocuments(Scip.Document.newBuilder()
                .setRelativePath("src/Test.java")
                .addOccurrences(Scip.Occurrence.newBuilder()
                    .setSymbol("test#symbol")
                    .setSymbolRoles(2) // Import
                    .build())
                .build())
            .build();

        var result = mapper.map(index, "/repo");

        assertEquals(ReferenceKind.IMPORT, result.references().get(0).kind());
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

