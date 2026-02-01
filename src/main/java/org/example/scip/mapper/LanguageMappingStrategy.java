package org.example.scip.mapper;

import org.example.model.LanguageSupport;
import org.example.model.Reference;
import org.example.model.ReferenceKind;
import org.example.model.Symbol;
import org.example.model.SymbolKind;
import scip.Scip;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Strategy interface for language-specific graph mapping logic.
 * 
 * <p>Each language has different patterns for:</p>
 * <ul>
 *   <li>Structural relationships (CONTAINS)</li>
 *   <li>Type relationships (HAS_TYPE, RETURNS_TYPE)</li>
 *   <li>Dependency injection patterns</li>
 *   <li>Symbol ID format parsing</li>
 * </ul>
 * 
 * <p>Implementations:</p>
 * <ul>
 *   <li>{@link java.JavaStrategy} - Java</li>
 *   <li>{@link go.GoStrategy} - Go</li>
 *   <li>{@link python.PythonStrategy} - Python</li>
 * </ul>
 */
public interface LanguageMappingStrategy {

    /**
     * Get the language this strategy handles.
     */
    LanguageSupport getLanguage();

    /**
     * Extract CONTAINS relationships from symbol hierarchy.
     * 
     * <p>Examples:</p>
     * <ul>
     *   <li>Package CONTAINS Class</li>
     *   <li>Class CONTAINS Method</li>
     *   <li>Method CONTAINS Parameter</li>
     * </ul>
     *
     * @param symbols List of SCIP symbol information
     * @param symbolKindMap Map of symbol ID to SymbolKind
     * @return List of CONTAINS references
     */
    List<Reference> extractContainsRelationships(
        List<Scip.SymbolInformation> symbols,
        Map<String, SymbolKind> symbolKindMap
    );

    /**
     * Extract type relationships (HAS_TYPE, RETURNS_TYPE).
     *
     * @param symbolInfo SCIP symbol information
     * @param symbolKindMap Map of symbol ID to SymbolKind
     * @return List of type-related references
     */
    List<Reference> extractTypeRelationships(
        Scip.SymbolInformation symbolInfo,
        Map<String, SymbolKind> symbolKindMap
    );

    /**
     * Detect and extract dependency injection relationships.
     * 
     * <p>Language-specific DI patterns:</p>
     * <ul>
     *   <li>Java: @Autowired, @Inject, constructor injection</li>
     *   <li>Go: Wire, Fx, NewXxx() constructors</li>
     *   <li>Python: __init__ with type hints, FastAPI Depends()</li>
     * </ul>
     *
     * @param symbols List of parsed Symbol objects
     * @param existingRefs Existing references (for context)
     * @param symbolKindMap Map of symbol ID to SymbolKind
     * @return List of INJECTS references
     */
    List<Reference> extractDependencyInjection(
        List<Symbol> symbols,
        List<Reference> existingRefs,
        Map<String, SymbolKind> symbolKindMap
    );

    /**
     * Parse the parent symbol ID from a symbol ID string.
     * 
     * <p>Symbol ID formats vary by language:</p>
     * <ul>
     *   <li>Java: "scip-java maven . . . com/example/Class#method()."</li>
     *   <li>Go: "scip-go gomod github.com/pkg v1 Type.Method()."</li>
     *   <li>Python: "scip-python python pkg 1.0 module/Class#method()."</li>
     * </ul>
     *
     * @param symbolId The symbol ID to parse
     * @return Parent symbol ID, or null if no parent
     */
    String parseParentSymbolId(String symbolId);

    /**
     * Check if a type ID represents a "service" type for DI detection.
     * 
     * <p>Common patterns:</p>
     * <ul>
     *   <li>*Service, *Repository, *Controller, *Handler</li>
     *   <li>*Client, *Manager, *Provider</li>
     * </ul>
     *
     * @param typeId The type ID to check
     * @return true if this is likely a service/component type
     */
    boolean isServiceType(String typeId);

    /**
     * Get additional symbol metadata based on SCIP information.
     * 
     * <p>Extracts modifiers, visibility, etc.</p>
     *
     * @param symbolInfo SCIP symbol information
     * @return SymbolMetadata with additional info
     */
    default SymbolMetadata getSymbolMetadata(Scip.SymbolInformation symbolInfo) {
        return SymbolMetadata.DEFAULT;
    }

    /**
     * Get the set of reference kinds this strategy can produce.
     */
    default Set<ReferenceKind> getSupportedReferenceKinds() {
        return Set.of(
            ReferenceKind.CONTAINS,
            ReferenceKind.HAS_TYPE,
            ReferenceKind.RETURNS_TYPE,
            ReferenceKind.INJECTS
        );
    }

    /**
     * Metadata extracted from a symbol.
     */
    record SymbolMetadata(
        boolean isStatic,
        boolean isAbstract,
        boolean isFinal,
        String visibility,
        boolean isGenerated,
        boolean isTest
    ) {
        public static final SymbolMetadata DEFAULT = new SymbolMetadata(
            false, false, false, null, false, false
        );
    }
}
