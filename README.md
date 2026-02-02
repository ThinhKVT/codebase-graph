# Codebase Knowledge Graph

A tool to index source repositories using SCIP, store symbols and references in Neo4j, and expose queries via a CLI and HTTP API.

## Features

- **Multi-language support**: Java, Go, Python, TypeScript
- **Auto Java version detection**: JDK 8, 11, 17, 21 (auto-set JAVA_HOME)
- **Rich relationship mapping**: CALL, CONTAINS, IMPLEMENTS, EXTENDS, FIELD_ACCESS, TYPE_REF
- **Source location tracking**: startLine, endLine for each symbol
- **Dependency Injection detection**: Spring @Autowired, Go constructors, Python FastAPI
- **Versioned index storage**: Track indexing history with metadata
- **Auto CRLF fix**: Handles Windows line endings in mvnw scripts

## Quick Start (Docker - Recommended)

### 1. Start services

```bash
docker-compose up -d
```

This starts:
- `codebase-graph-neo4j` - Graph database (http://localhost:7474)
- `codebase-graph-indexer` - Indexer with scip-java (multi-JDK support)

### 2. Add project to index

```bash
# Clone into demo/ folder
git clone https://github.com/user/repo.git demo/repo-name
```

### 3. Index using helper script

```powershell
# List available projects
.\cg.ps1 ls

# Index a project
.\cg.ps1 index repo-name

# Index with custom name
.\cg.ps1 index path/to/project my-project-name

# List indexed projects
.\cg.ps1 list

# Check status
.\cg.ps1 status

# Open shell in container
.\cg.ps1 shell
```

Or with docker exec directly:
```bash
docker exec codebase-graph-indexer java -jar /app/codebase-graph.jar \
  index /projects/repo-name \
  --neo4j-uri bolt://neo4j:7687 \
  --neo4j-password codebase123
```

### 4. Query in Neo4j Browser

Open http://localhost:7474 (user: `neo4j`, password: `codebase123`)

```cypher
// View all indexed repositories
MATCH (r:Repository) RETURN r

// Find all classes with line numbers
MATCH (s:Symbol) 
WHERE s.kind = 'CLASS' 
RETURN s.name, s.filePath, s.startLine, s.endLine

// Find method calls
MATCH (a:Symbol)-[r:REFERENCES {kind: 'CALL'}]->(b:Symbol) 
RETURN a.name as caller, b.name as callee

// Find class members (CONTAINS)
MATCH (c:Symbol {kind: 'CLASS'})-[r:REFERENCES {kind: 'CONTAINS'}]->(m:Symbol)
RETURN c.name as class, collect(m.name) as members

// Find field accesses
MATCH (a:Symbol)-[r:REFERENCES {kind: 'FIELD_ACCESS'}]->(b:Symbol)
RETURN a.name, b.name

// Relationship types breakdown
MATCH ()-[r:REFERENCES]->()
RETURN r.kind as type, count(*) as count
ORDER BY count DESC
```

## Data Model

### Nodes

| Label | Properties |
|-------|------------|
| `Repository` | id, name, path, language, lastIndexedAt |
| `SourceFile` | path, relativePath, language |
| `Symbol` | id, name, kind, filePath, startLine, endLine, signature, parentId |

### Relationships

All stored as `REFERENCES` with `kind` property:

| Kind | Description |
|------|-------------|
| `CALL` | Method invocation |
| `CONTAINS` | Parent contains child (class→method) |
| `FIELD_ACCESS` | Field read/write |
| `TYPE_REF` | Type reference |
| `IMPLEMENTS` | Interface implementation |
| `EXTENDS` | Class inheritance |
| `INJECTS` | Dependency injection |

### Symbol Kinds

`CLASS`, `INTERFACE`, `ENUM`, `METHOD`, `CONSTRUCTOR`, `FIELD`, `STATIC_METHOD`, `PARAMETER`, `VARIABLE`

**Full documentation**: [doc/indexing-workflow/WORKFLOW.md](doc/indexing-workflow/WORKFLOW.md)

---

## Local Development

Requirements:
- Java 17
- Maven
- Docker

### 1. Start Neo4j

```bash
docker-compose up -d neo4j
```

### 2. Build

```bash
mvn package
```

### 3. Index a repository

```bash
java -jar target/codebase-graph-1.0-SNAPSHOT.jar index /path/to/repo --name my-repo
```

### 4. Start API server

```bash
java -jar target/codebase-graph-1.0-SNAPSHOT.jar serve --port 8080
```

API endpoints:
- `GET /health` - Health check
- `GET /stats` - Statistics
- `GET /symbols?name=Foo` - Search symbols
- `GET /symbols/{id}` - Get symbol details
- `GET /symbols/{id}/references` - Get references
- `GET /symbols/{id}/dependencies?depth=N` - Get dependencies

Running tests
-------------

Run unit tests:

```bash
mvn test
```

Run a single test class:

```bash
mvn -Dtest=org.example.api.ApiServerIT test
```

Docker image
------------

A `Dockerfile` is provided to build a runnable image of the CLI tool (fat/uber-jar built by Maven Shade plugin).

```bash
# build jar and image
mvn package -DskipTests
docker build -t codebase-graph:latest .

# run (assumes Neo4j is reachable)
docker run --rm -e NEO4J_URI=bolt://host.docker.internal:7687 codebase-graph:latest serve --port 8080
```

Configuration
-------------
Configuration is read from `src/main/resources/application.properties` by default. You can override Neo4j connection settings via system properties or environment variables in the CLI (see `IndexCommand` and `ServeCommand` options).

Contributing
------------
See `CONTRIBUTING.md` for guidelines on developing and testing locally.

License
-------
(Choose an appropriate license)

