# Qdrant Vector Database Integration Plan

## Mục Tiêu

Tích hợp Qdrant vector database vào Codebase Knowledge Graph để:
1. **Semantic Code Search**: Tìm kiếm code theo ngữ nghĩa (không chỉ keyword)
2. **Agentic Capabilities**: AI agent có thể plan, execute, synthesize kết quả
3. **Hybrid Search**: Qdrant (semantic) + Neo4j (graph relationships)
4. **Context Retrieval**: RAG cho code với full context

## Quyết Định MVP1

| Decision | Choice | Reason |
|----------|--------|--------|
| Embedding Model | **Ollama (nomic-embed-text)** | Local, free, private |
| Search Strategy | **Qdrant-first, Neo4j-enrich** | Semantic primary, graph for context |
| Agent Mode | **Plan → Execute → Synthesize** | Structured decision making |

## Kiến Trúc MVP1

```
┌──────────────────────────────────────────────────────────────────┐
│                         AGENT LAYER                              │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐   │
│  │  PLAN    │───▶│ EXECUTE  │───▶│SYNTHESIZE│───▶│  OUTPUT  │   │
│  │(Analyze) │    │ (Search) │    │ (Merge)  │    │ (Format) │   │
│  └──────────┘    └──────────┘    └──────────┘    └──────────┘   │
└──────────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┴───────────────┐
              ▼                               ▼
┌─────────────────────────┐     ┌─────────────────────────┐
│      QDRANT (Primary)   │     │    NEO4J (Enrichment)   │
│  ┌───────────────────┐  │     │  ┌───────────────────┐  │
│  │ Semantic Search   │  │     │  │ Graph Traversal   │  │
│  │ Code Similarity   │  │     │  │ Relationships     │  │
│  │ Context Retrieval │  │     │  │ Call chains       │  │
│  └───────────────────┘  │     │  │ Dependencies      │  │
└─────────────────────────┘     │  └───────────────────┘  │
                                └─────────────────────────┘
```

## Agent Workflow

### Phase 1: PLAN (Phân tích query)
```
Input: "Find authentication logic and its dependencies"

Agent analyzes:
├── Intent: Find code + relationships
├── Primary search: "authentication" (Qdrant)
├── Need enrichment: Yes (dependencies → Neo4j)
└── Output format: Code snippets + dependency graph
```

### Phase 2: EXECUTE (Thực thi search)
```
Step 1: Qdrant Semantic Search
├── Query: "authentication logic"
├── Results: [AuthService, LoginController, JwtUtil...]
└── Scores: [0.92, 0.87, 0.85...]

Step 2: Neo4j Enrichment (if needed)
├── For each result, get:
│   ├── CALL relationships (who calls this?)
│   ├── CONTAINS relationships (what's inside?)
│   ├── IMPLEMENTS/EXTENDS (hierarchy)
│   └── FIELD_ACCESS (dependencies)
```

### Phase 3: SYNTHESIZE (Tổng hợp)
```
Merge results:
├── Deduplicate symbols
├── Build context graph
├── Rank by relevance + connections
└── Extract code snippets with context
```

### Phase 4: OUTPUT (Format kết quả)
```
{
  "query": "Find authentication logic",
  "results": [...],
  "context_graph": {...},
  "code_snippets": [...],
  "summary": "Found 5 authentication-related components..."
}
```

## Hybrid Search Strategy

### Search Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                        USER QUERY                               │
│            "Find authentication and its callers"                │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    1. QDRANT SEMANTIC SEARCH                    │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ Query embedding → Vector search → Top K results            │ │
│  │ Results: AuthService(0.92), LoginController(0.87)...       │ │
│  └────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                 2. NEO4J GRAPH ENRICHMENT                       │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ For symbol "AuthService":                                  │ │
│  │   → CALL: LoginController.login() calls AuthService.auth() │ │
│  │   → CONTAINS: AuthService contains [authenticate, verify]  │ │
│  │   → FIELD_ACCESS: uses UserRepository, PasswordEncoder     │ │
│  └────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    3. MERGED RESULT                             │
│  {                                                              │
│    "primary_results": [...],    // From Qdrant                  │
│    "relationships": [...],      // From Neo4j                   │
│    "context_graph": {...},      // Combined view                │
│    "code_snippets": [...]       // With line numbers            │
│  }                                                              │
└─────────────────────────────────────────────────────────────────┘
```

### When to Enrich from Neo4j

| Query Intent | Qdrant | Neo4j Enrichment |
|--------------|--------|------------------|
| "Find X" | ✅ Search | ❌ Not needed |
| "Find X and its callers" | ✅ Search | ✅ CALL relationships |
| "Find X and dependencies" | ✅ Search | ✅ FIELD_ACCESS, TYPE_REF |
| "Find X implementation" | ✅ Search | ✅ CONTAINS, structure |
| "Impact of changing X" | ✅ Search | ✅ Reverse CALL graph |

## Data Model

### Qdrant Collection: `code_symbols`

```
Vector: 768-dim (nomic-embed-text)

