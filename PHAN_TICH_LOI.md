# Phân Tích Lỗi: Elasticsearch-Springboot-Example Không Index Được

## Nguyên Nhân Chính

### 1. **Lỗi Maven Wrapper (mvnw) - Quyền Thực Thi**

**Lỗi cụ thể:**
```
Caused by: java.io.IOException: error=2, No such file or directory
at java.base/java.lang.ProcessImpl.forkAndExec(Native Method)
```

**Giải thích:**
- Project `Elasticsearch-Springboot-Example` sử dụng **Maven Wrapper** (`mvnw`, `mvnw.cmd`)
- Khi `scip-java` chạy trong Docker container (Linux), nó cố gọi file `mvnw`
- File `mvnw` trên Windows **không có execute permission** khi mount vào Linux container
- Linux không thể execute file này → lỗi "No such file or directory" (thực chất là permission denied)

### 2. **Tại Sao simple-java-demo Lại Chạy Được?**

Project `simple-java-demo` **không dùng Maven Wrapper**, mà dùng Maven system-wide:
- `scip-java` tự động phát hiện `pom.xml`
- Gọi lệnh `mvn` (Maven đã cài sẵn trong container)
- Không cần file `mvnw` → không gặp lỗi permission

## Cách Khắc Phục

### **Giải Pháp 1: Xóa Maven Wrapper (Khuyến Nghị)**

Xóa các file Maven Wrapper và để `scip-java` dùng Maven system:

```powershell
cd demo\Elasticsearch-Springboot-Example
Remove-Item mvnw, mvnw.cmd, .mvn -Recurse -Force
```

Sau đó chạy lại:
```powershell
.\index_project.bat "demo\Elasticsearch-Springboot-Example"
```

### **Giải Pháp 2: Fix Permission Trong Container**

Chạy lệnh này để set execute permission:

```powershell
docker run --rm -v "d:\Learn\G\dichv2\codebase-graph\demo\Elasticsearch-Springboot-Example:/repo" `
  -w /repo maven:3.9-eclipse-temurin-17 `
  sh -c "chmod +x mvnw && ./mvnw clean compile -DskipTests"
```

**Lưu ý:** Cách này chỉ fix tạm thời, mỗi lần restart container phải chạy lại.

### **Giải Pháp 3: Clone Lại Trên Linux/WSL**

Nếu bạn dùng WSL (Windows Subsystem for Linux):
```bash
cd /mnt/d/Learn/G/dichv2/codebase-graph/demo
git clone https://github.com/phuthien007/Elasticsearch-Springboot-Example.git
chmod +x Elasticsearch-Springboot-Example/mvnw
```

File permission sẽ được giữ nguyên khi mount vào Docker.

## Các Yêu Cầu Để Index Thành Công

### ✅ **Điều Kiện Bắt Buộc:**

1. **Build Tool Configuration:**
   - Phải có `pom.xml` (Maven) hoặc `build.gradle` (Gradle)
   - File phải valid và có thể build được

2. **Java Version:**
   - Project phải compile được với Java 17 (version trong scip-java container)
   - Nếu project yêu cầu Java 11 hoặc 21, có thể gặp lỗi

3. **Dependencies:**
   - Tất cả dependencies phải download được từ Maven Central
   - Không có private repository hoặc credentials

4. **File Permissions (Linux Container):**
   - Nếu dùng Maven Wrapper (`mvnw`), file phải có execute permission
   - Hoặc không dùng wrapper, để tool dùng system Maven

### ⚠️ **Các Vấn Đề Thường Gặp:**

1. **Maven Wrapper không execute được** (như trường hợp này)
2. **Java version mismatch:**
   ```xml
   <java.version>21</java.version>  <!-- scip-java chỉ có Java 17 -->
   ```
3. **Dependencies quá nặng hoặc timeout:**
   - Spring Boot với nhiều dependencies có thể mất 5-10 phút
   - Cần tăng timeout hoặc pre-compile

4. **Lombok annotation processing:**
   - Lombok cần plugin đặc biệt
   - `scip-java` có thể không process được annotation

## Test Nhanh Một Project

### Bước 1: Kiểm tra build tool
```powershell
# Kiểm tra có pom.xml hoặc build.gradle
Test-Path "path\to\project\pom.xml"
```

### Bước 2: Thử compile trước
```powershell
docker run --rm -v "path\to\project:/repo" -w /repo `
  maven:3.9-eclipse-temurin-17 `
  mvn clean compile -DskipTests
```

Nếu bước 2 thành công → 90% khả năng index được.

### Bước 3: Index
```powershell
.\index_project.bat "path\to\project"
```

## Khuyến Nghị

### **Cho Project Cá Nhân:**
- Xóa Maven Wrapper nếu không cần thiết
- Dùng Maven/Gradle system-wide

### **Cho Project Production:**
- Giữ Maven Wrapper
- Clone trên Linux/WSL để giữ file permission
- Hoặc dùng CI/CD pipeline với Docker native

### **Cho Demo/Testing:**
- Tạo project đơn giản như `simple-java-demo`
- Tránh dependencies phức tạp (Elasticsearch, Kafka, etc.)
- Chỉ dùng Spring Boot starter cơ bản

## Tóm Tắt

| Tiêu Chí | simple-java-demo ✅ | Elasticsearch-Springboot-Example ❌ |
|----------|---------------------|-------------------------------------|
| Maven Wrapper | Không | Có (mvnw) |
| Execute Permission | N/A | Thiếu |
| Dependencies | Minimal (JUnit) | Heavy (Spring Boot, ES, Tess4j, POI) |
| Build Time | < 30s | > 2 phút |
| Kết Quả | Thành công | Lỗi permission |

**Kết luận:** Lỗi không phải do code Java, mà do **file permission của Maven Wrapper** khi chạy trong Linux container từ Windows host.
