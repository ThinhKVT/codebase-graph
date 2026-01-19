package org.example.scip;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import scip.Scip;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ScipParser.
 */
class ScipParserTest {

    private final ScipParser parser = new ScipParser();

    @Test
    void testParseNonExistentFile() {
        Path nonExistent = Path.of("/non/existent/file.scip");
        assertThrows(ScipException.class, () -> parser.parse(nonExistent));
    }

    @Test
    void testParseValidScipFile(@TempDir Path tempDir) throws Exception {
        // Create a minimal SCIP index
        Scip.Index index = Scip.Index.newBuilder()
            .setMetadata(Scip.Metadata.newBuilder()
                .setVersion(Scip.ProtocolVersion.UnspecifiedProtocolVersion)
                .build())
            .addDocuments(Scip.Document.newBuilder()
                .setRelativePath("src/Main.java")
                .setLanguage("java")
                .addSymbols(Scip.SymbolInformation.newBuilder()
                    .setSymbol("scip-java maven . . . com/example/Main#")
                    .setKind(Scip.SymbolInformation.Kind.Class)
                    .build())
                .build())
            .build();

        // Write to file
        Path scipFile = tempDir.resolve("test.scip");
        try (OutputStream os = new FileOutputStream(scipFile.toFile())) {
            index.writeTo(os);
        }

        // Parse
        Scip.Index parsed = parser.parse(scipFile);

        assertEquals(1, parsed.getDocumentsCount());
        assertEquals("src/Main.java", parsed.getDocuments(0).getRelativePath());
    }

    @Test
    void testCountSymbols() {
        Scip.Index index = Scip.Index.newBuilder()
            .addDocuments(Scip.Document.newBuilder()
                .setRelativePath("src/A.java")
                .addSymbols(Scip.SymbolInformation.newBuilder()
                    .setSymbol("A#")
                    .setKind(Scip.SymbolInformation.Kind.Class)
                    .build())
                .addSymbols(Scip.SymbolInformation.newBuilder()
                    .setSymbol("A#method().")
                    .setKind(Scip.SymbolInformation.Kind.Method)
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

        assertEquals(3, parser.countSymbols(index));
    }

    @Test
    void testCountOccurrences() {
        Scip.Index index = Scip.Index.newBuilder()
            .addDocuments(Scip.Document.newBuilder()
                .setRelativePath("src/A.java")
                .addOccurrences(Scip.Occurrence.newBuilder()
                    .setSymbol("A#")
                    .setSymbolRoles(1)
                    .build())
                .addOccurrences(Scip.Occurrence.newBuilder()
                    .setSymbol("B#")
                    .setSymbolRoles(0)
                    .build())
                .build())
            .build();

        assertEquals(2, parser.countOccurrences(index));
    }

    @Test
    void testGetDocuments() {
        Scip.Index index = Scip.Index.newBuilder()
            .addDocuments(Scip.Document.newBuilder()
                .setRelativePath("src/A.java")
                .build())
            .addDocuments(Scip.Document.newBuilder()
                .setRelativePath("src/B.java")
                .build())
            .build();

        assertEquals(2, parser.getDocuments(index).size());
    }
}