Payload (Maximum Information):
{
  // Identity
  "symbol_id": "semanticdb maven ... AuthService#authenticate().",
  "neo4j_id": "uuid-xxx",           // Link to Neo4j
  
  // Location
  "repository": "my-project",
  "file_path": "src/main/java/com/example/AuthService.java",
  "relative_path": "com/example/AuthService.java",
  "start_line": 45,
  "end_line": 67,
  
  // Symbol Info
  "name": "authenticate",
  "fully_qualified_name": "com.example.AuthService.authenticate",
  "kind": "METHOD",
  "parent_name": "AuthService",
  "parent_kind": "CLASS",
  
  // Content for Embedding
  "signature": "public User authenticate(String username, String password)",
  "documentation": "Authenticates a user with credentials...",
  "code_content": "public User authenticate(String username, String password) {\n  ...\n}",
  
  // Metadata
  "visibility": "PUBLIC",
  "is_static": false,
  "is_test": false,
  "language": "java",
  
  // Searchable Text (combined for embedding)
  "searchable_text": "authenticate user password login credentials verify..."
}
```

### Embedding Strategy

```
searchable_text = concat(
  signature,                    // Method signature
  documentation,                // JavaDoc/comments  
  name,                         // Symbol name
  parent_name,                  // Class name
  extract_keywords(code_content) // Important identifiers
)
```

### Chunking Levels

| Level | What to Embed | Vector Count |
|-------|---------------|--------------|
| **METHOD** | Method signature + body + doc | 1 per method |
| **CLASS** | Class signature + field summary | 1 per class |
| **FILE** | Imports + class list + summary | 1 per file |

**MVP1**: Method + Class level only

## Search API Design

### POST /search/semantic
```json
{
  "query": "find authentication logic",
  "options": {
    "repository": "my-repo",
    "kinds": ["METHOD", "CLASS"],
    "limit": 10,
    "enrich": true,
    "enrich_depth": 1
  }
}
```

### POST /search/agent (Agentic Search)
```json
{
  "query": "Find authentication service and show me what calls it",
  "mode": "agent"
}

Response:
{
  "plan": {
    "intent": "find_with_callers",
    "steps": [
      {"action": "qdrant_search", "query": "authentication service"},
      {"action": "neo4j_enrich", "relationship": "CALL", "direction": "incoming"}
    ]
  },
  "execution": {
    "qdrant_results": [...],
    "neo4j_enrichment": [...]
  },
  "synthesis": {
    "summary": "Found AuthService with 3 callers",
    "primary_results": [...],
    "callers": [
      {"name": "LoginController.login", "file": "...", "line": 23}
    ],
    "context_graph": {
      "nodes": [...],
      "edges": [...]
    }
  }
}
```

## Agent Architecture

### CodeSearchAgent

```java
public class CodeSearchAgent {
    private final QdrantService qdrant;
    private final Neo4jGraphStore neo4j;
    private final QueryAnalyzer analyzer;
    
