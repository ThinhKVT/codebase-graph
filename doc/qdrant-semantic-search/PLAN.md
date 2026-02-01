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

### Phase 1: Infrastructure Setup ✅ COMPLETED

| Task | Status | Description | Files |
|------|--------|-------------|-------|
| 1.1 | ✅ | Add Qdrant + Ollama to docker-compose.yml | `docker-compose.yml` |
| 1.2 | ✅ | Create QdrantConfig class | `config/QdrantConfig.java` |
| 1.3 | ✅ | Create OllamaConfig class | `config/OllamaConfig.java` |
| 1.4 | ✅ | Create VectorStore interface | `vector/VectorStore.java` |
| 1.5 | ✅ | Create QdrantVectorStore implementation | `vector/QdrantVectorStore.java` |
| 1.6 | ✅ | Create VectorPoint model | `vector/VectorPoint.java` |
| 1.7 | ✅ | Create SearchResult model | `vector/SearchResult.java` |

### Phase 2: Embedding Service ✅ COMPLETED

| Task | Status | Description | Files |
|------|--------|-------------|-------|
| 2.1 | ✅ | EmbeddingService interface | `embedding/EmbeddingService.java` |
| 2.2 | ✅ | OllamaEmbeddingService | `embedding/OllamaEmbeddingService.java` |
| 2.3 | ✅ | EmbeddingException | `embedding/EmbeddingException.java` |
| 2.4 | ✅ | CodeEmbedder (content builder + indexer) | `embedding/CodeEmbedder.java` |

### Phase 3: CLI Commands ✅ COMPLETED

| Task | Status | Description | Files |
|------|--------|-------------|-------|
| 3.1 | ✅ | EmbedCommand (embed symbols) | `cli/EmbedCommand.java` |
| 3.2 | ✅ | SearchCommand (semantic search) | `cli/SearchCommand.java` |
| 3.3 | ✅ | Register commands in Main | `Main.java` |

### Phase 4: Hybrid Search ✅ COMPLETED

| Task | Status | Description | Files |
|------|--------|-------------|-------|
| 4.1 | ✅ | SemanticSearchService | `search/SemanticSearchService.java` |
| 4.2 | ✅ | SemanticSearchResult model | `search/SemanticSearchResult.java` |
| 4.3 | ✅ | Neo4j enrichment (callers/callees) | Integrated in SemanticSearchService |
| 4.4 | ✅ | QueryAnalyzer (intent detection) | `search/QueryAnalyzer.java` |
| 4.5 | ✅ | ResultSynthesizer | `search/ResultSynthesizer.java` |

### Phase 5: Agent & API ✅ COMPLETED

| Task | Status | Description | Files |
|------|--------|-------------|-------|
| 5.1 | ✅ | CodeSearchAgent | `agent/CodeSearchAgent.java` |
| 5.2 | ✅ | SearchPlan model | `agent/SearchPlan.java` |
| 5.3 | ✅ | /search/semantic endpoint | `api/handlers/SearchHandler.java` |
| 5.4 | ✅ | /search/agent endpoint | `api/handlers/SearchHandler.java` |
| 5.5 | ✅ | AgentSearchCommand (CLI) | `cli/AgentSearchCommand.java` |

## Progress Summary

| Phase | Status | Description |
|-------|--------|-------------|
| Phase 1 | ✅ | Infrastructure (Docker, Config, VectorStore) |
| Phase 2 | ✅ | Embedding Service (Ollama integration) |
| Phase 3 | ✅ | CLI Commands (embed, search, agent) |
| Phase 4 | ✅ | Hybrid Search (QueryAnalyzer, ResultSynthesizer) |
| Phase 5 | ✅ | Agent & API (CodeSearchAgent, REST endpoints) |

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

---

## Phase 6: Code Retrieval API (MVP1 Extension) ✅ COMPLETED

> **Lý do:** RAG cần actual source code, không chỉ metadata. Agent cần đọc code để trả lời.

### API Endpoints

#### GET /symbols/{symbolId}/source

Lấy source code theo Symbol ID từ Neo4j.

```json
// Request
GET /symbols/uuid-xxx/source

// Response
{
  "symbol_id": "uuid-xxx",
  "qualified_name": "com.example.AuthService.authenticate",
  "file_path": "src/main/java/com/example/AuthService.java",
  "start_line": 45,
  "end_line": 67,
  "source_code": "public User authenticate(String username, String password) {\n    // Validate input\n    if (username == null || password == null) {\n        throw new IllegalArgumentException(\"Credentials required\");\n    }\n    \n    User user = userRepository.findByUsername(username);\n    if (user != null && passwordEncoder.matches(password, user.getPassword())) {\n        return user;\n    }\n    return null;\n}",
  "context": {
    "before": "// 5 lines before...",
    "after": "// 5 lines after..."
  }
}
```

