# Feature Specification: Codebase Knowledge Graph

**Feature Branch**: `001-codebase-knowledge-graph`  
**Created**: January 18, 2026  
**Status**: Draft  
**Input**: User description: "Build a codebase knowledge graph that indexes repositories, resolves symbols, and exposes dependency relationships."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Index a Repository (Priority: P1)

As a developer, I want to index a local repository so that all symbols (classes, methods, functions, variables) are extracted and stored in a graph database.

**Why this priority**: This is the foundational capability. Without indexing, no other features can work. It provides the raw data that all downstream queries depend on.

**Independent Test**: Can be fully tested by pointing the tool at a sample Java repository and verifying that nodes are created in the graph for each class, method, and field.

**Acceptance Scenarios**:

1. **Given** a local Java repository path, **When** I run the indexer, **Then** all classes are stored as nodes with properties (name, package, file path, line number)
2. **Given** a repository with multiple source files, **When** I run the indexer, **Then** all methods are stored as nodes linked to their containing class
3. **Given** a previously indexed repository, **When** I re-run the indexer, **Then** the graph is updated incrementally (changed files re-indexed, deleted symbols removed)
4. **Given** an invalid repository path, **When** I run the indexer, **Then** a clear error message is displayed
5. **Given** Neo4j server is not running, **When** I run the indexer, **Then** a clear error message is displayed with instructions to start Docker Compose

---

### User Story 2 - Resolve Symbol References (Priority: P1)

As a developer, I want symbol references to be resolved so that I can trace where a class/method is used across the codebase.

**Why this priority**: Symbol resolution is core to understanding code relationships. It transforms raw syntax into semantic connections (e.g., linking a method call to its definition).

**Independent Test**: After indexing, query for all usages of a specific class and verify that all call sites are correctly linked.

**Acceptance Scenarios**:

1. **Given** an indexed repository with class `Foo` used in class `Bar`, **When** I query for references to `Foo`, **Then** the result includes `Bar` with the specific line number
2. **Given** a method `calculate()` called from 3 different classes, **When** I query for references, **Then** all 3 call sites are returned with file and line information
3. **Given** an overloaded method, **When** I query for references, **Then** each overload is resolved independently based on signature
4. **Given** a symbol with no references, **When** I query for references, **Then** an empty result set is returned

---

### User Story 3 - Query Dependency Relationships (Priority: P1)

As a developer, I want to query dependency relationships so that I can understand how components are connected.

**Why this priority**: Dependency analysis is a primary use case for knowledge graphs. It enables impact analysis, architecture visualization, and refactoring decisions.

**Independent Test**: Query for all dependencies of a given class and verify the returned graph matches manual inspection.

**Acceptance Scenarios**:

1. **Given** class `A` that imports class `B`, **When** I query dependencies of `A`, **Then** `B` is returned as a dependency
2. **Given** a class hierarchy `A extends B implements C`, **When** I query dependencies of `A`, **Then** both `B` and `C` are returned with relationship types (EXTENDS, IMPLEMENTS)
3. **Given** class `A` with field of type `B`, **When** I query dependencies, **Then** `B` is included as a HAS_FIELD_TYPE dependency
4. **Given** a depth parameter of 2, **When** I query transitive dependencies, **Then** dependencies up to 2 hops are returned

---

### User Story 4 - Expose Graph via API (Priority: P2)

As an integration developer, I want to access the knowledge graph via a programmatic API so that I can build tools on top of it (IDE plugins, CI checks, visualization).

**Why this priority**: An API unlocks third-party integrations and automation. However, CLI/direct queries satisfy initial use cases.

**Independent Test**: Make HTTP/gRPC calls to query symbols and verify JSON/protobuf responses match expected schema.

**Acceptance Scenarios**:

1. **Given** a running API server, **When** I GET `/symbols?name=Foo`, **Then** matching symbols are returned as JSON with name, type, location
2. **Given** a symbol ID, **When** I GET `/symbols/{id}/references`, **Then** all references are returned with file paths and line numbers
3. **Given** a symbol ID, **When** I GET `/symbols/{id}/dependencies?depth=2`, **Then** transitive dependencies are returned as a graph structure
4. **Given** an invalid symbol ID, **When** I query the API, **Then** a 404 response with error details is returned

