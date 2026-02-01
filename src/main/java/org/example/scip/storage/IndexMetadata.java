package org.example.scip.storage;

import org.example.model.LanguageSupport;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Metadata about a SCIP index file.
 * Tracks the index through the processing pipeline.
 */
public record IndexMetadata(
    String id,                      // Unique ID = filename without extension
    String projectName,             // Project name (sanitized)
    String projectPath,             // Original project path
    LanguageSupport language,       // Programming language
    Instant indexedAt,              // Timestamp when indexing started
    Path scipFilePath,              // Path to .scip file
    long fileSize,                  // File size in bytes
    int documentCount,              // Number of documents in index
    int symbolCount,                // Total symbols
    int occurrenceCount,            // Total occurrences
    String indexerVersion,          // scip-java version, etc.
    IndexStatus status,             // Current processing status
    String errorMessage             // Error message if failed
) {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = 
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * Create initial metadata for a new index.
     */
    public static IndexMetadata create(String projectName, String projectPath,
                                       LanguageSupport language, Path scipFilePath) {
        String timestamp = TIMESTAMP_FORMAT.format(LocalDateTime.now());
        String sanitized = sanitizeProjectName(projectName);
        String id = sanitized + "-" + timestamp;

        return new IndexMetadata(
            id,
            sanitized,
            projectPath,
            language,
            Instant.now(),
            scipFilePath,
            0L,
            0,
            0,
            0,
            null,
            IndexStatus.CREATED,
            null
        );
    }

    /**
     * Generate an ID for a project at current time.
     */
    public static String generateId(String projectName) {
        String timestamp = TIMESTAMP_FORMAT.format(LocalDateTime.now());
        return sanitizeProjectName(projectName) + "-" + timestamp;
    }

    /**
     * Sanitize project name to be safe for filenames.
     */
    public static String sanitizeProjectName(String name) {
        if (name == null || name.isBlank()) {
            return "unknown";
        }
        return name.toLowerCase()
            .replaceAll("[^a-z0-9-]", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
    }

    /**
     * Update status.
     */
    public IndexMetadata withStatus(IndexStatus newStatus) {
        return new IndexMetadata(
            id, projectName, projectPath, language, indexedAt,
            scipFilePath, fileSize, documentCount, symbolCount, occurrenceCount,
            indexerVersion, newStatus, errorMessage
        );
    }

    /**
     * Update status with error.
     */
    public IndexMetadata withError(String error) {
        return new IndexMetadata(
            id, projectName, projectPath, language, indexedAt,
            scipFilePath, fileSize, documentCount, symbolCount, occurrenceCount,
            indexerVersion, IndexStatus.FAILED, error
        );
    }

    /**
     * Update file size after index file is created.
     */
    public IndexMetadata withFileSize(long size) {
        return new IndexMetadata(
            id, projectName, projectPath, language, indexedAt,
            scipFilePath, size, documentCount, symbolCount, occurrenceCount,
            indexerVersion, status, errorMessage
        );
    }

    /**
     * Update counts after parsing.
     */
    public IndexMetadata withCounts(int docs, int symbols, int occurrences) {
        return new IndexMetadata(
            id, projectName, projectPath, language, indexedAt,
            scipFilePath, fileSize, docs, symbols, occurrences,
            indexerVersion, status, errorMessage
        );
    }

    /**
     * Update indexer version.
     */
    public IndexMetadata withIndexerVersion(String version) {
        return new IndexMetadata(
            id, projectName, projectPath, language, indexedAt,
            scipFilePath, fileSize, documentCount, symbolCount, occurrenceCount,
            version, status, errorMessage
        );
    }

    /**
     * Get the timestamp portion of the ID.
     */
    public String getTimestamp() {
        int dashIndex = id.lastIndexOf('-');
        return dashIndex > 0 ? id.substring(dashIndex + 1) : "";
    }

    /**
     * Get formatted indexed time.
     */
    public String getFormattedTime() {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .format(indexedAt.atZone(ZoneId.systemDefault()));
    }

    /**
     * Check if processing completed successfully.
     */
    public boolean isComplete() {
        return status == IndexStatus.STORED;
    }

    /**
     * Check if processing failed.
     */
    public boolean isFailed() {
        return status == IndexStatus.FAILED;
    }
}