#### GET /code

Lấy source code theo qualified name.

```json
// Request
GET /code?qualified_name=com.example.AuthService.authenticate&context_lines=5

// Response (same as above)
```

#### POST /code/batch

Batch retrieval cho nhiều symbols.

```json
// Request
POST /code/batch
{
  "symbol_ids": ["uuid-1", "uuid-2", "uuid-3"],
  "context_lines": 3
}

// Response
{
  "results": [
    { "symbol_id": "uuid-1", "source_code": "...", ... },
    { "symbol_id": "uuid-2", "source_code": "...", ... },
    { "symbol_id": "uuid-3", "source_code": "...", ... }
  ],
  "errors": []
}
```

### Implementation

#### SourceCodeExtractor.java

```java
public class SourceCodeExtractor {
    
    public CodeSnippet extract(String filePath, int startLine, int endLine, int contextLines) {
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            throw new FileNotFoundException("Source file not found: " + filePath);
        }
        
        List<String> allLines = Files.readAllLines(path);
        
        // Calculate range with context
        int contextStart = Math.max(0, startLine - contextLines - 1);
        int contextEnd = Math.min(allLines.size(), endLine + contextLines);
        
        // Extract lines
        List<String> codeLines = allLines.subList(startLine - 1, endLine);
        List<String> beforeContext = allLines.subList(contextStart, startLine - 1);
        List<String> afterContext = allLines.subList(endLine, contextEnd);
        
        return new CodeSnippet(
            String.join("\n", codeLines),
            String.join("\n", beforeContext),
            String.join("\n", afterContext),
            startLine,
            endLine
        );
    }
}
```

#### CodeRetrievalHandler.java

```java
public class CodeRetrievalHandler implements HttpHandler {
    private final Neo4jGraphStore graphStore;
    private final SourceCodeExtractor extractor;
    
    @Override
    public void handle(HttpExchange exchange) {
        String symbolId = extractPathParam(exchange, "symbolId");
        
        // 1. Get symbol location from Neo4j
        Symbol symbol = graphStore.findSymbolById(symbolId);
        if (symbol == null) {
            sendError(exchange, 404, "Symbol not found");
            return;
        }
        
        // 2. Extract source code
        CodeSnippet snippet = extractor.extract(
            symbol.getFilePath(),
            symbol.getStartLine(),
            symbol.getEndLine(),
            5  // default context lines
        );
        
        // 3. Return response
        sendJson(exchange, new CodeResponse(symbol, snippet));
    }
}
```

### Tasks

| Task | Status | Description | Files |
|------|--------|-------------|-------|
| 6.1 | ✅ | CodeSnippet model | `model/CodeSnippet.java` |
| 6.2 | ✅ | SourceCodeExtractor service | `service/SourceCodeExtractor.java` |
| 6.3 | ✅ | GET /symbols/{id}/source endpoint | `api/handlers/CodeRetrievalHandler.java` |
| 6.4 | ✅ | GET /code?qualified_name endpoint | `api/handlers/CodeRetrievalHandler.java` |
| 6.5 | ✅ | POST /code/batch endpoint | `api/handlers/CodeRetrievalHandler.java` |

### File Structure (Addition)

```
src/main/java/org/example/
├── model/
│   └── CodeSnippet.java          // NEW
├── service/
│   └── SourceCodeExtractor.java  // NEW
└── api/handlers/
    └── CodeRetrievalHandler.java // NEW
```

### Success Criteria

- [x] `GET /symbols/{id}/source` returns actual code
- [x] `GET /code?qualified_name=...` works with FQN
- [x] Context lines configurable (default 5)
- [x] Batch retrieval for multiple symbols
- [x] Error handling for missing files

**Note:** Source roots are taken from repository paths in the graph; add `--source-root <path>` when starting `serve` if needed.

---

## Phase 7: Natural Language to Cypher (MVP2) ✅ COMPLETED

> **Lý do:** QueryAnalyzer chỉ có pattern matching cố định. LLM có thể handle queries phức tạp hơn.

### So Sánh Approach

| Aspect | QueryAnalyzer (MVP1) | NL-to-Cypher (MVP2) |
|--------|---------------------|---------------------|
| Method | Regex patterns | LLM generation |
| Flexibility | Fixed patterns | Any query |
| Accuracy | High cho known patterns | Depends on LLM |
| Speed | Fast | Slower (LLM call) |
| Use case | Common queries | Complex/custom queries |

### Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     QUERY ROUTING                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  User Query ──▶ QueryRouter ──┬──▶ QueryAnalyzer (fast path)    │
│                               │    - Pattern matched             │
│                               │    - Use predefined Cypher       │
│                               │                                  │
│                               └──▶ NLToCypherService (slow path) │
│                                    - No pattern match            │
│                                    - LLM generates Cypher        │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### API Endpoint

#### POST /query/natural

```json
// Request
POST /query/natural
{
  "query": "Find all classes that have more than 5 methods and are in the service package",
  "options": {
    "force_llm": false,  // true = skip pattern matching
    "validate": true,    // validate generated Cypher
    "explain": true      // include explanation
  }
}

// Response
{
  "original_query": "Find all classes that have more than 5 methods...",
  "method": "llm",  // or "pattern"
  "generated_cypher": "MATCH (c:Class)-[:DEFINES_METHOD]->(m:Method) WHERE c.qualified_name CONTAINS 'service' WITH c, count(m) as method_count WHERE method_count > 5 RETURN c.name AS class_name, c.qualified_name AS qualified_name, method_count ORDER BY method_count DESC LIMIT 50",
  "explanation": "This query finds classes in the service package with more than 5 methods, ordered by method count.",
  "results": [
    {"class_name": "UserService", "qualified_name": "com.example.service.UserService", "method_count": 12},
    {"class_name": "OrderService", "qualified_name": "com.example.service.OrderService", "method_count": 8}
  ],
  "execution_time_ms": 450
}
```

### LLM Prompt

```java
public class CypherPromptBuilder {
    
    private static final String SYSTEM_PROMPT = """
        You are an expert Neo4j Cypher query generator for a code knowledge graph.
        
        ## Graph Schema
        
        Nodes:
        - Repository {name, path}
        - SourceFile {path, language}
        - Symbol {symbol, name, kind, documentation, start_line, end_line}
        
        Relationships:
        - (Repository)-[:CONTAINS]->(SourceFile)
        - (SourceFile)-[:DEFINES]->(Symbol)
        - (Symbol)-[:REFERENCES {kind}]->(Symbol)
        
        Symbol kinds: CLASS, METHOD, FUNCTION, FIELD, INTERFACE, ENUM, CONSTRUCTOR
        Reference kinds: CALL, IMPORT, INHERITANCE, IMPLEMENTATION, TYPE_REFERENCE
        
        ## Rules
        
        1. ALWAYS return specific properties with aliases (never `RETURN n`)
        2. Use `toLower()` for case-insensitive string matching
        3. Use `CONTAINS` for partial string matching
        4. Always add `LIMIT 50` for list queries
        5. For count queries, return ONLY the count
        6. Use `STARTS WITH` for path matching
        
        ## Examples
        
        Query: "Find all classes"
        Cypher:
        ```cypher
        MATCH (s:Symbol)
        WHERE s.kind = 'CLASS'
        RETURN s.name AS name, s.symbol AS qualified_name
        LIMIT 50
        ```
        
        Query: "What calls the login method?"
        Cypher:
        ```cypher
        MATCH (caller:Symbol)-[r:REFERENCES]->(target:Symbol)
        WHERE toLower(target.name) CONTAINS 'login' AND r.kind = 'CALL'
        RETURN caller.name AS caller_name, caller.symbol AS caller_qualified_name, target.name AS target_name
        LIMIT 50
        ```
        
        Query: "Count methods in UserService"
        Cypher:
        ```cypher
        MATCH (f:SourceFile)-[:DEFINES]->(s:Symbol)
        WHERE toLower(f.path) CONTAINS 'userservice' AND s.kind = 'METHOD'
        RETURN count(s) AS method_count
        ```
        
        ## Your Task
        
        Convert the following natural language query to a valid Cypher query.
        Return ONLY the Cypher query, no explanation.
        """;
    
    public String buildPrompt(String userQuery) {
        return SYSTEM_PROMPT + "\n\nUser Query: " + userQuery + "\n\nCypher Query:";
    }
}
```

### Implementation

#### LLMClient.java (Interface)

```java
public interface LLMClient {
    String complete(String prompt);
    String complete(String systemPrompt, String userPrompt);
}
```

#### OllamaLLMClient.java