---

### User Story 5 - Support Multiple Languages (Priority: P3)

As a developer working on polyglot codebases, I want the indexer to support multiple languages (Java, Python, TypeScript) so that cross-language dependencies are visible.

**Why this priority**: Multi-language support expands applicability but adds significant complexity. Java-only MVP delivers value to many users.

**Independent Test**: Index a mixed Java/Python repository and verify symbols from both languages appear in the graph with cross-references where applicable.

**Technical Note**: Multi-language support is enabled by SCIP format - simply add scip-python, scip-typescript runners alongside scip-java.

**Acceptance Scenarios**:

1. **Given** a Python repository, **When** I run the indexer with scip-python, **Then** classes, functions, and modules are extracted and stored
2. **Given** a TypeScript repository, **When** I run the indexer with scip-typescript, **Then** classes, interfaces, functions, and imports are extracted
3. **Given** a multi-language repo, **When** I query the graph, **Then** I can filter by language
4. **Given** a language not yet supported, **When** I run the indexer, **Then** a warning is logged and unsupported files are skipped

---

### Edge Cases

- What happens when a file has syntax errors? → scip-java handles gracefully, index valid portions, log errors
- How does the system handle circular dependencies? → Detect and store cycles, expose via query API
- What happens with generated code (e.g., protobuf)? → Option to include/exclude generated sources
- How are anonymous classes and lambdas handled? → SCIP format includes synthetic names, linked to enclosing scope
- What happens when the graph database is unavailable? → Graceful failure with clear error, retry logic, instructions to run `docker-compose up`
- How are very large repositories (>100K files) handled? → Streaming/batched indexing, progress reporting
- What happens when scip-java is not installed? → Clear error message with installation instructions

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST use **scip-java** to generate SCIP index from Java source files
- **FR-002**: System MUST parse SCIP protobuf format to extract symbols (packages, classes, interfaces, enums, methods, fields)
- **FR-003**: System MUST resolve symbol references using SCIP occurrence data (definitions and references)
- **FR-004**: System MUST store symbols and relationships in **Neo4j server** (via Docker Compose)
- **FR-005**: System MUST support incremental indexing (only re-index changed files based on timestamps or hashes)
- **FR-006**: System MUST expose a CLI for indexing operations (`index`, `query`, `status`)
- **FR-007**: System MUST support querying symbols by name, type, package, or file path
- **FR-008**: System MUST support querying references (find all usages of a symbol)
- **FR-009**: System MUST support querying dependencies (what does this symbol depend on)
- **FR-010**: System MUST support querying dependents (what depends on this symbol)
- **FR-011**: System MUST support transitive dependency queries with configurable depth
- **FR-012**: System MUST handle repositories with up to 100,000 source files
- **FR-013**: System MUST provide progress feedback during indexing (files processed, symbols found)
- **FR-014**: System MUST log errors for unparseable files without stopping the indexing process
- **FR-015**: System MUST expose an HTTP/gRPC API for programmatic access (P2)
- **FR-016**: System MUST check Neo4j server connectivity before indexing and provide clear error if unavailable
- **FR-017**: System MUST check scip-java installation before indexing and provide clear error if not found

### Non-Functional Requirements

- **NFR-001**: Indexing throughput MUST be at least 1,000 files per minute on standard hardware
- **NFR-002**: Symbol lookup queries MUST return in under 100ms for repositories up to 50K symbols
- **NFR-003**: System MUST use less than 4GB RAM for repositories up to 100K files
- **NFR-004**: Graph database MUST persist data durably (Docker volume survives container restarts)
- **NFR-005**: API MUST support concurrent requests (at least 10 simultaneous queries)

### Key Entities

- **Repository**: A codebase being indexed. Attributes: path, name, language, last_indexed_at
- **SourceFile**: A file within a repository. Attributes: path, hash, last_modified, language
- **Symbol**: A named code element. Types: PACKAGE, CLASS, INTERFACE, ENUM, METHOD, FIELD, VARIABLE, FUNCTION, MODULE
  - Attributes: name, fully_qualified_name, kind, visibility, file_path, start_line, end_line, signature (for methods)
