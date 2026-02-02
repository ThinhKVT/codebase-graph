package org.example.scip.mapper.python;

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
 * Mapping strategy for Python projects.
 * 
 * <p>Handles Python-specific patterns:</p>
 * <ul>
 *   <li>__init__ constructor injection with type hints</li>
 *   <li>FastAPI Depends() pattern</li>
 *   <li>Django class-based views</li>
 *   <li>Module-level functions</li>
 *   <li>Dataclasses and Pydantic models</li>
 * </ul>
 */
public class PythonStrategy extends AbstractMappingStrategy {

    /**
     * Service naming patterns for Python.
     */
    private static final Set<String> SERVICE_PATTERNS = Set.of(
        "Service", "Repository", "Handler", "Client",
        "Manager", "Provider", "Factory", "Controller",
        "View", "Router", "Gateway", "Adapter"
    );

    /**
     * Python magic method names that indicate constructors.
     */
    private static final Set<String> CONSTRUCTOR_METHODS = Set.of(
        "__init__", "__new__"
    );

    @Override
    public LanguageSupport getLanguage() {
        return LanguageSupport.PYTHON;
    }

    @Override
    public String parseParentSymbolId(String symbolId) {
        if (symbolId == null || symbolId.isEmpty()) {
            return null;
        }

        // Python SCIP symbol format examples:
        // Module: "scip-python python pkg 1.0.0 module/"
        // Class: "scip-python python pkg 1.0.0 module/MyClass#"
        // Method: "scip-python python pkg 1.0.0 module/MyClass#method()."
        // Function: "scip-python python pkg 1.0.0 module/function()."
        // Field: "scip-python python pkg 1.0.0 module/MyClass#field."

        int lastHash = symbolId.lastIndexOf('#');
        
        // Module-level function or class method
        int parenIdx = symbolId.lastIndexOf("().");
        if (parenIdx > 0) {
            if (lastHash > 0 && lastHash < parenIdx) {
                // Class method - parent is the class
                return symbolId.substring(0, lastHash + 1);
            } else {
                // Module-level function - parent is the module
                int slashIdx = symbolId.lastIndexOf('/', parenIdx);
                if (slashIdx > 0) {
                    return symbolId.substring(0, slashIdx + 1);
                }
            }
        }

        // Class attribute/field (has # and ends with .)
        if (lastHash > 0 && symbolId.endsWith(".") && !symbolId.contains("()")) {
            return symbolId.substring(0, lastHash + 1);
        }

        // Class in module (ends with #)
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

        // Pattern 1: __init__ constructor injection with type hints
        for (Symbol method : filterByKind(symbols, SymbolKind.METHOD)) {
            if (isConstructorMethod(method.name())) {
                String classId = method.parentId();
                if (classId == null) {
                    // Try to parse from method ID
                    classId = parseParentSymbolId(method.id());
                }
                
                if (classId != null) {
                    // Find parameters of __init__ (skip 'self')
                    List<Symbol> params = findParameters(symbols, method.id());
                    for (Symbol param : params) {
                        // Skip 'self' and 'cls' parameters
                        if (isSelfOrCls(param.name())) {
                            continue;
                        }
                        
                        String typeId = param.typeId();
                        if (typeId != null && isServiceType(typeId)) {
                            diRefs.add(createInjectsRef(classId, typeId, method.filePath()));
                        }
                    }
                }
            }
        }

        // Pattern 2: Class attributes with type hints
        for (Symbol field : filterByKind(symbols, SymbolKind.FIELD)) {
            String typeId = field.typeId();
            String parentId = field.parentId();
            if (typeId != null && parentId != null && isServiceType(typeId)) {
                diRefs.add(createInjectsRef(parentId, typeId, field.filePath()));
            }
        }

        // Pattern 3: Function parameters (for FastAPI Depends, etc.)
        // This is more heuristic - looking for function parameters with service types
        for (Symbol func : filterByKind(symbols, SymbolKind.FUNCTION)) {
            // FastAPI route functions often have injected dependencies
            if (isPossibleRouteFunction(func)) {
                List<Symbol> params = findParameters(symbols, func.id());
                for (Symbol param : params) {
                    String typeId = param.typeId();
                    if (typeId != null && isServiceType(typeId)) {
                        // Function depends on this service
                        diRefs.add(createInjectsRef(func.id(), typeId, func.filePath()));
                    }
                }
            }
        }

        return diRefs;
    }

    /**
     * Check if method name is a Python constructor.
     */
    private boolean isConstructorMethod(String methodName) {
        return methodName != null && CONSTRUCTOR_METHODS.contains(methodName);
    }

    /**
     * Check if parameter name is 'self' or 'cls'.
     */
    private boolean isSelfOrCls(String paramName) {
        return "self".equals(paramName) || "cls".equals(paramName);
    }

    /**
     * Heuristic check if function might be a FastAPI/Flask route.
     */
    private boolean isPossibleRouteFunction(Symbol func) {
        // Check if function name suggests it's a route handler
        String name = func.name();
        if (name == null) return false;
        
        return name.startsWith("get_") ||
               name.startsWith("post_") ||
               name.startsWith("put_") ||
               name.startsWith("delete_") ||
               name.startsWith("patch_") ||
               name.startsWith("list_") ||
               name.startsWith("create_") ||
               name.startsWith("update_") ||
               name.startsWith("handle_");
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

        // In Python, we might also look for protocol/abstract base class patterns
        // Type hints often reference these directly
        
        return false;
    }

    @Override
    public SymbolMetadata getSymbolMetadata(scip.Scip.SymbolInformation symbolInfo) {
        // Python has different concepts - check for decorators, etc.
        // Static methods are indicated by @staticmethod decorator
        // Class methods by @classmethod
        // Abstract by @abstractmethod
        
        // For now, return defaults - this could be enhanced by parsing
        // documentation or decorator information
        return SymbolMetadata.DEFAULT;
    }
}
