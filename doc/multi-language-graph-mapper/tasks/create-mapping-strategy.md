# Task: Create LanguageMappingStrategy Interface

**ID:** create-mapping-strategy  
**Status:** pending  
**Priority:** high  

## Description

Tao LanguageMappingStrategy interface de define contract cho language-specific graph mapping logic.

## Acceptance Criteria

- [ ] Tao `LanguageMappingStrategy` interface trong package `org.example.scip.mapper`
- [ ] Define methods cho:
  - `getLanguage()` - tra ve LanguageSupport enum
  - `extractContainsRelationships()` - CONTAINS relationships
  - `extractTypeRelationships()` - HAS_TYPE, RETURNS_TYPE
  - `extractDependencyInjection()` - DI detection
  - `getSymbolMetadata()` - additional symbol info
  - `parseParentSymbolId()` - parse parent from symbol ID
  - `isServiceType()` - check if type is a service/component

## Interface Definition

```java
public interface LanguageMappingStrategy {
    
    LanguageSupport getLanguage();
    
    List<Reference> extractContainsRelationships(
        List<Scip.SymbolInformation> symbols,
        Map<String, SymbolKind> symbolKindMap
    );
    
    List<Reference> extractTypeRelationships(
        Scip.SymbolInformation symbolInfo,
        Map<String, SymbolKind> symbolKindMap
    );
    
    List<Reference> extractDependencyInjection(
        List<Symbol> symbols,
        List<Reference> existingRefs,
        Map<String, SymbolKind> symbolKindMap
    );
    
    SymbolMetadata getSymbolMetadata(Scip.SymbolInformation symbolInfo);
    
    String parseParentSymbolId(String symbolId);
    
    boolean isServiceType(String typeId);
}
```

## Related Files

- `src/main/java/org/example/scip/ScipToGraphMapper.java` (will use this interface)
- `src/main/java/org/example/model/Symbol.java`
- `src/main/java/org/example/model/Reference.java`

## Dependencies

- Depends on: `create-language-enum`, `update-reference-kind`
- Blocks: `create-java-strategy`, `create-go-strategy`, `create-python-strategy`
