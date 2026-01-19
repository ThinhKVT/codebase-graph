# Tasks: Codebase Knowledge Graph

**Input**: 
- [spec-codebase-knowledge-graph.md](./spec-codebase-knowledge-graph.md)
- [plan-codebase-knowledge-graph.md](./plan-codebase-knowledge-graph.md)

**Organization**: Tasks được nhóm theo user story để có thể implement và test độc lập.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Có thể chạy song song (khác file, không dependencies)
- **[Story]**: User story liên quan (US1, US2, US3...)
- Paths theo Maven structure: `src/main/java/`, `src/test/java/`

---

## Phase 1: Setup (Shared Infrastructure) ✅ COMPLETE

**Purpose**: Khởi tạo project và cấu trúc cơ bản

- [x] T001 Tạo `docker-compose.yml` với Neo4j server configuration
- [x] T002 Cập nhật `pom.xml` với dependencies: Neo4j Driver, Picocli, Protobuf, JUnit 5, Testcontainers
- [x] T003 [P] Tạo `src/main/resources/application.properties` với Neo4j connection config
- [x] T004 [P] Tạo `src/main/resources/logback.xml` cho logging configuration
- [x] T005 Download và thêm `scip.proto` vào `src/main/proto/` (từ sourcegraph/scip)
- [x] T006 Configure Maven protobuf plugin để generate Java classes từ scip.proto

**Verify**: `docker-compose up -d` starts Neo4j, `mvn compile` succeeds ✅

---

## Phase 2: Foundational (Blocking Prerequisites) ✅ COMPLETE

**Purpose**: Core infrastructure cần hoàn thành trước khi implement user stories

### Domain Models ✅

- [x] T007 [P] Tạo `SymbolKind.java` enum trong `src/main/java/org/example/model/`
- [x] T008 [P] Tạo `ReferenceKind.java` enum trong `src/main/java/org/example/model/`
- [x] T009 [P] Tạo `Symbol.java` record trong `src/main/java/org/example/model/`
- [x] T010 [P] Tạo `SourceFile.java` record trong `src/main/java/org/example/model/`
- [x] T011 [P] Tạo `Reference.java` record trong `src/main/java/org/example/model/`
- [x] T012 [P] Tạo `Repository.java` record trong `src/main/java/org/example/model/`

### Graph Store Interface ✅

- [x] T013 Tạo `GraphStore.java` interface trong `src/main/java/org/example/graph/`
- [x] T014 Tạo `Neo4jGraphStore.java` implementation trong `src/main/java/org/example/graph/`
- [x] T015 Tạo `Neo4jGraphStoreIT.java` integration test trong `src/test/java/org/example/graph/`

### CLI Framework Setup ✅

- [x] T016 Cập nhật `Main.java` làm CLI entry point với Picocli
- [x] T017 [P] Tạo `StatusCommand.java` trong `src/main/java/org/example/cli/`

**Checkpoint**: Foundation ready - `mvn compile` passes, CLI skeleton works ✅

---

## Phase 3: User Story 1 - Index Repository (Priority: P1) 🎯 MVP ✅ COMPLETE

**Goal**: Index một Java repository và lưu symbols vào Neo4j

**Independent Test**: Chạy `index ./sample-project`, verify symbols trong Neo4j Browser

### SCIP Integration ✅

- [x] T018 Tạo `ScipRunner.java` trong `src/main/java/org/example/scip/`
  - Execute `scip-java` CLI command
  - Handle process output/errors
  - Return path to generated `.scip` file
- [x] T019 Tạo `ScipRunnerTest.java` trong `src/test/java/org/example/scip/`
  - Test với sample Java project
  - Test error handling khi scip-java not found

### SCIP Parsing ✅

- [x] T020 Tạo `ScipParser.java` trong `src/main/java/org/example/scip/`
  - Parse `.scip` protobuf file
  - Extract Index, Documents, SymbolInformation, Occurrences
- [x] T021 Tạo `ScipParserTest.java` trong `src/test/java/org/example/scip/`
  - Test với sample `.scip` file trong `src/test/resources/sample-scip/`
- [x] T022 Tạo sample `.scip` file cho testing
  - Generate programmatically trong tests
  - Sử dụng Scip.Index.newBuilder() trong test code

### SCIP to Graph Mapping ✅

