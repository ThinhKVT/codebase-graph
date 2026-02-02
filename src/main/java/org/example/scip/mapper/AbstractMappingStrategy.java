package org.example.scip.mapper;

import org.example.model.Reference;
import org.example.model.ReferenceKind;
import org.example.model.Symbol;
import org.example.model.SymbolKind;
import scip.Scip;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Abstract base class for language mapping strategies.
 * Provides common utility methods for symbol processing.
 */
public abstract class AbstractMappingStrategy implements LanguageMappingStrategy {

    /**
     * Filter symbols by kind.
     */
    protected List<Symbol> filterByKind(List<Symbol> symbols, SymbolKind kind) {
        return symbols.stream()
            .filter(s -> s.kind() == kind)
            .toList();
    }

    /**
     * Filter symbols by multiple kinds.
     */
    protected List<Symbol> filterByKinds(List<Symbol> symbols, SymbolKind... kinds) {
        var kindSet = java.util.Set.of(kinds);
        return symbols.stream()
            .filter(s -> kindSet.contains(s.kind()))
            .toList();
    }

    /**
     * Group symbols by their parent ID.
     */
    protected Map<String, List<Symbol>> groupByParent(List<Symbol> symbols) {
        return symbols.stream()
            .filter(s -> s.parentId() != null)
            .collect(Collectors.groupingBy(Symbol::parentId));
    }

    /**
     * Find symbols that belong to a specific parent.
     */
    protected List<Symbol> findChildren(List<Symbol> symbols, String parentId) {
        return symbols.stream()
            .filter(s -> parentId.equals(s.parentId()))
            .toList();
    }

    /**
     * Find parameters of a method/function.
     */
    protected List<Symbol> findParameters(List<Symbol> symbols, String methodId) {
        return symbols.stream()
            .filter(s -> s.kind() == SymbolKind.PARAMETER)
            .filter(s -> methodId.equals(s.parentId()))
            .toList();
    }

    /**
     * Create a CONTAINS reference.
     */
    protected Reference createContainsRef(String parentId, String childId, String filePath) {
        return Reference.builder()
            .fromSymbolId(parentId)
            .toSymbolId(childId)
            .kind(ReferenceKind.CONTAINS)
            .filePath(filePath)
            .build();
    }

    /**
     * Create an INJECTS reference.
     */
    protected Reference createInjectsRef(String fromId, String toTypeId, String filePath) {
        return Reference.builder()
            .fromSymbolId(fromId)
            .toSymbolId(toTypeId)
            .kind(ReferenceKind.INJECTS)
            .filePath(filePath)
            .build();
    }

    /**
     * Create a HAS_TYPE reference.
     */
    protected Reference createHasTypeRef(String symbolId, String typeId, String filePath) {
        return Reference.builder()
            .fromSymbolId(symbolId)
            .toSymbolId(typeId)
            .kind(ReferenceKind.HAS_TYPE)
            .filePath(filePath)
            .build();
    }

    /**
     * Create a RETURNS_TYPE reference.
     */
    protected Reference createReturnsTypeRef(String methodId, String typeId, String filePath) {
        return Reference.builder()
            .fromSymbolId(methodId)
            .toSymbolId(typeId)
            .kind(ReferenceKind.RETURNS_TYPE)
            .filePath(filePath)
            .build();
    }

    @Override
    public List<Reference> extractContainsRelationships(
            List<Scip.SymbolInformation> symbols,
            Map<String, SymbolKind> symbolKindMap) {
        
        List<Reference> refs = new ArrayList<>();
        
        for (Scip.SymbolInformation symbolInfo : symbols) {
            String symbolId = symbolInfo.getSymbol();
            
            // Method 1: Use enclosing_symbol if available
            String enclosing = symbolInfo.getEnclosingSymbol();
            if (enclosing != null && !enclosing.isEmpty()) {
                refs.add(createContainsRef(enclosing, symbolId, null));
            }
            
            // Method 2: Parse from symbol ID
            String parentId = parseParentSymbolId(symbolId);
            if (parentId != null && !parentId.equals(enclosing)) {
                refs.add(createContainsRef(parentId, symbolId, null));
            }
        }
        
        return refs;
    }

    @Override
    public List<Reference> extractTypeRelationships(
            Scip.SymbolInformation symbolInfo,
            Map<String, SymbolKind> symbolKindMap) {
        
        List<Reference> refs = new ArrayList<>();
        String symbolId = symbolInfo.getSymbol();
        SymbolKind kind = symbolKindMap.get(symbolId);
        
        // Extract type relationships from SCIP relationships
        for (Scip.Relationship rel : symbolInfo.getRelationshipsList()) {
            String targetSymbol = rel.getSymbol();
            
            if (rel.getIsTypeDefinition()) {
                // Determine if it's HAS_TYPE or RETURNS_TYPE based on symbol kind
                if (kind != null && kind.isCallable()) {
                    refs.add(createReturnsTypeRef(symbolId, targetSymbol, null));
                } else if (kind != null && (kind.isField() || kind == SymbolKind.PARAMETER)) {
                    refs.add(createHasTypeRef(symbolId, targetSymbol, null));
                }
            }
        }
        
        return refs;
    }

    /**
     * Common service type patterns.
     */
    protected static final java.util.Set<String> COMMON_SERVICE_PATTERNS = java.util.Set.of(
        "Service", "Repository", "Dao", "Controller",
        "Handler", "Manager", "Client", "Provider",
        "Factory", "Builder", "Helper", "Util"
    );

    @Override
    public boolean isServiceType(String typeId) {
        if (typeId == null || typeId.isEmpty()) {
            return false;
        }
        return COMMON_SERVICE_PATTERNS.stream()
            .anyMatch(typeId::contains);
    }
}
