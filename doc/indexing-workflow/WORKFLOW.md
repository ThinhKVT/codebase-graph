# Codebase Graph - Indexing Workflow

## Tổng Quan

Codebase Graph là công cụ phân tích mã nguồn, tạo knowledge graph từ code để truy vấn symbols, dependencies và references.

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Source    │────▶│  SCIP Index │────▶│   Parser    │────▶│   Neo4j     │
│    Code     │     │  (scip-java)│     │   Mapper    │     │   Graph     │
└─────────────┘     └─────────────┘     └─────────────┘     └─────────────┘
```

## Yêu Cầu

### Ngôn ngữ hỗ trợ

| Language | Build Tool Required | Runner |
|----------|---------------------|--------|
| Java | Maven hoặc Gradle | `ScipJavaRunner` |
| Go | go.mod | `ScipGoRunner` |
| Python | requirements.txt / pyproject.toml | `ScipPythonRunner` |
| TypeScript | package.json | `ScipTypescriptRunner` |

### Java Version Support

Tự động detect từ `pom.xml` hoặc `build.gradle`:
- JDK 8, 11, 17, 21
- Auto-set `JAVA_HOME` tương ứng

## Quick Start

### 1. Khởi động services

```bash
docker-compose up -d
```

Services:
- `codebase-graph-neo4j` - Graph database (port 7474, 7687)
- `codebase-graph-indexer` - Indexer với scip-java

### 2. Thêm project cần index

Copy/clone project vào folder `demo/`:

```bash
# Clone từ git
git clone https://github.com/user/repo.git demo/repo-name

# Hoặc copy
cp -r /path/to/project demo/project-name
```

### 3. Index project

```powershell
# Sử dụng helper script
.\cg.ps1 index project-name

# Hoặc với tên custom
.\cg.ps1 index path/to/project custom-name
```

### 4. Xem kết quả

```powershell
# List indices
.\cg.ps1 list

# Check status
.\cg.ps1 status
```

## CLI Commands

### Helper Script (cg.ps1)

```powershell
.\cg.ps1 ls                              # List projects trong /projects
.\cg.ps1 index <project> [name]          # Index một project
.\cg.ps1 list                            # List stored indices  
.\cg.ps1 status                          # Check Neo4j connection
.\cg.ps1 shell                           # Vào bash container
```

### Docker Exec trực tiếp

```bash
# Index
docker exec codebase-graph-indexer java -jar /app/codebase-graph.jar \
  index /projects/PROJECT_NAME \
  --name "project-name" \
  --neo4j-uri bolt://neo4j:7687 \
  --neo4j-password codebase123 \
  -v

# Query
docker exec codebase-graph-indexer java -jar /app/codebase-graph.jar \
  query symbols --name "ClassName" \
  --neo4j-uri bolt://neo4j:7687 \
  --neo4j-password codebase123
```

## Kết Quả Sau Index

### 1. SCIP Index File

```
/root/.codebase-graph/
├── indices/
│   └── project-name-20260201120000.scip    # Binary index file
└── metadata/
    └── project-name-20260201120000.json    # Metadata JSON
```

### 2. Neo4j Graph Data

#### Nodes (Đỉnh)

| Label | Mô tả | Properties |
|-------|-------|------------|
| `Repository` | Project đã index | name, path, indexedAt |
| `SourceFile` | File source code | path, language, relativePath |
| `Symbol` | Class, method, field... | name, kind, signature, isStatic, visibility |

#### Relationships (Cạnh)

| Type | Mô tả | Example |
|------|-------|---------|
| `CONTAINS` | File chứa symbol, Class chứa method | File → Class, Class → Method |
| `CALL` | Gọi method | methodA() → methodB() |
| `REFERENCE` | Tham chiếu đến symbol | Code → ClassName |
| `IMPLEMENTS` | Implement interface | ClassA → InterfaceB |
| `EXTENDS` | Kế thừa class | ChildClass → ParentClass |
| `FIELD_ACCESS` | Truy cập field | method → this.field |
| `TYPE_REF` | Tham chiếu kiểu dữ liệu | param: TypeName |
| `INJECTS` | Dependency Injection | @Autowired field |

#### Symbol Kinds

```
CLASS, INTERFACE, ENUM, METHOD, CONSTRUCTOR, FIELD, 
PARAMETER, LOCAL_VARIABLE, PACKAGE, MODULE,
STRUCT, TRAIT, STATIC_METHOD, LAMBDA, PROPERTY
```

### 3. Ví dụ Query Neo4j

Truy cập Neo4j Browser: http://localhost:7474

```cypher
-- Xem tất cả repositories
MATCH (r:Repository) RETURN r