```java
public class OllamaLLMClient implements LLMClient {
    private final OkHttpClient httpClient;
    private final String baseUrl;
    private final String model;
    
    public OllamaLLMClient(String baseUrl, String model) {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
        this.baseUrl = baseUrl;
        this.model = model;  // e.g., "llama3", "codellama", "mistral"
    }
    
    @Override
    public String complete(String prompt) {
        JsonObject request = new JsonObject();
        request.addProperty("model", model);
        request.addProperty("prompt", prompt);
        request.addProperty("stream", false);
        
        Request httpRequest = new Request.Builder()
            .url(baseUrl + "/api/generate")
            .post(RequestBody.create(request.toString(), MediaType.parse("application/json")))
            .build();
        
        try (Response response = httpClient.newCall(httpRequest).execute()) {
            JsonObject result = JsonParser.parseString(response.body().string()).getAsJsonObject();
            return result.get("response").getAsString().trim();
        }
    }
}
```

#### NLToCypherService.java

```java
public class NLToCypherService {
    private final LLMClient llmClient;
    private final CypherPromptBuilder promptBuilder;
    private final CypherValidator validator;
    
    public CypherResult translate(String naturalLanguageQuery) {
        // 1. Build prompt
        String prompt = promptBuilder.buildPrompt(naturalLanguageQuery);
        
        // 2. Call LLM
        String rawResponse = llmClient.complete(prompt);
        
        // 3. Extract Cypher (remove markdown if present)
        String cypher = extractCypher(rawResponse);
        
        // 4. Validate
        if (!validator.isValid(cypher)) {
            throw new InvalidCypherException("Generated invalid Cypher: " + cypher);
        }
        
        // 5. Sanitize (prevent injection)
        cypher = validator.sanitize(cypher);
        
        return new CypherResult(cypher, "llm");
    }
    
    private String extractCypher(String response) {
        // Remove ```cypher ... ``` if present
        if (response.contains("```")) {
            int start = response.indexOf("```") + 3;
            if (response.substring(start).startsWith("cypher")) {
                start += 6;
            }
            int end = response.indexOf("```", start);
            return response.substring(start, end).trim();
        }
        return response.trim();
    }
}
```

#### QueryRouter.java

```java
public class QueryRouter {
    private final QueryAnalyzer patternAnalyzer;  // MVP1
    private final NLToCypherService llmService;   // MVP2
    
    public QueryResult route(String query, boolean forceLLM) {
        // 1. Try pattern matching first (fast)
        if (!forceLLM) {
            Optional<SearchPlan> plan = patternAnalyzer.tryMatch(query);
            if (plan.isPresent()) {
                return new QueryResult(plan.get().toCypher(), "pattern");
            }
        }
        
        // 2. Fall back to LLM (slow but flexible)
        CypherResult result = llmService.translate(query);
        return new QueryResult(result.getCypher(), "llm");
    }
}
```

### Tasks

| Task | Status | Description | Files |
|------|--------|-------------|-------|
| 7.1 | ✅ | LLMClient interface | `llm/LLMClient.java` |
| 7.2 | ✅ | OllamaLLMClient implementation | `llm/OllamaLLMClient.java` |
| 7.3 | ✅ | CypherPromptBuilder với schema | `llm/CypherPromptBuilder.java` |
| 7.4 | ✅ | CypherValidator | `llm/CypherValidator.java` |
| 7.5 | ✅ | NLToCypherService | `llm/NLToCypherService.java` |
| 7.6 | ✅ | QueryRouter (LLM path) | `search/QueryRouter.java` |
| 7.7 | ✅ | POST /query/natural endpoint | `api/handlers/NaturalQueryHandler.java` |
| — | ✅ | GraphStore.runQuery for read-only Cypher | `graph/GraphStore.java`, `Neo4jGraphStore.java` |

### File Structure (Addition)

```
src/main/java/org/example/
├── llm/
│   ├── LLMClient.java            // NEW
│   ├── OllamaLLMClient.java      // NEW
│   ├── CypherPromptBuilder.java  // NEW
│   ├── CypherValidator.java      // NEW
│   └── NLToCypherService.java    // NEW
├── search/
│   └── QueryRouter.java          // NEW
└── api/handlers/
    └── NaturalQueryHandler.java  // NEW
```

### Configuration

```properties
# application.properties
ollama.base-url=http://localhost:11434
ollama.model=codellama
ollama.timeout=60
```

### Success Criteria

- [x] LLM generates valid Cypher for complex queries
- [x] QueryRouter uses LLM path (pattern fast path optional for later)
- [x] Cypher validation prevents injection (read-only only)
- [x] Response includes method used (llm) and execution_time_ms
- [x] Serve with `--enable-natural-query --llm-model llama3` (or codellama)

### Example Queries (MVP2 can handle)

```
# Complex queries that pattern matching can't handle:

"Find all classes that have more than 5 methods"
"Show me methods that are called by more than 3 different classes"  
"What are the most referenced symbols in the codebase?"
"Find circular dependencies between packages"
"List all public methods that don't have documentation"
```
