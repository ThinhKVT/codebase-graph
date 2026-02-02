# Hướng Dẫn Sử Dụng Codebase Graph

## Tổng Quan
Bạn đã index thành công các project sau vào Neo4j:
- **codebase-graph** (chính project này): 751 symbols, 2799 references
- **simple-java-demo**: 9 symbols, 6 references

**Tổng cộng hiện tại**: 760 symbols, 1987 references

## Cách Sử Dụng

### 1. Kiểm Tra Thống Kê
```powershell
curl -UseBasicParsing http://localhost:8080/stats
```
Kết quả: `{"symbols":760,"references":1987}`

### 2. Tìm Kiếm Symbol (Class, Method, Field)

#### Tìm theo tên:
```powershell
curl -UseBasicParsing "http://localhost:8080/symbols?name=Calculator"
```

#### Tìm theo loại (kind):
```powershell
# Tìm tất cả class
curl -UseBasicParsing "http://localhost:8080/symbols?kind=CLASS"

# Tìm tất cả method
curl -UseBasicParsing "http://localhost:8080/symbols?kind=METHOD"
```

### 3. Xem Chi Tiết Symbol

Sau khi tìm được symbol, copy `id` của nó và query:
```powershell
curl -UseBasicParsing "http://localhost:8080/symbols/{id}"
```

### 4. Tìm References (Nơi sử dụng)
```powershell
curl -UseBasicParsing "http://localhost:8080/symbols/{id}/references"
```
Ví dụ: Tìm xem method `add()` được gọi ở đâu.

### 5. Phân Tích Dependencies
```powershell
# Xem dependencies của một symbol
curl -UseBasicParsing "http://localhost:8080/symbols/{id}/dependencies"

# Xem ai phụ thuộc vào symbol này
curl -UseBasicParsing "http://localhost:8080/symbols/{id}/dependents"
```

## Sử Dụng Neo4j Browser (Nâng Cao)

### Truy cập:
Mở trình duyệt: http://localhost:7474
- Username: `neo4j`
- Password: `codebase123`

### Các Query Hữu Ích:

#### 1. Xem tất cả repositories đã index:
```cypher
MATCH (r:Repository) RETURN r
```

#### 2. Xem tất cả class trong project simple-java-demo:
```cypher
MATCH (c:Class)-[:BELONGS_TO]->(r:Repository {name: "simple-java-demo"})
RETURN c.name, c.fullyQualifiedName
```

#### 3. Xem class Calculator và tất cả method của nó:
```cypher
MATCH (c:Class {name: "Calculator"})-[:CONTAINS]->(m:Method)
RETURN c, m
```

#### 4. Tìm tất cả nơi gọi method `add`:
```cypher
MATCH (m:Method {name: "add"})<-[:REFERENCES]-(ref)
RETURN m, ref
```

#### 5. Visualize toàn bộ graph của simple-java-demo:
```cypher
MATCH (n)-[r]->(m)
WHERE n.repositoryId CONTAINS "simple-java-demo"
RETURN n, r, m
LIMIT 100
```

#### 6. Tìm class có nhiều method nhất:
```cypher
MATCH (c:Class)-[:CONTAINS]->(m:Method)
RETURN c.name, count(m) as methodCount
ORDER BY methodCount DESC
LIMIT 10
```

## Index Project Mới

### Sử dụng script:
```powershell
.\index_project.bat "path\to\your\java\project"
```

**Lưu ý**: Project phải có `pom.xml` (Maven) hoặc `build.gradle` (Gradle).

### Ví dụ thực tế:
```powershell
# Index project từ GitHub (clone trước)
git clone https://github.com/username/project.git demo/project
.\index_project.bat "demo\project"
```

## Use Cases Thực Tế

### 1. Code Review
- Tìm tất cả nơi sử dụng một method trước khi refactor
- Phân tích impact khi thay đổi API

### 2. Onboarding
- Hiểu cấu trúc project thông qua graph visualization
- Tìm entry points (main methods, controllers)

### 3. Technical Debt
- Tìm class có quá nhiều dependencies
- Phát hiện circular dependencies

### 4. Documentation
- Tự động tạo class diagram từ Neo4j graph
- Export danh sách API endpoints

## Troubleshooting

### Server không chạy:
```powershell
docker-compose -f docker-compose.full.yml up -d
```

### Xóa dữ liệu cũ:
```cypher
# Trong Neo4j Browser
MATCH (n) DETACH DELETE n
```

### Xem logs:
```powershell
docker logs codebase-graph-app
docker logs codebase-graph-neo4j
```
