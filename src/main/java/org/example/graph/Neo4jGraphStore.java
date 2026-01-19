package org.example.graph;

import org.example.model.*;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/**
 * Neo4j implementation of GraphStore.
 */
public class Neo4jGraphStore implements GraphStore {

    private static final Logger logger = LoggerFactory.getLogger(Neo4jGraphStore.class);

    private final String uri;
    private final String username;
    private final String password;
    private Driver driver;

    public Neo4jGraphStore(String uri, String username, String password) {
        this.uri = uri;
        this.username = username;
        this.password = password;
    }

    @Override
    public void connect() throws GraphStoreException {
        try {
            logger.info("Connecting to Neo4j at {}", uri);
            driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password));
            driver.verifyConnectivity();
            logger.info("Successfully connected to Neo4j");
        } catch (Exception e) {
            throw GraphStoreException.connectionFailed(uri, e);
        }
    }

    @Override
    public boolean isConnected() {
        if (driver == null) return false;
        try {
            driver.verifyConnectivity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void close() {
        if (driver != null) {
            driver.close();
            driver = null;
        }
    }

    private void ensureConnected() {
        if (driver == null) throw GraphStoreException.notConnected();
    }

    private Session session() {
        ensureConnected();
        return driver.session();
    }

    // ==================== Repository Operations ====================

    @Override
    public void saveRepository(Repository repository) {
        String cypher = "MERGE (r:Repository {id: $id}) SET r.name = $name, r.path = $path, r.language = $language, r.lastIndexedAt = $lastIndexedAt, r.fileCount = $fileCount, r.symbolCount = $symbolCount";
        try (Session session = session()) {
            session.run(cypher, Map.of(
                "id", repository.id(),
                "name", repository.name(),
                "path", repository.path(),
                "language", Objects.toString(repository.language(), ""),
                "lastIndexedAt", Objects.toString(repository.lastIndexedAt(), ""),
                "fileCount", repository.fileCount(),
                "symbolCount", repository.symbolCount()
            ));
        }
    }

    @Override
    public Optional<Repository> findRepositoryById(String id) {
        String cypher = "MATCH (r:Repository {id: $id}) RETURN r";
        try (Session session = session()) {
            return session.run(cypher, Map.of("id", id)).list().stream().findFirst().map(this::mapToRepository);
        }
    }

    @Override
    public Optional<Repository> findRepositoryByPath(String path) {
        String cypher = "MATCH (r:Repository {path: $path}) RETURN r";
        try (Session session = session()) {
            return session.run(cypher, Map.of("path", path)).list().stream().findFirst().map(this::mapToRepository);
        }
    }

    @Override
    public List<Repository> findAllRepositories() {
        String cypher = "MATCH (r:Repository) RETURN r";
        try (Session session = session()) {
            return session.run(cypher).list().stream().map(this::mapToRepository).toList();
        }
    }

    @Override
    public void deleteRepository(String repositoryId) {
        String cypher = "MATCH (r:Repository {id: $id}) OPTIONAL MATCH (r)-[:CONTAINS_FILE]->(f:SourceFile) OPTIONAL MATCH (f)-[:DEFINES]->(s:Symbol) OPTIONAL MATCH (s)-[ref:REFERENCES]->() DELETE ref, s, f, r";
        try (Session session = session()) {
            session.run(cypher, Map.of("id", repositoryId));
        }
    }

    private Repository mapToRepository(Record record) {
        var node = record.get("r").asNode();
        String lastIndexedStr = node.get("lastIndexedAt").asString("");
        Instant lastIndexedAt = lastIndexedStr.isEmpty() ? null : Instant.parse(lastIndexedStr);
        return new Repository(node.get("id").asString(), node.get("name").asString(), node.get("path").asString(),
            node.get("language").asString(null), lastIndexedAt, node.get("fileCount").asInt(0), node.get("symbolCount").asInt(0));
    }

    // ==================== Symbol Operations ====================

    @Override
    public void saveSymbol(Symbol symbol) {
        saveSymbols(List.of(symbol));
    }

    @Override
    public void saveSymbols(List<Symbol> symbols) {
        if (symbols.isEmpty()) return;
        String cypher = "UNWIND $symbols AS s MERGE (sym:Symbol {id: s.id}) SET sym.name = s.name, sym.fullyQualifiedName = s.fullyQualifiedName, sym.kind = s.kind, sym.filePath = s.filePath, sym.startLine = s.startLine, sym.endLine = s.endLine, sym.signature = s.signature, sym.documentation = s.documentation, sym.parentId = s.parentId";
        List<Map<String, Object>> symbolMaps = symbols.stream().map(this::symbolToMap).toList();
        try (Session session = session()) {
            session.run(cypher, Map.of("symbols", symbolMaps));
        }
    }

    private Map<String, Object> symbolToMap(Symbol s) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", s.id());
        map.put("name", s.name());
        map.put("fullyQualifiedName", s.fullyQualifiedName());
        map.put("kind", s.kind().name());
        map.put("filePath", Objects.toString(s.filePath(), ""));
        map.put("startLine", s.startLine());
        map.put("endLine", s.endLine());
        map.put("signature", Objects.toString(s.signature(), ""));
        map.put("documentation", Objects.toString(s.documentation(), ""));
        map.put("parentId", Objects.toString(s.parentId(), ""));
        return map;
    }

    @Override
    public Optional<Symbol> findSymbolById(String id) {
        String cypher = "MATCH (s:Symbol {id: $id}) RETURN s";
        try (Session session = session()) {
            return session.run(cypher, Map.of("id", id)).list().stream().findFirst().map(this::mapToSymbol);
        }
    }

    @Override
    public List<Symbol> findSymbolsByName(String name) {
        String cypher = "MATCH (s:Symbol {name: $name}) RETURN s";
        try (Session session = session()) {
            return session.run(cypher, Map.of("name", name)).list().stream().map(this::mapToSymbol).toList();
        }
    }

    @Override
    public List<Symbol> findSymbolsByNamePattern(String pattern) {
        String regex = pattern.replace("*", ".*").replace("?", ".");
        String cypher = "MATCH (s:Symbol) WHERE s.name =~ $pattern RETURN s";
        try (Session session = session()) {
            return session.run(cypher, Map.of("pattern", regex)).list().stream().map(this::mapToSymbol).toList();
        }
    }

    @Override
    public List<Symbol> findSymbolsByKind(SymbolKind kind) {
        String cypher = "MATCH (s:Symbol {kind: $kind}) RETURN s";
        try (Session session = session()) {
            return session.run(cypher, Map.of("kind", kind.name())).list().stream().map(this::mapToSymbol).toList();
        }
    }

    @Override
    public List<Symbol> findSymbolsByFile(String filePath) {
        String cypher = "MATCH (s:Symbol {filePath: $filePath}) RETURN s";
        try (Session session = session()) {
            return session.run(cypher, Map.of("filePath", filePath)).list().stream().map(this::mapToSymbol).toList();
        }
    }

    @Override
    public Optional<Symbol> findSymbolByFQN(String fullyQualifiedName) {
        String cypher = "MATCH (s:Symbol {fullyQualifiedName: $fqn}) RETURN s";
        try (Session session = session()) {
            return session.run(cypher, Map.of("fqn", fullyQualifiedName)).list().stream().findFirst().map(this::mapToSymbol);
        }
    }

    @Override
    public void deleteSymbolsByRepository(String repositoryId) {
        String cypher = "MATCH (r:Repository {id: $repoId})-[:CONTAINS_FILE]->(f:SourceFile)-[:DEFINES]->(s:Symbol) OPTIONAL MATCH (s)-[ref:REFERENCES]->() DELETE ref, s";
        try (Session session = session()) {
            session.run(cypher, Map.of("repoId", repositoryId));
        }
    }

    private Symbol mapToSymbol(Record record) {
        var node = record.get("s").asNode();
        return Symbol.builder()
            .id(node.get("id").asString())
            .name(node.get("name").asString())
            .fullyQualifiedName(node.get("fullyQualifiedName").asString())
            .kind(SymbolKind.valueOf(node.get("kind").asString()))
            .filePath(nullIfEmpty(node.get("filePath").asString("")))
            .startLine(node.get("startLine").asInt(0))
            .endLine(node.get("endLine").asInt(0))
            .signature(nullIfEmpty(node.get("signature").asString("")))
            .documentation(nullIfEmpty(node.get("documentation").asString("")))
            .parentId(nullIfEmpty(node.get("parentId").asString("")))
            .build();
    }

    private String nullIfEmpty(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    // ==================== Reference Operations ====================

    @Override
    public void saveReference(Reference reference) {
        saveReferences(List.of(reference));
    }

    @Override
    public void saveReferences(List<Reference> references) {
        if (references.isEmpty()) return;
        String cypher = "UNWIND $refs AS r MATCH (from:Symbol {id: r.fromSymbolId}) MATCH (to:Symbol {id: r.toSymbolId}) MERGE (from)-[ref:REFERENCES {kind: r.kind}]->(to) SET ref.filePath = r.filePath, ref.line = r.line, ref.column = r.column";
        List<Map<String, Object>> refMaps = references.stream().map(this::referenceToMap).toList();
        try (Session session = session()) {
            session.run(cypher, Map.of("refs", refMaps));
        }
    }

    private Map<String, Object> referenceToMap(Reference r) {
        Map<String, Object> map = new HashMap<>();
        map.put("fromSymbolId", r.fromSymbolId());
        map.put("toSymbolId", r.toSymbolId());
        map.put("kind", r.kind().name());
        map.put("filePath", Objects.toString(r.filePath(), ""));
        map.put("line", r.line());
        map.put("column", r.column());
        return map;
    }

    @Override
    public List<Reference> findReferencesToSymbol(String symbolId) {
        String cypher = "MATCH (from:Symbol)-[ref:REFERENCES]->(to:Symbol {id: $symbolId}) RETURN from.id AS fromId, to.id AS toId, ref.kind AS kind, ref.filePath AS filePath, ref.line AS line, ref.column AS column";
        try (Session session = session()) {
            return session.run(cypher, Map.of("symbolId", symbolId)).list().stream().map(this::mapToReference).toList();
        }
    }

    @Override
    public List<Reference> findReferencesFromSymbol(String symbolId) {
        String cypher = "MATCH (from:Symbol {id: $symbolId})-[ref:REFERENCES]->(to:Symbol) RETURN from.id AS fromId, to.id AS toId, ref.kind AS kind, ref.filePath AS filePath, ref.line AS line, ref.column AS column";
        try (Session session = session()) {
            return session.run(cypher, Map.of("symbolId", symbolId)).list().stream().map(this::mapToReference).toList();
        }
    }

    @Override
    public List<Reference> findReferencesByKind(String symbolId, ReferenceKind kind) {
        String cypher = "MATCH (from:Symbol)-[ref:REFERENCES {kind: $kind}]->(to:Symbol {id: $symbolId}) RETURN from.id AS fromId, to.id AS toId, ref.kind AS kind, ref.filePath AS filePath, ref.line AS line, ref.column AS column";
        try (Session session = session()) {
            return session.run(cypher, Map.of("symbolId", symbolId, "kind", kind.name())).list().stream().map(this::mapToReference).toList();
        }
    }

    private Reference mapToReference(Record record) {
        return Reference.builder()
            .fromSymbolId(record.get("fromId").asString())
            .toSymbolId(record.get("toId").asString())
            .kind(ReferenceKind.valueOf(record.get("kind").asString()))
            .filePath(nullIfEmpty(record.get("filePath").asString("")))
            .line(record.get("line").asInt(0))
            .column(record.get("column").asInt(0))
            .build();
    }

    // ==================== Dependency Operations ====================

    @Override
    public List<Symbol> findDependencies(String symbolId) {
        String cypher = "MATCH (s:Symbol {id: $symbolId})-[:REFERENCES]->(dep:Symbol) RETURN DISTINCT dep AS s";
        try (Session session = session()) {
            return session.run(cypher, Map.of("symbolId", symbolId)).list().stream().map(this::mapToSymbol).toList();
        }
    }

    @Override
    public List<Symbol> findTransitiveDependencies(String symbolId, int depth) {
        String cypher = String.format("MATCH (s:Symbol {id: $symbolId})-[:REFERENCES*1..%d]->(dep:Symbol) RETURN DISTINCT dep AS s", depth);
        try (Session session = session()) {
            return session.run(cypher, Map.of("symbolId", symbolId)).list().stream().map(this::mapToSymbol).toList();
        }
    }

    @Override
    public List<Symbol> findDependents(String symbolId) {
        String cypher = "MATCH (dep:Symbol)-[:REFERENCES]->(s:Symbol {id: $symbolId}) RETURN DISTINCT dep AS s";
        try (Session session = session()) {
            return session.run(cypher, Map.of("symbolId", symbolId)).list().stream().map(this::mapToSymbol).toList();
        }
    }

    @Override
    public List<Symbol> findTransitiveDependents(String symbolId, int depth) {
        String cypher = String.format("MATCH (dep:Symbol)-[:REFERENCES*1..%d]->(s:Symbol {id: $symbolId}) RETURN DISTINCT dep AS s", depth);
        try (Session session = session()) {
            return session.run(cypher, Map.of("symbolId", symbolId)).list().stream().map(this::mapToSymbol).toList();
        }
    }

    // ==================== Source File Operations ====================

    @Override
    public void saveSourceFile(SourceFile sourceFile, String repositoryId) {
        String cypher = "MATCH (r:Repository {id: $repoId}) MERGE (f:SourceFile {path: $path}) SET f.relativePath = $relativePath, f.hash = $hash, f.language = $language MERGE (r)-[:CONTAINS_FILE]->(f)";
        try (Session session = session()) {
            session.run(cypher, Map.of("repoId", repositoryId, "path", sourceFile.path(), "relativePath", sourceFile.relativePath(),
                "hash", Objects.toString(sourceFile.hash(), ""), "language", Objects.toString(sourceFile.language(), "")));
        }
    }

    @Override
    public Optional<SourceFile> findSourceFileByPath(String path) {
        String cypher = "MATCH (f:SourceFile {path: $path}) RETURN f";
        try (Session session = session()) {
            return session.run(cypher, Map.of("path", path)).list().stream().findFirst().map(this::mapToSourceFile);
        }
    }

    @Override
    public List<SourceFile> findSourceFilesByRepository(String repositoryId) {
        String cypher = "MATCH (r:Repository {id: $repoId})-[:CONTAINS_FILE]->(f:SourceFile) RETURN f";
        try (Session session = session()) {
            return session.run(cypher, Map.of("repoId", repositoryId)).list().stream().map(this::mapToSourceFile).toList();
        }
    }

    private SourceFile mapToSourceFile(Record record) {
        var node = record.get("f").asNode();
        return new SourceFile(node.get("path").asString(), node.get("relativePath").asString(),
            nullIfEmpty(node.get("hash").asString("")), nullIfEmpty(node.get("language").asString("")));
    }

    // ==================== Statistics ====================

    @Override
    public long countSymbols() {
        String cypher = "MATCH (s:Symbol) RETURN count(s) AS count";
        try (Session session = session()) {
            return session.run(cypher).single().get("count").asLong();
        }
    }

    @Override
    public long countSymbolsByRepository(String repositoryId) {
        String cypher = "MATCH (r:Repository {id: $repoId})-[:CONTAINS_FILE]->(:SourceFile)-[:DEFINES]->(s:Symbol) RETURN count(s) AS count";
        try (Session session = session()) {
            return session.run(cypher, Map.of("repoId", repositoryId)).single().get("count").asLong();
        }
    }

    @Override
    public long countReferences() {
        String cypher = "MATCH ()-[r:REFERENCES]->() RETURN count(r) AS count";
        try (Session session = session()) {
            return session.run(cypher).single().get("count").asLong();
        }
    }

    // ==================== Maintenance ====================

    @Override
    public void createIndexes() {
        String[] indexes = {
            "CREATE INDEX symbol_id IF NOT EXISTS FOR (s:Symbol) ON (s.id)",
            "CREATE INDEX symbol_name IF NOT EXISTS FOR (s:Symbol) ON (s.name)",
            "CREATE INDEX symbol_fqn IF NOT EXISTS FOR (s:Symbol) ON (s.fullyQualifiedName)",
            "CREATE INDEX repo_id IF NOT EXISTS FOR (r:Repository) ON (r.id)",
            "CREATE INDEX file_path IF NOT EXISTS FOR (f:SourceFile) ON (f.path)"
        };
        try (Session session = session()) {
            for (String index : indexes) {
                session.run(index);
            }
        }
    }

    @Override
    public void clearAll() {
        String cypher = "MATCH (n) DETACH DELETE n";
        try (Session session = session()) {
            session.run(cypher);
        }
    }
}

