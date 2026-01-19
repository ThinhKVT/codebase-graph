# Implementation Plan: Codebase Knowledge Graph

**Branch**: `001-codebase-knowledge-graph` | **Date**: January 18, 2026 | **Spec**: [spec-codebase-knowledge-graph.md](./spec-codebase-knowledge-graph.md)

## Summary

Xây dựng một **Knowledge Graph** để index codebase, trích xuất **symbols** (classes, methods, fields), resolve **references**, và expose **dependency relationships** thông qua CLI và API.

**Technical Approach**: Sử dụng **scip-java** để generate SCIP index từ Java source code, parse SCIP protobuf để extract symbols/references, lưu trữ trong **Neo4j server** (chạy via Docker Compose).

## Technical Context

**Language/Version**: Java 17 (Maven project)  
**Primary Dependencies**: 
- **scip-java** (external CLI tool) - Generate SCIP index từ Java source code
- Protobuf 3.x - Parse SCIP index file (`.scip` format)
- Neo4j Java Driver 5.x (kết nối Neo4j server)
- Picocli 4.x (CLI framework)

**Why scip-java instead of JavaParser?**
- scip-java đã có sẵn symbol resolution chính xác (dùng SemanticDB/compiler)
- SCIP là format chuẩn, dễ mở rộng sang Python (scip-python), TypeScript (scip-typescript)
- Không cần tự implement parsing và symbol resolution logic
- Production-ready, được maintain bởi Sourcegraph

