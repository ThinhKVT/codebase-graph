package org.example.scip;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class LanguageDetectorTest {

    @Test
    void detectJava(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");
        String lang = LanguageDetector.detectLanguage(tempDir);
        assertEquals("java", lang);
    }

    @Test
    void detectPython(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("pyproject.toml"), "[tool.poetry]\nname = \"sample\"");
        String lang = LanguageDetector.detectLanguage(tempDir);
        assertEquals("python", lang);
    }

    @Test
    void detectTypescript(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("package.json"), "{ \"name\": \"sample\" }");
        String lang = LanguageDetector.detectLanguage(tempDir);
        assertEquals("typescript", lang);
    }
}