    public AgentResponse search(String query) {
        // 1. PLAN
        SearchPlan plan = analyzer.analyze(query);
        
        // 2. EXECUTE
        List<SearchResult> qdrantResults = executeQdrantSearch(plan);
        
        // 3. ENRICH (if needed)
        if (plan.needsEnrichment()) {
            enrichFromNeo4j(qdrantResults, plan.getEnrichmentType());
        }
        
        // 4. SYNTHESIZE
        return synthesize(qdrantResults, plan);
    }
}
```

### Query Intent Detection

| Query Pattern | Intent | Actions |
|---------------|--------|---------|
| "find X" | `FIND` | Qdrant only |
| "find X and callers" | `FIND_CALLERS` | Qdrant + Neo4j(CALL) |
| "find X dependencies" | `FIND_DEPS` | Qdrant + Neo4j(FIELD_ACCESS) |
| "find X implementation" | `FIND_IMPL` | Qdrant + Neo4j(CONTAINS) |
| "similar to X" | `FIND_SIMILAR` | Qdrant similarity |
| "impact of X" | `IMPACT` | Qdrant + Neo4j(reverse graph) |

## MVP1 Tasks Breakdown

### Phase 1: Infrastructure Setup

| Task | Description | Files |
|------|-------------|-------|
| 1.1 | Add Qdrant + Ollama to docker-compose.yml | `docker-compose.yml` |
| 1.2 | Create QdrantConfig class | `config/QdrantConfig.java` |
| 1.3 | Create QdrantClient wrapper | `vector/QdrantClientWrapper.java` |
| 1.4 | Create collection initialization | `vector/QdrantCollectionManager.java` |

### Phase 2: Embedding Service

| Task | Description | Files |
|------|-------------|-------|
| 2.1 | EmbeddingService interface | `embedding/EmbeddingService.java` |
| 2.2 | OllamaEmbeddingService | `embedding/OllamaEmbeddingService.java` |
| 2.3 | CodeContentBuilder | `embedding/CodeContentBuilder.java` |
| 2.4 | Test embedding generation | `test/.../EmbeddingServiceTest.java` |

### Phase 3: Vector Indexing

| Task | Description | Files |
|------|-------------|-------|
| 3.1 | VectorIndexer class | `vector/VectorIndexer.java` |
| 3.2 | Integrate into IndexCommand | `cli/IndexCommand.java` |
| 3.3 | Symbol to Qdrant point mapper | `vector/SymbolPointMapper.java` |
| 3.4 | Batch upsert implementation | `vector/QdrantClientWrapper.java` |

### Phase 4: Hybrid Search

| Task | Description | Files |
|------|-------------|-------|
| 4.1 | QueryAnalyzer (intent detection) | `search/QueryAnalyzer.java` |
| 4.2 | SemanticSearchService | `search/SemanticSearchService.java` |
| 4.3 | Neo4jEnricher | `search/Neo4jEnricher.java` |
| 4.4 | ResultSynthesizer | `search/ResultSynthesizer.java` |

### Phase 5: Agent & API

| Task | Description | Files |
|------|-------------|-------|
| 5.1 | CodeSearchAgent | `agent/CodeSearchAgent.java` |
| 5.2 | SearchPlan model | `agent/SearchPlan.java` |
| 5.3 | /search/semantic endpoint | `api/handlers/SearchHandler.java` |
| 5.4 | /search/agent endpoint | `api/handlers/AgentSearchHandler.java` |

## Dependencies

```xml
<!-- Qdrant Java Client -->
<dependency>
    <groupId>io.qdrant</groupId>
    <artifactId>client</artifactId>
    <version>1.9.1</version>
</dependency>

<!-- HTTP Client for Ollama -->
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>okhttp</artifactId>
    <version>4.12.0</version>
</dependency>

<!-- JSON processing -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.16.1</version>
</dependency>
```

## Docker Services (MVP1)

```yaml
services:
  qdrant:
    image: qdrant/qdrant:latest
    ports:
      - "6333:6333"    # REST API
      - "6334:6334"    # gRPC
    volumes:
      - qdrant_data:/qdrant/storage

  ollama:
    image: ollama/ollama:latest
    ports:
      - "11434:11434"
    volumes:
      - ollama_data:/root/.ollama
    # Pull model on start
    entrypoint: ["/bin/sh", "-c", "ollama serve & sleep 5 && ollama pull nomic-embed-text && wait"]
```

## File Structure (New)

```
src/main/java/org/example/
├── agent/
│   ├── CodeSearchAgent.java
│   ├── QueryIntent.java
│   └── SearchPlan.java
├── config/
│   └── QdrantConfig.java
├── embedding/
│   ├── EmbeddingService.java
│   ├── OllamaEmbeddingService.java
│   └── CodeContentBuilder.java
├── search/
│   ├── QueryAnalyzer.java
│   ├── SemanticSearchService.java
│   ├── Neo4jEnricher.java
│   └── ResultSynthesizer.java
├── vector/
│   ├── QdrantClientWrapper.java
│   ├── QdrantCollectionManager.java
│   ├── VectorIndexer.java
│   └── SymbolPointMapper.java
└── api/handlers/
    ├── SearchHandler.java
    └── AgentSearchHandler.java
```

## Success Criteria (MVP1)

- [ ] Qdrant + Ollama running in docker-compose
- [ ] Embeddings generated during indexing
- [ ] `/search/semantic?q=authentication` returns relevant code
- [ ] `/search/agent` returns enriched results with relationships
- [ ] Search latency < 1s (MVP acceptable)
- [ ] Basic intent detection working (find, find_callers, find_deps)

## Example Usage (MVP1)

```bash
# 1. Start all services
docker-compose up -d

# 2. Index a project (now includes vector indexing)
.\cg.ps1 index my-project

# 3. Semantic search
curl "http://localhost:8080/search/semantic?q=authentication"

# 4. Agent search with enrichment
curl -X POST "http://localhost:8080/search/agent" \
  -H "Content-Type: application/json" \
  -d '{"query": "find authentication and its callers"}'
```
