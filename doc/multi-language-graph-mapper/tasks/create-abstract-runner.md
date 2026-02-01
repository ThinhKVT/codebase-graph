# Task: Create AbstractScipRunner

**ID:** create-abstract-runner  
**Status:** pending  
**Priority:** high  

## Description

Tao AbstractScipRunner class su dung Template Method pattern de giam code duplication giua cac ScipRunner implementations.

## Acceptance Criteria

- [ ] Tao `AbstractScipRunner` trong package `org.example.scip.runner`
- [ ] Di chuyen common logic tu cac concrete runners vao abstract class
- [ ] Define abstract methods: `getToolCommand()`, `getIndexArgs()`, `isToolInstalled()`, `getToolVersion()`
- [ ] Define hook methods: `preProcess()`, `postProcess()`
- [ ] Template method `runIndex()` goi cac abstract/hook methods theo dung thu tu
- [ ] Unit tests cho template method execution

## Implementation Notes

```java
// Key structure
public abstract class AbstractScipRunner implements ScipRunner {
    // Template method pattern
    @Override
    public final Path runIndex(Path outputPath) throws ScipException {
        preProcess();           // Hook
        // ... common logic ...
        postProcess();          // Hook
    }
    
    // Abstract - must implement
    protected abstract String getToolCommand();
    
    // Hook - can override
    protected void preProcess() { }
}
```

## Related Files

- `src/main/java/org/example/scip/ScipRunner.java` (interface)
- `src/main/java/org/example/scip/ScipJavaRunner.java` (reference)
- `src/main/java/org/example/scip/ScipPythonRunner.java` (reference)

## Dependencies

- Depends on: `create-language-enum` (nice to have, not blocking)
- Blocks: `refactor-java-runner`, `create-go-runner`, `refactor-python-runner`