-- Xem classes trong một repo
MATCH (r:Repository {name: 'keycloak-admin'})-[:CONTAINS]->(f:SourceFile)-[:CONTAINS]->(s:Symbol)
WHERE s.kind = 'CLASS'
RETURN s.name, f.relativePath

-- Tìm method calls
MATCH (caller:Symbol)-[:CALL]->(callee:Symbol)
RETURN caller.name, callee.name LIMIT 20

-- Tìm implementations
MATCH (impl:Symbol)-[:IMPLEMENTS]->(interface:Symbol)
RETURN impl.name, interface.name

-- Tìm tất cả symbols trong một file
MATCH (f:SourceFile {relativePath: 'src/main/java/com/example/MyClass.java'})-[:CONTAINS]->(s:Symbol)
RETURN s.name, s.kind

-- Tìm dependencies của một class
MATCH (c:Symbol {name: 'UserService', kind: 'CLASS'})-[r]->(dep:Symbol)
RETURN type(r), dep.name, dep.kind
```

## Workflow Chi Tiết

### Indexing Pipeline

```
1. VALIDATE
   └── Check build tool (pom.xml / build.gradle)
   └── Detect language
   └── Validate project structure

2. PRE-PROCESS  
   └── Fix mvnw CRLF line endings
   └── Detect Java version from build file
   └── Set JAVA_HOME

3. SCIP INDEX
   └── Run scip-java/scip-go/scip-python
   └── Compile project
   └── Generate .scip binary file

4. PARSE
   └── Parse .scip protobuf
   └── Extract documents, symbols, occurrences

5. MAP TO GRAPH
   └── Create Symbol nodes
   └── Create SourceFile nodes
   └── Map relationships (CALL, CONTAINS, IMPLEMENTS...)
   └── Apply language-specific strategies

6. STORE
   └── Persist to Neo4j
   └── Save metadata JSON
```

### Multi-Project Indexing

Với projects có nhiều modules (như keycloak-example-demo):

```powershell
# Index từng module
.\cg.ps1 index keycloak-example-demo/admin/admin keycloak-admin
.\cg.ps1 index keycloak-example-demo/gateway/gateway keycloak-gateway
.\cg.ps1 index keycloak-example-demo/partner/partner keycloak-partner
.\cg.ps1 index keycloak-example-demo/registry/registry keycloak-registry
```

## Troubleshooting

### Lỗi thường gặp

| Lỗi | Nguyên nhân | Giải pháp |
|-----|-------------|-----------|
| `cannot run program mvnw` | CRLF line endings | Auto-fixed, hoặc `dos2unix mvnw` |
| `No pom.xml or build.gradle` | Thiếu build tool | Thêm pom.xml |
| `Java version mismatch` | JDK không phù hợp | Auto-detect, hoặc check pom.xml |
| `Neo4j connection refused` | Neo4j chưa ready | Chờ healthcheck pass |

### Logs

```bash
# Xem logs indexer
docker-compose logs -f indexer

# Xem logs Neo4j
docker-compose logs -f neo4j
```

## File Structure

```
codebase-graph/
├── demo/                           # Mount vào /projects
│   ├── project-1/
│   ├── project-2/
│   └── ...
├── docker-compose.yml              # Services config
├── cg.ps1                          # Helper script
├── src/main/java/org/example/
│   ├── cli/                        # CLI commands
│   ├── scip/
│   │   ├── runner/                 # Language runners
│   │   ├── mapper/                 # Mapping strategies
│   │   └── storage/                # Index storage
│   ├── graph/                      # Neo4j integration
│   └── model/                      # Domain models
└── doc/                            # Documentation
```