- **Reference**: A usage of a symbol. Attributes: from_symbol, to_symbol, reference_kind (CALL, IMPORT, EXTENDS, IMPLEMENTS, TYPE_REF, FIELD_ACCESS)
- **Dependency**: An edge representing a dependency relationship. Types: IMPORTS, EXTENDS, IMPLEMENTS, USES, CONTAINS

### Relationship Types in Graph

```
(Symbol)-[:DEFINED_IN]->(SourceFile)
(SourceFile)-[:BELONGS_TO]->(Repository)
(Symbol)-[:CONTAINS]->(Symbol)  // class contains method
(Symbol)-[:EXTENDS]->(Symbol)
(Symbol)-[:IMPLEMENTS]->(Symbol)
(Symbol)-[:REFERENCES]->(Symbol)  // usage relationship
(Symbol)-[:HAS_PARAMETER_TYPE]->(Symbol)
(Symbol)-[:HAS_RETURN_TYPE]->(Symbol)
(Symbol)-[:HAS_FIELD_TYPE]->(Symbol)
```

## Technical Approach *(informational)*

### Parsing Strategy

Sử dụng **scip-java** (external CLI tool) thay vì tự implement parser:

1. **scip-java** generate SCIP index từ Java source code
   - Sử dụng compiler-level symbol resolution (chính xác hơn JavaParser)
   - Output: `.scip` protobuf file
   
2. **ScipParser** đọc SCIP protobuf, extract:
   - `Document`: source files
   - `SymbolInformation`: symbol definitions với metadata
   - `Occurrence`: symbol usages (references)

3. **Multi-language extension** (P3): Thêm runners cho:
   - `scip-python` cho Python
   - `scip-typescript` cho TypeScript

### Graph Database

- **Primary choice**: Neo4j server via **Docker Compose**
  - Container: `neo4j:5.15.0`
  - Bolt connection: `bolt://localhost:7687`
  - Web UI: `http://localhost:7474` để visualize graph
  - Data persistence: Docker volume

### Architecture Components

1. **ScipRunner**: Execute scip-java CLI, generate .scip file
2. **ScipParser**: Parse SCIP protobuf format
3. **ScipToGraphMapper**: Convert SCIP entities → domain model (Symbol, Reference)
4. **Neo4jGraphStore**: Persist to Neo4j via Bolt driver
5. **Query Engine**: Translates high-level queries to Cypher
6. **CLI**: Command-line interface for indexing and querying
7. **API Server**: HTTP/gRPC server exposing graph queries (P2)

### SCIP Integration

The project includes `scip.proto` for SCIP format support:
- SCIP provides language-agnostic symbol information
- Can consume SCIP indexes from various language indexers
- Enables multi-language support without custom parsers per language
- Maintained by Sourcegraph, production-ready

## Infrastructure Requirements

### Docker Compose Setup

```yaml
services:
  neo4j:
    image: neo4j:5.15.0
    ports:
      - "7474:7474"   # Web UI
      - "7687:7687"   # Bolt driver
    environment:
      - NEO4J_AUTH=neo4j/codebase123
    volumes:
      - neo4j_data:/data
```

### External Dependencies

| Dependency | Installation | Purpose |
|------------|--------------|---------|
| Docker & Docker Compose | System install | Run Neo4j server |
| scip-java | `cs install scip-java` | Generate SCIP index from Java |
| Java 17+ | System install | Run CLI tool |

## Open Questions (Resolved)

| Question | Decision |
|----------|----------|
| Graph database selection | **Neo4j server** via Docker Compose |
| Incremental updates | **File-level** granularity (simpler, sufficient) |
| SCIP vs native parsing | **SCIP-first** via scip-java (no JavaParser needed) |
| Hosting model | **Docker Compose** - easy setup, web UI included |
| Authentication | **No auth** cho MVP |

## Out of Scope (for initial release)

- Real-time file watching and auto-reindexing
- Visual graph exploration UI (Neo4j Browser available as alternative)
- Code search (full-text search within symbol bodies)
- Cross-repository dependency analysis
- Integration with specific IDEs
- Neo4j embedded mode (sử dụng server mode only)