**Storage**: Neo4j **server mode** (via Docker Compose)
- Chạy Neo4j server trong Docker container
- Kết nối qua Bolt protocol (bolt://localhost:7687)
- Web UI tại http://localhost:7474 để visualize graph
- Dữ liệu persist trong Docker volume

**Testing**: JUnit 5, Testcontainers (Neo4j container cho integration tests)  
**Target Platform**: JVM (cross-platform CLI tool)  
**Project Type**: Single project - CLI tool kết nối Neo4j server  
**Performance Goals**: 
- Indexing: ≥1,000 files/minute
- Query: <100ms cho symbol lookup (up to 50K symbols)

**Constraints**: 
- RAM: <4GB cho repositories up to 100K files
- Incremental indexing để tránh re-index toàn bộ

**Scale/Scope**: Hỗ trợ repositories up to 100,000 source files

## Constitution Check

| Principle | Status | Notes |
|-----------|--------|-------|
| Library-First | ✅ PASS | Core indexer là standalone library, CLI wrap library |
| CLI Interface | ✅ PASS | CLI commands: `index`, `query`, `status` |
| Test-First | ✅ PASS | Contract tests cho graph operations, integration tests với Neo4j |
| Simplicity | ✅ PASS | Start với Java-only, Docker Compose setup, no auth |

## Project Structure

### Documentation

```text
.specify/memory/
├── spec-codebase-knowledge-graph.md    # Feature specification
├── plan-codebase-knowledge-graph.md    # This file
├── tasks-codebase-knowledge-graph.md   # Task breakdown (Phase 2)
└── research/                           # Technical research notes
    └── scip-format.md
```

### Source Code (repository root)

```text
├── docker-compose.yml              # Neo4j server setup
├── pom.xml
src/
├── main/
│   ├── java/org/example/
│   │   ├── Main.java                   # CLI entry point
│   │   ├── cli/                        # CLI commands (Picocli)
│   │   │   ├── IndexCommand.java       # Orchestrate: run scip-java → parse → store
│   │   │   ├── QueryCommand.java
│   │   │   └── StatusCommand.java
│   │   ├── scip/                       # SCIP format handling
│   │   │   ├── ScipRunner.java         # Execute scip-java CLI
│   │   │   ├── ScipParser.java         # Parse .scip protobuf file
│   │   │   └── ScipToGraphMapper.java  # Map SCIP entities → Graph nodes
│   │   ├── model/                      # Domain entities
│   │   │   ├── Repository.java
│   │   │   ├── SourceFile.java
│   │   │   ├── Symbol.java
│   │   │   ├── SymbolKind.java         # Enum: CLASS, METHOD, FIELD...
│   │   │   ├── Reference.java
│   │   │   └── ReferenceKind.java      # Enum: CALL, IMPORT, EXTENDS...
│   │   ├── graph/                      # Graph database layer
│   │   │   ├── GraphStore.java         # Interface
│   │   │   ├── Neo4jGraphStore.java    # Neo4j driver implementation
│   │   │   └── GraphQueries.java       # Query builders
│   │   ├── query/                      # Query engine
│   │   │   ├── SymbolQuery.java
│   │   │   ├── ReferenceQuery.java
│   │   │   └── DependencyQuery.java
│   │   └── api/                        # HTTP/gRPC API (P2)
│   │       ├── ApiServer.java
│   │       └── handlers/
│   ├── proto/
│   │   └── scip.proto                  # SCIP protocol definition (from sourcegraph/scip)
│   └── resources/
│       ├── application.properties      # Neo4j connection config
│       └── logback.xml
└── test/
    ├── java/org/example/
    │   ├── scip/
    │   │   ├── ScipParserTest.java
    │   │   └── ScipToGraphMapperTest.java
    │   ├── graph/
    │   │   └── Neo4jGraphStoreIT.java  # Integration test với Testcontainers
    │   └── query/
    │       └── DependencyQueryTest.java
    └── resources/
        └── sample-scip/                # Sample .scip files for testing
            └── sample-index.scip
```

**Structure Decision**: Single project với Maven. Sử dụng package structure để tách biệt concerns: `cli`, `scip`, `model`, `graph`, `query`, `api`.

## Docker Compose Setup

### docker-compose.yml

```yaml
version: '3.8'

services:
  neo4j:
    image: neo4j:5.15.0
    container_name: codebase-graph-neo4j
    ports:
      - "7474:7474"   # HTTP (Browser UI)
      - "7687:7687"   # Bolt (Driver connection)
    environment:
      - NEO4J_AUTH=neo4j/codebase123   # Username/password
      - NEO4J_PLUGINS=["apoc"]          # Optional: APOC procedures
      - NEO4J_dbms_memory_heap_initial__size=512m
      - NEO4J_dbms_memory_heap_max__size=2G
    volumes:
      - neo4j_data:/data
      - neo4j_logs:/logs
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:7474"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  neo4j_data:
  neo4j_logs:
```

### Neo4j Connection Config

```properties
# application.properties
neo4j.uri=bolt://localhost:7687
neo4j.username=neo4j
neo4j.password=codebase123
```

## Component Design

### 1. Domain Model

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│ Repository  │────▶│ SourceFile  │────▶│   Symbol    │
└─────────────┘     └─────────────┘     └─────────────┘
      │                                        │
      │                                        ▼
      │                                 ┌─────────────┐
      │                                 │  Reference  │
      │                                 └─────────────┘
      ▼
┌─────────────────────────────────────────────────────┐
│                Neo4j Server (Docker)                 │
│  (Symbol)-[:DEFINED_IN]->(SourceFile)               │
│  (Symbol)-[:CONTAINS]->(Symbol)                     │
│  (Symbol)-[:EXTENDS|IMPLEMENTS|REFERENCES]->(Symbol)│
└─────────────────────────────────────────────────────┘
```

### 2. Indexing Pipeline (Updated for scip-java)

```
┌──────────────┐    ┌─────────────┐    ┌─────────────┐    ┌───────────────────┐    ┌─────────────────┐
│ Java Project │───▶│  scip-java  │───▶│ .scip file  │───▶│   ScipParser      │───▶│  Neo4j Server   │
│   (source)   │    │   (CLI)     │    │ (protobuf)  │    │ ScipToGraphMapper │    │   (Docker)      │
└──────────────┘    └─────────────┘    └─────────────┘    └───────────
