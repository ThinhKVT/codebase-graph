package org.example.scip.mapper.go;

import org.example.model.LanguageSupport;
import org.example.model.Reference;
import org.example.model.Symbol;
import org.example.model.SymbolKind;
import org.example.scip.mapper.AbstractMappingStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mapping strategy for Go projects.
 * 
 * <p>Handles Go-specific patterns:</p>
 * <ul>
 *   <li>Struct-based types with methods (implicit interface implementation)</li>
 *   <li>Package-level functions</li>
 *   <li>Constructor patterns (NewXxx functions)</li>
 *   <li>DI patterns: Wire, Fx, manual injection</li>
 * </ul>
 */
public class GoStrategy extends AbstractMappingStrategy {

    /**
     * Service naming patterns for Go.
     * Go convention: interfaces often end with "er" (Reader, Writer, Handler).
     */
    private static final Set<String> SERVICE_PATTERNS = Set.of(
        "Service", "Repository", "Client", "Handler",
        "Store", "Manager", "Provider", "Factory"
    );

    /**
     * Interface suffix patterns in Go.
     */
    private static final Set<String> INTERFACE_SUFFIXES = Set.of(
        "er", "or", "able"
    );

    @Override
    public LanguageSupport getLanguage() {
        return LanguageSupport.GO;
    }

    @Override
    public String parseParentSymbolId(String symbolId) {
        if (symbolId == null || symbolId.isEmpty()) {
            return null;
        }

        // Go SCIP symbol format examples:
        // Package: "scip-go gomod github.com/user/pkg v1.0.0 pkg/"
        // Struct: "scip-go gomod github.com/user/pkg v1.0.0 pkg/MyStruct#"
        // Method: "scip-go gomod github.com/user/pkg v1.0.0 pkg/MyStruct#Method()."
        // Function: "scip-go gomod github.com/user/pkg v1.0.0 pkg/Function()."
        // Field: "scip-go gomod github.com/user/pkg v1.0.0 pkg/MyStruct#field."

        // Find method marker
        int parenIdx = symbolId.lastIndexOf("().");
        if (parenIdx > 0) {
            // This is a method or function
            int hashIdx = symbolId.lastIndexOf('#', parenIdx);
            if (hashIdx > 0) {
                // Method of a struct - parent is the struct
                return symbolId.substring(0, hashIdx + 1);
            } else {
                // Package-level function - parent is the package
                int slashIdx = symbolId.lastIndexOf('/', parenIdx);
                if (slashIdx > 0) {
                    return symbolId.substring(0, slashIdx + 1);
                }
            }
        }

        // Check for field (has # and ends with .)
        int hashIdx = symbolId.lastIndexOf('#');
        if (hashIdx > 0 && symbolId.endsWith(".") && !symbolId.contains("()")) {
            // Field of a struct
            return symbolId.substring(0, hashIdx + 1);
        }

        // Check for struct in package (ends with #)
        if (symbolId.endsWith("#")) {
            int slashIdx = symbolId.lastIndexOf('/', symbolId.length() - 2);
            if (slashIdx > 0) {
                return symbolId.substring(0, slashIdx + 1);
            }
        }

        return null;
    }

    @Override
    public List<Reference> extractDependencyInjection(
            List<Symbol> symbols,
            List<Reference> existingRefs,
            Map<String, SymbolKind> symbolKindMap) {
        
        List<Reference> diRefs = new ArrayList<>();

        // Go DI pattern: Constructor functions (NewXxx, ProvideXxx)
        // that return a struct and take interfaces/services as parameters
        for (Symbol func : filterByKind(symbols, SymbolKind.FUNCTION)) {
            String funcName = func.name();
            
            // Check if this is a constructor-like function
            if (isConstructorFunction(funcName)) {
                String returnType = func.typeId();
                if (returnType != null) {
                    // Find parameters of this function
                    List<Symbol> params = findParameters(symbols, func.id());
                    for (Symbol param : params) {
                        String paramType = param.typeId();
                        if (paramType != null && isServiceType(paramType)) {
                            // The returned type INJECTS the parameter type
                            diRefs.add(createInjectsRef(returnType, paramType, func.filePath()));
                        }
                    }
                }
            }
        }

        // Also check struct fields for direct dependencies
        for (Symbol field : filterByKinds(symbols, SymbolKind.FIELD, SymbolKind.PROPERTY)) {
            String typeId = field.typeId();
            String parentId = field.parentId();
            if (typeId != null && parentId != null && isServiceType(typeId)) {
                diRefs.add(createInjectsRef(parentId, typeId, field.filePath()));
            }
        }

        return diRefs;
    }

    /**
     * Check if a function name follows Go constructor patterns.
     */
    private boolean isConstructorFunction(String funcName) {
        if (funcName == null) return false;
        return funcName.startsWith("New") ||
               funcName.startsWith("Provide") ||
               funcName.startsWith("Create") ||
               funcName.startsWith("Make") ||
               funcName.startsWith("Build");
    }

    @Override
    public boolean isServiceType(String typeId) {
        if (typeId == null || typeId.isEmpty()) {
            return false;
        }

        // Check common service patterns
        for (String pattern : SERVICE_PATTERNS) {
            if (typeId.contains(pattern)) {
                return true;
            }
        }

        // Check Go interface naming convention (ends with "er")
        // Extract the type name from the full ID
        String typeName = extractTypeName(typeId);
        if (typeName != null) {
            for (String suffix : INTERFACE_SUFFIXES) {
                if (typeName.endsWith(suffix) && typeName.length() > suffix.length() + 2) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Extract the simple type name from a full symbol ID.
     */
    private String extractTypeName(String symbolId) {
        if (symbolId == null) return null;
        
        // Remove trailing # or .
        String cleaned = symbolId;
        if (cleaned.endsWith("#") || cleaned.endsWith(".")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        
        // Get the last component
        int lastSlash = cleaned.lastIndexOf('/');
        int lastHash = cleaned.lastIndexOf('#');
        int lastSep = Math.max(lastSlash, lastHash);
        
        if (lastSep >= 0 && lastSep < cleaned.length() - 1) {
            return cleaned.substring(lastSep + 1);
        }
        
        return cleaned;
    }

    @Override
    public SymbolMetadata getSymbolMetadata(scip.Scip.SymbolInformation symbolInfo) {
        // Go doesn't have the same modifier concepts as Java
        // but we can detect some patterns
        return SymbolMetadata.DEFAULT;
    }
}