- [x] T023 Tạo `ScipToGraphMapper.java` trong `src/main/java/org/example/scip/`
  - Convert SCIP SymbolInformation → domain Symbol
  - Convert SCIP Occurrence → domain Reference
  - Handle symbol kind mapping (SCIP SymbolKind → our SymbolKind)
- [x] T024 Tạo `ScipToGraphMapperTest.java` trong `src/test/java/org/example/scip/`
  - Test mapping accuracy
  - Test edge cases (anonymous classes, lambdas)

### Index Command ✅

- [x] T025 Cập nhật `IndexCommand.java` trong `src/main/java/org/example/cli/`
  - Accept repository path as argument
  - Orchestrate: validate path → run scip-java → parse → store
  - Progress reporting (files processed, symbols found)
  - Error handling với clear messages
- [x] T026 Tạo `IndexCommandIT.java` integration test trong `src/test/java/org/example/cli/`
  - End-to-end test: index sample project → verify in Neo4j

### Graph Persistence ✅

- [x] T027 Implement batch insert trong `Neo4jGraphStore.java`
  - Batch symbols/references đã có trong saveSymbols/saveReferences
  - Transaction management via try-with-resources
- [x] T028 Tạo Neo4j indexes cho query performance
  - Index on Symbol.fullyQualifiedName, Symbol.name, SourceFile.path
  - Đã implement trong createIndexes() method

**Checkpoint**: `java -jar codebase-graph.jar index ./my-project` works end-to-end ✅

---

## Phase 4: User Story 2 - Resolve Symbol References (Priority: P1) ✅ COMPLETE

**Goal**: Query để tìm tất cả usages của một symbol

**Independent Test**: Sau khi index, chạy `query refs org.example.Foo` → list các call sites

### Query Infrastructure ✅

- [x] T029 Tạo `SymbolQuery.java` trong `src/main/java/org/example/query/`
  - Find symbols by name, FQN, kind, file
  - Support wildcards và partial matching
- [x] T030 Tạo `ReferenceQuery.java` trong `src/main/java/org/example/query/`
  - Find all references to a symbol
  - Filter by reference kind
  - Include file path và line number in results
- [x] T031 Tạo `GraphQueries.java` trong `src/main/java/org/example/graph/`
  - Cypher query builders cho common patterns
  - Parameterized queries để prevent injection

### Query Command ✅

- [x] T032 Tạo `QueryCommand.java` trong `src/main/java/org/example/cli/`
  - Subcommands: `symbols`, `refs`, `deps`
  - Output formats: table (default), json
- [x] T033 Implement `query symbols` subcommand
  - Options: --name, --kind, --file, --package
  - Display: name, kind, file:line
- [x] T034 Implement `query refs` subcommand
  - Argument: symbol FQN hoặc name
  - Display: file:line, reference kind, context

### Tests ✅

- [x] T035 [P] Tạo `SymbolQueryTest.java` trong `src/test/java/org/example/query/`
- [x] T036 [P] Tạo `ReferenceQueryTest.java` trong `src/test/java/org/example/query/`
- [x] T037 Tạo `QueryCommandIT.java` integration test

**Checkpoint**: `query refs org.example.UserService` returns all call sites ✅

---

## Phase 5: User Story 3 - Query Dependencies (Priority: P1) ✅ COMPLETE

**Goal**: Query dependency relationships với transitive support

**Independent Test**: `query deps org.example.Foo --depth 2` → dependency tree

### Dependency Query ✅

- [x] T038 Tạo `DependencyQuery.java` trong `src/main/java/org/example/query/`
  - Find direct dependencies của một symbol
  - Support transitive dependencies với configurable depth
  - Return dependency graph structure
- [x] T039 Implement Cypher queries cho dependency traversal
  - EXTENDS, IMPLEMENTS, IMPORTS relationships
  - Avoid cycles trong transitive queries

### Query Command Extension ✅

- [x] T040 Implement `query deps` subcommand trong `QueryCommand.java`
  - Argument: symbol FQN
  - Options: --depth (default 1), --kind (EXTENDS, IMPLEMENTS, ALL)
  - Output: tree view hoặc JSON graph
- [x] T041 Implement `query dependents` subcommand (reverse dependencies)
  - "What depends on this symbol?"
  - Option: --reverse flag

### Tests ✅

- [x] T042 [P] Tạo `DependencyQueryTest.java` trong `src/test/java/org/example/query/`
- [x] T043 Tạo `DependencyQueryIT.java` integration test cho transitive dependency queries

