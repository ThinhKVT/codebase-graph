package org.example.scip.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.model.LanguageSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Manages storage of SCIP index files and their metadata.
 * 
 * <p>Provides versioned storage with the following structure:</p>
 * <pre>
 * {storageRoot}/
 * ├── indices/                        # SCIP index files
 * │   └── {project}-{timestamp}.scip
 * ├── metadata/                       # JSON metadata files
 * │   └── {project}-{timestamp}.json
 * └── config.json                     # Optional configuration
 * </pre>
 */
public class ScipIndexStorage {

    private static final Logger logger = LoggerFactory.getLogger(ScipIndexStorage.class);
    
    private static final String DEFAULT_STORAGE_DIR = ".codebase-graph";
    private static final String INDICES_DIR = "indices";
    private static final String METADATA_DIR = "metadata";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = 
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final Path storageRoot;
    private final ObjectMapper objectMapper;

    /**
     * Create storage in user home directory.
     */
    public ScipIndexStorage() {
        this(Path.of(System.getProperty("user.home"), DEFAULT_STORAGE_DIR));
    }

    /**
     * Create storage at specified path.
     */
    public ScipIndexStorage(Path storageRoot) {
        this.storageRoot = storageRoot;
        this.objectMapper = createObjectMapper();
        initializeStorage();
    }

    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }

    private void initializeStorage() {
        try {
            Files.createDirectories(getIndicesDir());
            Files.createDirectories(getMetadataDir());
            logger.info("Initialized SCIP storage at: {}", storageRoot);
        } catch (IOException e) {
            throw new StorageException("Failed to initialize storage at: " + storageRoot, e);
        }
    }

    /**
     * Get the storage root directory.
     */
    public Path getStorageRoot() {
        return storageRoot;
    }

    /**
     * Get the indices directory.
     */
    public Path getIndicesDir() {
        return storageRoot.resolve(INDICES_DIR);
    }

    /**
     * Get the metadata directory.
     */
    public Path getMetadataDir() {
        return storageRoot.resolve(METADATA_DIR);
    }

    /**
     * Generate a new index file path with timestamp.
     * 
     * @param projectName The project name (will be sanitized)
     * @return Path where the new index file should be written
     */
    public Path generateIndexPath(String projectName) {
        String sanitized = IndexMetadata.sanitizeProjectName(projectName);
        String timestamp = TIMESTAMP_FORMAT.format(LocalDateTime.now());
        String filename = sanitized + "-" + timestamp + ".scip";
        return getIndicesDir().resolve(filename);
    }

    /**
     * Create initial metadata for a new index.
     * 
     * @param projectName Project name
     * @param projectPath Full path to the project
     * @param language Programming language
     * @return New IndexMetadata with generated ID and path
     */
    public IndexMetadata createMetadata(String projectName, String projectPath, 
                                        LanguageSupport language) {
        Path indexPath = generateIndexPath(projectName);
        return IndexMetadata.create(projectName, projectPath, language, indexPath);
    }

    /**
     * Save metadata to JSON file.
     */
    public void saveMetadata(IndexMetadata metadata) {
        Path metaPath = getMetadataPath(metadata.id());
        try {
            objectMapper.writeValue(metaPath.toFile(), metadata);
            logger.debug("Saved metadata: {}", metaPath);
        } catch (IOException e) {
            throw new StorageException("Failed to save metadata: " + metadata.id(), e);
        }
    }

    /**
     * Load metadata by ID.
     */
    public Optional<IndexMetadata> loadMetadata(String id) {
        Path metaPath = getMetadataPath(id);
        if (!Files.exists(metaPath)) {
            return Optional.empty();
        }
        try {
            IndexMetadata metadata = objectMapper.readValue(metaPath.toFile(), IndexMetadata.class);
            return Optional.of(metadata);
        } catch (IOException e) {
            logger.warn("Failed to load metadata: {}", id, e);
            return Optional.empty();
        }
    }

    /**
     * Get the path to a metadata file.
     */
    public Path getMetadataPath(String id) {
        return getMetadataDir().resolve(id + ".json");
    }

    /**
     * Get the path to an index file.
     */
    public Path getIndexPath(String id) {
        return getIndicesDir().resolve(id + ".scip");
    }

    /**
     * List all indices for a project, sorted by timestamp (newest first).
     */
    public List<IndexMetadata> listIndices(String projectName) {
        String prefix = IndexMetadata.sanitizeProjectName(projectName) + "-";
        try (Stream<Path> files = Files.list(getMetadataDir())) {
            return files
                .filter(p -> p.getFileName().toString().startsWith(prefix))
                .filter(p -> p.toString().endsWith(".json"))
                .map(p -> {
                    String id = p.getFileName().toString().replace(".json", "");
                    return loadMetadata(id).orElse(null);
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(IndexMetadata::indexedAt).reversed())
                .toList();
        } catch (IOException e) {
            logger.warn("Failed to list indices for: {}", projectName, e);
            return List.of();
        }
    }

    /**
     * List all indices across all projects.
     */
    public List<IndexMetadata> listAllIndices() {
        try (Stream<Path> files = Files.list(getMetadataDir())) {
            return files
                .filter(p -> p.toString().endsWith(".json"))
                .map(p -> {
                    String id = p.getFileName().toString().replace(".json", "");
                    return loadMetadata(id).orElse(null);
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(IndexMetadata::indexedAt).reversed())
                .toList();
        } catch (IOException e) {
            logger.warn("Failed to list all indices", e);
            return List.of();
        }
    }

    /**
     * Get the latest index for a project.
     */
    public Optional<IndexMetadata> getLatestIndex(String projectName) {
        return listIndices(projectName).stream().findFirst();
    }

    /**
     * Get the latest successful (STORED) index for a project.
     */
    public Optional<IndexMetadata> getLatestSuccessfulIndex(String projectName) {
        return listIndices(projectName).stream()
            .filter(IndexMetadata::isComplete)
            .findFirst();
    }

    /**
     * Delete an index and its metadata.
     */
    public boolean deleteIndex(String id) {
        try {
            Path indexPath = getIndexPath(id);
            Path metaPath = getMetadataPath(id);
            
            boolean indexDeleted = Files.deleteIfExists(indexPath);
            boolean metaDeleted = Files.deleteIfExists(metaPath);
            
            if (indexDeleted || metaDeleted) {
                logger.info("Deleted index: {}", id);
                return true;
            }
            return false;
        } catch (IOException e) {
            logger.warn("Failed to delete index: {}", id, e);
            return false;
        }
    }

    /**
     * Delete old indices, keeping only the N most recent.
     * 
     * @param projectName Project name
     * @param keepCount Number of indices to keep
     * @return Number of indices deleted
     */
    public int cleanupOldIndices(String projectName, int keepCount) {
        List<IndexMetadata> indices = listIndices(projectName);
        if (indices.size() <= keepCount) {
            return 0;
        }

        int deleted = 0;
        List<IndexMetadata> toDelete = indices.subList(keepCount, indices.size());
        for (IndexMetadata meta : toDelete) {
            if (deleteIndex(meta.id())) {
                deleted++;
            }
        }

        logger.info("Cleaned up {} old indices for project: {}", deleted, projectName);
        return deleted;
    }

    /**
     * Check if an index file exists.
     */
    public boolean indexExists(String id) {
        return Files.exists(getIndexPath(id));
    }

    /**
     * Get file size of an index.
     */
    public long getIndexFileSize(String id) {
        try {
            return Files.size(getIndexPath(id));
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * Update metadata with file size from the actual file.
     */
    public IndexMetadata updateFileSizeFromDisk(IndexMetadata metadata) {
        long size = getIndexFileSize(metadata.id());
        IndexMetadata updated = metadata.withFileSize(size);
        saveMetadata(updated);
        return updated;
    }

    /**
     * Exception for storage operations.
     */
    public static class StorageException extends RuntimeException {
        public StorageException(String message) {
            super(message);
        }

        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
