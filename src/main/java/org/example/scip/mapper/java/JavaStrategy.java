package org.example.scip.mapper.java;

import org.example.model.LanguageSupport;
import org.example.model.Reference;
import org.example.model.Symbol;
import org.example.model.SymbolKind;
import org.example.scip.mapper.AbstractMappingStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mapping strategy for Java projects.
 * 
 * <p>Handles Java-specific patterns:</p>
 * <ul>
 *   <li>Constructor injection</li>
 *   <li>Field injection</li>
 *   <li>Setter injection</li>
 *   <li>Common service patterns detection</li>
 * </ul>
 */
public class JavaStrategy extends AbstractMappingStrategy {

    /**
     * Service naming patterns for DI detection.
     */
    private static final Set<String> SERVICE_PATTERNS = Set.of(
        "Service", "Repository", "Dao", "Controller",
        "Handler", "Manager", "Client", "Provider",
        "Factory", "Gateway", "Adapter", "Facade"
    );

    @Override
    public LanguageSupport getLanguage() {
        return LanguageSupport.JAVA;
    }

    @Override
    public String parseParentSymbolId(String symbolId) {
        if (symbolId == null || symbolId.isEmpty()) {
            return null;
        }

        // Java SCIP symbol format examples:
        // Class: "scip-java maven . . . com/example/MyClass#"
        // Method: "scip-java maven . . . com/example/MyClass#myMethod()."
        // Field: "scip-java maven . . . com/example/MyClass#myField."
        // Inner class: "scip-java maven . . . com/example/Outer#Inner#"
        // Parameter: "scip-java maven . . . com/example/MyClass#method().(param)"

        int lastHash = symbolId.lastIndexOf('#');
        if (lastHash < 0) {
            return null;
        }

        // Check for method (ends with "()." or "(params).")
        if (symbolId.contains("(") && symbolId.endsWith(".")) {
            return symbolId.substring(0, lastHash + 1);
        }

        // Check for field (ends with "." after #, but no parentheses)
        int dotAfterHash = symbolId.indexOf('.', lastHash);
        if (dotAfterHash > 0 && dotAfterHash == symbolId.length() - 1 && !symbolId.contains("(")) {
            return symbolId.substring(0, lastHash + 1);
        }

        // Check for inner class (has two #)
        int prevHash = symbolId.lastIndexOf('#', lastHash - 1);
        if (prevHash >= 0) {
            return symbolId.substring(0, lastHash + 1);
        }

        // Check for parameter (ends with "(name)" pattern)
        if (symbolId.endsWith(")") && !symbolId.endsWith("().")) {
            int parenStart = symbolId.lastIndexOf(".(");
            if (parenStart > 0) {
                return symbolId.substring(0, parenStart + 1);
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

        // Group symbols by class
        Map<String, List<Symbol>> symbolsByClass = groupSymbolsByClass(symbols);

        for (Map.Entry<String, List<Symbol>> entry : symbolsByClass.entrySet()) {
            String classId = entry.getKey();
            List<Symbol> members = entry.getValue();

            // Method 1: Field injection
            // Fields with service types are likely injected
            for (Symbol field : filterByKinds(members, SymbolKind.FIELD, SymbolKind.STATIC_FIELD)) {
                String typeId = field.typeId();
                if (typeId != null && isServiceType(typeId)) {
                    diRefs.add(createInjectsRef(classId, typeId, field.filePath()));
                }
            }

            // Method 2: Constructor injection
            // Parameters of constructors with service types
            for (Symbol ctor : filterByKind(members, SymbolKind.CONSTRUCTOR)) {
                List<Symbol> params = findParameters(symbols, ctor.id());
                for (Symbol param : params) {
                    String typeId = param.typeId();
                    if (typeId != null && isServiceType(typeId)) {
                        diRefs.add(createInjectsRef(classId, typeId, ctor.filePath()));
                    }
                }
            }
        }

        return diRefs;
    }

    /**
     * Group symbols by their containing class.
     */
    private Map<String, List<Symbol>> groupSymbolsByClass(List<Symbol> symbols) {
        // Find all class symbols
        Set<String> classIds = symbols.stream()
            .filter(s -> s.kind().isType())
            .map(Symbol::id)
            .collect(Collectors.toSet());

        // Group members by their parent class
        return symbols.stream()
            .filter(s -> s.parentId() != null && classIds.contains(s.parentId()))
            .collect(Collectors.groupingBy(Symbol::parentId));
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
        
        // Check if it's an interface (often used for DI)
        // In Java SCIP, interfaces typically end with just "#"
        if (typeId.endsWith("#") && !typeId.contains(".")) {
            return true;
        }
        
        return false;
    }

    @Override
    public SymbolMetadata getSymbolMetadata(scip.Scip.SymbolInformation symbolInfo) {
        // Extract metadata from SCIP Kind
        var kind = symbolInfo.getKind();
        
        boolean isStatic = kind == scip.Scip.SymbolInformation.Kind.StaticField ||
                          kind == scip.Scip.SymbolInformation.Kind.StaticMethod;
        boolean isAbstract = kind == scip.Scip.SymbolInformation.Kind.AbstractMethod;
        
        // TODO: Extract visibility from symbol signature or documentation
        String visibility = null;
        
        // Check for generated/test flags from SCIP roles if available
        boolean isGenerated = false;
        boolean isTest = false;
        
        return new SymbolMetadata(isStatic, isAbstract, false, visibility, isGenerated, isTest);
    }
}
