# Task: Create ScipIndexStorage

**ID:** create-scip-storage  
**Status:** pending  
**Priority:** high  

## Description

Tao ScipIndexStorage class de quan ly viec luu tru va dinh danh cac SCIP index files. Class nay se:
- Generate unique file paths voi timestamp
- Luu tru metadata cho moi index
- Ho tro list/query cac indices
- Cleanup old indices

## Acceptance Criteria

- [ ] Tao `ScipIndexStorage` trong package `org.example.scip.storage`
- [ ] Tao `IndexMetadata` record
- [ ] Tao `IndexStatus` enum
- [ ] Implement `generateIndexPath(projectName)` - tra ve path voi format `{project}-{yyyyMMddHHmmss}.scip`
- [ ] Implement `saveMetadata(metadata)` - luu metadata ra JSON file
- [ ] Implement `loadMetadata(id)` - doc metadata tu JSON
- [ ] Implement `listIndices(projectName)` - list tat ca indices cua 1 project
- [ ] Implement `getLatestIndex(projectName)` - lay index moi nhat
- [ ] Implement `cleanupOldIndices(projectName, keepCount)` - xoa indices cu
- [ ] Unit tests cho tat ca methods

## Folder Structure

```
.codebase-graph/                    # Default storage root
├── indices/                        # SCIP files
│   └── {project}-{timestamp}.scip
├── metadata/                       # JSON metadata
│   └── {project}-{timestamp}.json
└── config.json                     # Optional config
```

## Implementation

### IndexMetadata Record

```java
public record IndexMetadata(
    String id,                      // filename without .scip
    String projectName,
    String projectPath,
    String language,
    Instant indexedAt,
    Path scipFilePath,
    long fileSize,
    int documentCount,
    int symbolCount,
    int occurrenceCount,
    String indexerVersion,
    IndexStatus status
) {
    public static IndexMetadata create(String projectName, String projectPath, 
                                       String language, Path scipFilePath) {
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            .format(LocalDateTime.now());
        String id = projectName + "-" + timestamp;
        
        return new IndexMetadata(
            id, projectName, projectPath, language,
            Instant.now(), scipFilePath, 0,
            0, 0, 0, null, IndexStatus.CREATED
        );
    }
    
    public IndexMetadata withStatus(IndexStatus newStatus) {
        return new IndexMetadata(id, projectName, projectPath, language,
            indexedAt, scipFilePath, fileSize,
            documentCount, symbolCount, occurrenceCount,
            indexerVersion, newStatus);
    }
    
    public IndexMetadata withCounts(int docs, int symbols, int occurrences) {
        return new IndexMetadata(id, projectName, projectPath, language,
            indexedAt, scipFilePath, fileSize,
            docs, symbols, occurrences,
            indexerVersion, status);
    }
}
```

### ScipIndexStorage Class

```java
public class ScipIndexStorage {
    private static final String DEFAULT_STORAGE_DIR = ".codebase-graph";
    private static final String INDICES_DIR = "indices";
    private static final String METADATA_DIR = "metadata";
    
    private final Path storageRoot;
    private final ObjectMapper objectMapper;
    
    public ScipIndexStorage() {
        this(Path.of(System.getProperty("user.home"), DEFAULT_STORAGE_DIR));
    }
    
    public ScipIndexStorage(Path storageRoot) {
        this.storageRoot = storageRoot;
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
        initializeStorage();
    }
    
    private void initializeStorage() {
        try {
            Files.createDirectories(storageRoot.resolve(INDICES_DIR));
            Files.createDirectories(storageRoot.resolve(METADATA_DIR));
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize storage", e);
        }
    }
    
    public Path generateIndexPath(String projectName) {
        String sanitized = sanitizeProjectName(projectName);
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            .format(LocalDateTime.now());
        String filename = sanitized + "-" + timestamp + ".scip";
        return storageRoot.resolve(INDICES_DIR).resolve(filename);
    }
    
    private String sanitizeProjectName(String name) {
        return name.toLowerCase()
            .replaceAll("[^a-z0-9-]", "-")
            .replaceAll("-+", "-");
    }
    
    public void saveMetadata(IndexMetadata metadata) throws IOException {
        Path metaPath = storageRoot.resolve(METADATA_DIR)
            .resolve(metadata.id() + ".json");
        objectMapper.writeValue(metaPath.toFile(), metadata);
    }
    
    public Optional<IndexMetadata> loadMetadata(String id) {
        Path metaPath = storageRoot.resolve(METADATA_DIR)
            .resolve(id + ".json");
        if (!Files.exists(metaPath)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(metaPath.toFile(), IndexMetadata.class));
        } catch (IOException e) {
            return Optional.empty();
        }
    }
    
    public List<IndexMetadata> listIndices(String projectName) {
        String prefix = sanitizeProjectName(projectName) + "-";
        try (var files = Files.list(storageRoot.resolve(METADATA_DIR))) {
            return files
                .filter(p -> p.getFileName().toString().startsWith(prefix))
                .map(p -> {
                    String id = p.getFileName().toString().replace(".json", "");
                    return loadMetadata(id).orElse(null);
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(IndexMetadata::indexedAt).reversed())
                .toList();
        } catch (IOException e) {
            return List.of();
        }
    }
    
    public Optional<IndexMetadata> getLatestIndex(String projectName) {
        return listIndices(projectName).stream().findFirst();
    }
}
```

## Related Files

- `src/main/java/org/example/cli/IndexCommand.java` (will use this)
- `src/main/java/org/example/scip/ScipParser.java` (will update metadata)

## Dependencies

- Depends on: None (can start immediately)
- Blocks: `update-index-command`

## Notes

- Su dung Jackson ObjectMapper de serialize/deserialize metadata
- Storage root co the config qua environment variable hoac CLI option
- Project name duoc sanitize de dam bao filename hop le