**Checkpoint**: Full P1 MVP complete - index, find refs, query deps all work ✅

---

## Phase 6: User Story 4 - HTTP API (Priority: P2)

**Goal**: Expose graph queries via REST API

**Independent Test**: `curl localhost:8080/symbols?name=Foo` returns JSON

### API Server

- [x] T044 Thêm dependency: Javalin hoặc Spring Boot Web
- [x] T045 Tạo `ApiServer.java` trong `src/main/java/org/example/api/`
  - HTTP server setup
  - Route registration
  - Error handling middleware
- [x] T046 Tạo `SymbolHandler.java` trong `src/main/java/org/example/api/handlers/`
  - GET /symbols - list/search symbols
  - GET /symbols/{id} - get symbol by ID
- [x] T047 Tạo `ReferenceHandler.java` trong `src/main/java/org/example/api/handlers/`
  - GET /symbols/{id}/references
- [x] T048 Tạo `DependencyHandler.java` trong `src/main/java/org/example/api/handlers/`
  - GET /symbols/{id}/dependencies?depth=N
  - GET /symbols/{id}/dependents

---

### CLI Integration

- [x] T049 Thêm `serve` command trong CLI
  - Start API server on configurable port
  - Options: --port (default 8080)

### Tests

- [x] T050 [P] Tạo API integration tests với HTTP client

**Checkpoint**: REST API functional, can be used by external tools

---

## Phase 7: User Story 5 - Multi-language Support (Priority: P3)

**Goal**: Support Python và TypeScript via SCIP

**Independent Test**: Index Python project, verify symbols in graph

### Language Runners

- [x] T051 Refactor `ScipRunner.java` thành abstract/interface
- [x] T052 Tạo `ScipJavaRunner.java` - Java-specific runner
- [x] T053 Tạo `ScipPythonRunner.java` - Python runner (scip-python) (basic implementation)
- [x] T054 Tạo `ScipTypescriptRunner.java` - TypeScript runner (scip-typescript)
- [x] T055 Tạo `LanguageDetector.java` - detect language từ project files

### Index Command Extension

- [x] T056 Update `IndexCommand.java` để support multiple languages
  - Auto-detect language từ project (via `LanguageDetector`)
  - Option: --language to force specific language
  - Handle mixed-language projects (default: detect primary language)

### Tests

- [x] T057 [P] Tạo sample detection tests (`LanguageDetectorTest`) and Scip runner unit tests for Java (`ScipRunnerTest`)
- [x] T058 [P] Tạo sample TypeScript project cho testing (added `src/test/resources/sample-ts/package.json`)
- [ ] T059 Integration tests cho multi-language indexing (e.g., Testcontainers + language-specific scip tools) (not implemented)

**Notes**: 
- `ScipPythonRunner` has a working `runIndex` implementation that invokes `scip-python` if available. If `scip-python` is not installed, `runIndex()` will throw `ScipException.notInstalled()`.
- `ScipTypescriptRunner` is present and detects TS projects but `runIndex()` currently throws NotImplemented; implement when an appropriate `scip-typescript` CLI is available or copy the `ScipJavaRunner` process logic.

---

## Phase 8: Polish & Documentation

**Purpose**: Improvements affecting multiple user stories

- [x] T060 README.md với installation và usage instructions
- [x] T061 CONTRIBUTING.md cho developers
- [x] T062 Dockerfile để build CLI tool image
- [x] T063 GitHub Actions CI/CD pipeline
- [x] T064 Performance benchmarking với large repository (added BENCHMARKING.md)
- [x] T065 Error messages review và improvement (added ERROR_MESSAGES.md)

---

## Summary

| Phase | Tasks | User Story | Priority |
|-------|-------|------------|----------|
| 1 | T001-T006 | Setup | - |
| 2 | T007-T017 | Foundation | - |
| 3 | T018-T028 | US1: Index Repository | P1 🎯 |
| 4 | T029-T037 | US2: Resolve References | P1 |
| 5 | T038-T043 | US3: Query Dependencies | P1 |
| 6 | T044-T050 | US4: HTTP API | P2 |
| 7 | T051-T059 | US5: Multi-language | P3 |
| 8 | T060-T065 | Polish | - |

**Total Tasks**: 65  
**MVP (P1)**: Phases 1-5 (T001-T043) = 43 tasks
