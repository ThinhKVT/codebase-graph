package org.example.scip.storage;

import org.example.model.LanguageSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ScipIndexStorage.
 */
class ScipIndexStorageTest {

    @TempDir
    Path tempDir;

    private ScipIndexStorage storage;

    @BeforeEach
    void setUp() {
        storage = new ScipIndexStorage(tempDir);
    }

    @Test
    void testStorageDirectoriesCreated() {
        assertTrue(Files.exists(storage.getIndicesDir()));
        assertTrue(Files.exists(storage.getMetadataDir()));
    }

    @Test
    void testGenerateIndexPath() {
        Path indexPath = storage.generateIndexPath("myproject");
        
        assertNotNull(indexPath);
        assertTrue(indexPath.toString().contains("myproject-"));
        assertTrue(indexPath.toString().endsWith(".scip"));
        assertTrue(indexPath.startsWith(storage.getIndicesDir()));
    }

    @Test
    void testCreateMetadata() {
        IndexMetadata metadata = storage.createMetadata(
            "testproject",
            "/path/to/project",
            LanguageSupport.JAVA
        );
        
        assertNotNull(metadata);
        assertEquals("testproject", metadata.projectName());
        assertEquals("/path/to/project", metadata.projectPath());
        assertEquals(LanguageSupport.JAVA, metadata.language());
        assertNotNull(metadata.scipFilePath());
        assertEquals(IndexStatus.CREATED, metadata.status());
        assertNotNull(metadata.id());
        assertTrue(metadata.id().startsWith("testproject-"));
    }

    @Test
    void testSaveMetadata() throws Exception {
        IndexMetadata original = storage.createMetadata(
            "testproject",
            "/path/to/project",
            LanguageSupport.JAVA
        );
        
        // Create the index file
        Files.createFile(original.scipFilePath());
        
        storage.saveMetadata(original);
        
        // Verify metadata file exists
        Path metadataPath = storage.getMetadataPath(original.id());
        assertTrue(Files.exists(metadataPath), "Metadata file should exist at: " + metadataPath);
        
        // Verify file is not empty and contains expected content
        String content = Files.readString(metadataPath);
        assertTrue(content.contains("testproject"), "Metadata should contain project name");
        assertTrue(content.contains("JAVA"), "Metadata should contain language");
    }

    @Test
    void testLoadMetadata_notExists() {
        var metadata = storage.loadMetadata("nonexistent-12345678901234");
        assertTrue(metadata.isEmpty());
    }

    @Test
    void testSanitizeProjectName() {
        assertEquals("my-project", IndexMetadata.sanitizeProjectName("My Project"));
        assertEquals("test-123", IndexMetadata.sanitizeProjectName("TEST_123"));
        assertEquals("hello-world", IndexMetadata.sanitizeProjectName("hello--world"));
        assertEquals("unknown", IndexMetadata.sanitizeProjectName(null));
        assertEquals("unknown", IndexMetadata.sanitizeProjectName("  "));
    }

    @Test
    void testDeleteIndex() throws Exception {
        IndexMetadata metadata = storage.createMetadata(
            "delete-test",
            "/path/to/project",
            LanguageSupport.JAVA
        );
        Files.createFile(metadata.scipFilePath());
        storage.saveMetadata(metadata);
        
        assertTrue(storage.indexExists(metadata.id()), "Index should exist before delete");
        
        boolean deleted = storage.deleteIndex(metadata.id());
        assertTrue(deleted);
        
        assertFalse(storage.indexExists(metadata.id()), "Index should not exist after delete");
    }

    @Test
    void testWithStatus() {
        IndexMetadata metadata = storage.createMetadata(
            "status-test",
            "/path/to/project",
            LanguageSupport.JAVA
        );
        
        assertEquals(IndexStatus.CREATED, metadata.status());
        
        IndexMetadata parsed = metadata.withStatus(IndexStatus.PARSED);
        assertEquals(IndexStatus.PARSED, parsed.status());
        assertEquals(metadata.id(), parsed.id());
        
        IndexMetadata mapped = parsed.withStatus(IndexStatus.MAPPED);
        assertEquals(IndexStatus.MAPPED, mapped.status());
        
        IndexMetadata stored = mapped.withStatus(IndexStatus.STORED);
        assertEquals(IndexStatus.STORED, stored.status());
        assertTrue(stored.isComplete());
    }

    @Test
    void testWithError() {
        IndexMetadata metadata = storage.createMetadata(
            "error-test",
            "/path/to/project",
            LanguageSupport.JAVA
        );
        
        IndexMetadata failed = metadata.withError("Test error message");
        assertEquals(IndexStatus.FAILED, failed.status());
        assertEquals("Test error message", failed.errorMessage());
        assertTrue(failed.isFailed());
    }

    @Test
    void testWithCounts() {
        IndexMetadata metadata = storage.createMetadata(
            "counts-test",
            "/path/to/project",
            LanguageSupport.JAVA
        );
        
        assertEquals(0, metadata.documentCount());
        assertEquals(0, metadata.symbolCount());
        assertEquals(0, metadata.occurrenceCount());
        
        IndexMetadata updated = metadata.withCounts(10, 100, 500);
        assertEquals(10, updated.documentCount());
        assertEquals(100, updated.symbolCount());
        assertEquals(500, updated.occurrenceCount());
    }
}
