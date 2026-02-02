# Task: Create JavaSpringBootStrategy

**ID:** create-java-strategy  
**Status:** pending  
**Priority:** high  

## Description

Tao JavaSpringBootStrategy implementation de handle Java/Spring Boot specific graph mapping, dac biet la Dependency Injection detection.

## Acceptance Criteria

- [ ] Tao `JavaSpringBootStrategy` trong package `org.example.scip.mapper.java`
- [ ] Implement `extractDependencyInjection()`:
  - Phat hien field injection (@Autowired)
  - Phat hien constructor injection
  - Phat hien setter injection
- [ ] Implement `parseParentSymbolId()` theo SCIP Java symbol format
- [ ] Implement `isServiceType()` voi patterns: Service, Repository, Controller, etc.
- [ ] Unit tests cho DI detection

## DI Detection Logic

```java
// Service patterns to detect
private static final Set<String> SERVICE_PATTERNS = Set.of(
    "Service", "Repository", "Dao", "Controller",
    "Handler", "Manager", "Client", "Provider"
);

// Detection methods:
// 1. Field with service type -> INJECTS
// 2. Constructor param with service type -> INJECTS
// 3. Setter method param with service type -> INJECTS
```

## Symbol ID Parsing

```
// Java symbol format examples:
"scip-java maven . . . com/example/MyClass#"           -> Class
"scip-java maven . . . com/example/MyClass#myField."   -> Field
"scip-java maven . . . com/example/MyClass#method()."  -> Method
"scip-java maven . . . com/example/Outer#Inner#"       -> Inner class
```

## Related Files

- `src/main/java/org/example/scip/mapper/LanguageMappingStrategy.java` (implements)
- `src/main/java/org/example/model/ReferenceKind.java` (uses INJECTS)

## Dependencies

- Depends on: `create-mapping-strategy`, `update-reference-kind`
- Blocks: `update-sciptographmapper`
