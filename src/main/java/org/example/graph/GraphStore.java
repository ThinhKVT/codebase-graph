package org.example.graph;

import org.example.model.*;
import java.util.List;
import java.util.Optional;

/**
 * Interface for graph database operations.
 */
public interface GraphStore extends AutoCloseable {

    void connect() throws GraphStoreException;
    boolean isConnected();
    @Override
    void close();

    // Repository Operations
    void saveRepository(Repository repository);
    Optional<Repository> findRepositoryById(String id);
    Optional<Repository> findRepositoryByPath(String path);
    List<Repository> findAllRepositories();
    void deleteRepository(String repositoryId);

    // Symbol Operations
    void saveSymbol(Symbol symbol);
    void saveSymbols(List<Symbol> symbols);
    Optional<Symbol> findSymbolById(String id);
    List<Symbol> findSymbolsByName(String name);
    List<Symbol> findSymbolsByNamePattern(String pattern);
    List<Symbol> findSymbolsByKind(SymbolKind kind);
    List<Symbol> findSymbolsByFile(String filePath);
    Optional<Symbol> findSymbolByFQN(String fullyQualifiedName);
    void deleteSymbolsByRepository(String repositoryId);

    // Reference Operations
    void saveReference(Reference reference);
    void saveReferences(List<Reference> references);
    List<Reference> findReferencesToSymbol(String symbolId);
    List<Reference> findReferencesFromSymbol(String symbolId);
    List<Reference> findReferencesByKind(String symbolId, ReferenceKind kind);

    // Dependency Operations
    List<Symbol> findDependencies(String symbolId);
    List<Symbol> findTransitiveDependencies(String symbolId, int depth);
    List<Symbol> findDependents(String symbolId);
    List<Symbol> findTransitiveDependents(String symbolId, int depth);

    // Source File Operations
    void saveSourceFile(SourceFile sourceFile, String repositoryId);
    Optional<SourceFile> findSourceFileByPath(String path);
    List<SourceFile> findSourceFilesByRepository(String repositoryId);

    // Statistics
    long countSymbols();
    long countSymbolsByRepository(String repositoryId);
    long countReferences();

    // Maintenance
    void createIndexes();
    void clearAll();
}

