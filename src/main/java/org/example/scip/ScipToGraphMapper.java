package org.example.scip;

import org.example.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scip.Scip;

import java.util.*;

/**
 * Maps SCIP entities to domain model objects.
 */
public class ScipToGraphMapper {

    private static final Logger logger = LoggerFactory.getLogger(ScipToGraphMapper.class);

    // Prefix for local symbols in SCIP
    private static final String LOCAL_SYMBOL_PREFIX = "local ";

    /**
     * Check if a symbol is a local symbol (local variable within a method/function).
     * Local symbols have the format "local <id>" (e.g., "local 0", "local 1").
     */
    private boolean isLocalSymbol(String symbolId) {
        return symbolId != null && symbolId.startsWith(LOCAL_SYMBOL_PREFIX);
    }

    /**
     * Create a qualified ID for local symbols to avoid conflicts across files.
     * Format: "local:<filePath>:<localId>"
     */
    private String qualifyLocalSymbol(String symbolId, String filePath) {
        if (isLocalSymbol(symbolId)) {
            return "local:" + filePath + ":" + symbolId.substring(LOCAL_SYMBOL_PREFIX.length());
        }
        return symbolId;
    }

    /**
     * Result of mapping a SCIP index.
     */
    public record MappingResult(
        List<SourceFile> sourceFiles,
        List<Symbol> symbols,
        List<Reference> references,
        int documentsProcessed,
        int symbolsProcessed,
        int occurrencesProcessed
    ) {}

    /**
     * Map a SCIP index to domain model objects.
     *
     * @param includeLocalSymbols if true, include local symbols (local variables); if false, skip them
     */
    public MappingResult map(Scip.Index index, String repositoryPath) {
        return map(index, repositoryPath, false); // Default: skip local symbols
    }

    /**
     * Map a SCIP index to domain model objects.
     *
     * @param includeLocalSymbols if true, include local symbols (local variables); if false, skip them
     */
    public MappingResult map(Scip.Index index, String repositoryPath, boolean includeLocalSymbols) {
        List<SourceFile> sourceFiles = new ArrayList<>();
        List<Symbol> symbols = new ArrayList<>();
        List<Reference> references = new ArrayList<>();

        // First pass: collect all symbol IDs and their kinds so we can validate references
        Set<String> definedSymbolIds = new HashSet<>();
        Map<String, SymbolKind> symbolKindMap = new HashMap<>(); // Track symbol kinds for CALL detection

        int documentsProcessed = 0;
        int symbolsProcessed = 0;
        int occurrencesProcessed = 0;
        int localSymbolsSkipped = 0;

        // First pass: collect symbols and their kinds (skip local symbols if not included)
        for (Scip.Document doc : index.getDocumentsList()) {
            String relativePath = doc.getRelativePath();
            for (Scip.SymbolInformation symbolInfo : doc.getSymbolsList()) {
                String symbolId = symbolInfo.getSymbol();
                if (symbolId != null && !symbolId.isEmpty()) {
                    if (isLocalSymbol(symbolId)) {
                        if (includeLocalSymbols) {
                            String qualifiedId = qualifyLocalSymbol(symbolId, relativePath);
                            definedSymbolIds.add(qualifiedId);
                            symbolKindMap.put(qualifiedId, mapSymbolKind(symbolInfo.getKind()));
                        }
                    } else {
                        definedSymbolIds.add(symbolId);
                        symbolKindMap.put(symbolId, mapSymbolKind(symbolInfo.getKind()));
                    }
                }
            }
        }

        // Second pass: process documents, symbols, and references
        for (Scip.Document doc : index.getDocumentsList()) {
            String relativePath = doc.getRelativePath();
            String language = doc.getLanguage();

            SourceFile sourceFile = new SourceFile(
                repositoryPath + "/" + relativePath,
                relativePath,
                null,
                language
            );
            sourceFiles.add(sourceFile);

            // Track symbols defined in this document for occurrence mapping
            Map<String, String> definitionSymbols = new HashMap<>();

            // Process symbols and extract relationships
            for (Scip.SymbolInformation symbolInfo : doc.getSymbolsList()) {
                String symbolId = symbolInfo.getSymbol();

                // Skip local symbols if not included
                if (isLocalSymbol(symbolId)) {
                    if (!includeLocalSymbols) {
                        localSymbolsSkipped++;
                        continue;
                    }
                }

                Symbol symbol = mapSymbol(symbolInfo, relativePath, includeLocalSymbols);
                if (symbol != null) {
                    symbols.add(symbol);
                    symbolsProcessed++;

                    String qualifiedId = isLocalSymbol(symbolId)
                        ? qualifyLocalSymbol(symbolId, relativePath)
                        : symbolId;
                    definitionSymbols.put(symbolId, qualifiedId);

                    // Extract relationships (EXTENDS, IMPLEMENTS, TYPE_DEFINITION)
                    // Skip relationships for local symbols as they are not meaningful
                    if (!isLocalSymbol(symbolId)) {
                        List<Reference> relationshipRefs = mapRelationships(symbolInfo, relativePath, definedSymbolIds, symbolKindMap);
                        references.addAll(relationshipRefs);
                        occurrencesProcessed += relationshipRefs.size();
                    }
                }
            }

            // Process occurrences - find the enclosing symbol for each occurrence
            String currentEnclosingSymbol = null;
            for (Scip.Occurrence occ : doc.getOccurrencesList()) {
                String occSymbol = occ.getSymbol();
                if (occSymbol == null || occSymbol.isEmpty()) {
                    continue;
                }

                // Skip local symbols in occurrences if not included
                if (isLocalSymbol(occSymbol) && !includeLocalSymbols) {
                    continue;
                }

                ReferenceKind baseKind = ReferenceKind.fromScipRole(occ.getSymbolRoles());

                // Qualify local symbol if needed
                String qualifiedOccSymbol = isLocalSymbol(occSymbol)
                    ? qualifyLocalSymbol(occSymbol, relativePath)
                    : occSymbol;

                // If this is a definition, update the current enclosing symbol
                if (baseKind == ReferenceKind.DEFINITION) {
                    // Only use non-local symbols as enclosing symbols
                    if (!isLocalSymbol(occSymbol)) {
                        currentEnclosingSymbol = occSymbol;
                    }
                    continue; // Skip creating reference for definition itself
                }

                // For references, create a reference from the enclosing symbol to the referenced symbol
                if (currentEnclosingSymbol != null && definedSymbolIds.contains(qualifiedOccSymbol)) {
                    // Don't create self-references
                    if (!currentEnclosingSymbol.equals(qualifiedOccSymbol)) {
                        int line = 0;
                        int column = 0;
                        if (occ.getRangeCount() >= 2) {
                            line = occ.getRange(0);
                            column = occ.getRange(1);
                        }

                        // Determine the actual reference kind based on target symbol type
                        ReferenceKind actualKind = determineReferenceKind(baseKind, qualifiedOccSymbol, symbolKindMap);

                        Reference ref = Reference.builder()
                            .fromSymbolId(currentEnclosingSymbol)
                            .toSymbolId(qualifiedOccSymbol)
                            .kind(actualKind)
                            .filePath(relativePath)
                            .line(line + 1)
                            .column(column + 1)
                            .build();
                        references.add(ref);
                        occurrencesProcessed++;
                    }
                }
            }

            documentsProcessed++;
        }

        if (localSymbolsSkipped > 0) {
            logger.info("Skipped {} local symbols (local variables)", localSymbolsSkipped);
        }
        logger.info("Mapped {} documents, {} symbols, {} references",
            documentsProcessed, symbolsProcessed, references.size());

        return new MappingResult(sourceFiles, symbols, references,
            documentsProcessed, symbolsProcessed, occurrencesProcessed);
    }

    /**
     * Determine the actual reference kind based on SCIP role and target symbol kind.
     * This helps distinguish CALL (method invocation) from TYPE_REF (type usage).
     */
    private ReferenceKind determineReferenceKind(ReferenceKind baseKind, String targetSymbolId, Map<String, SymbolKind> symbolKindMap) {
        // If it's already a specific kind (IMPORT, WRITE, READ), keep it
        if (baseKind != ReferenceKind.REFERENCE) {
            return baseKind;
        }

        // Look up the target symbol kind to determine if it's a CALL, TYPE_REF, etc.
        SymbolKind targetKind = symbolKindMap.get(targetSymbolId);
        if (targetKind != null) {
            return ReferenceKind.fromTargetSymbolKind(targetKind);
        }

        // Fallback: try to infer from symbol ID pattern
        if (targetSymbolId.contains("(") && targetSymbolId.contains(").")) {
            // Method signature pattern: "methodName()."
            return ReferenceKind.CALL;
        }
        if (targetSymbolId.endsWith("#")) {
            // Type pattern: "ClassName#"
            return ReferenceKind.TYPE_REF;
        }

        return baseKind;
    }

    private Symbol mapSymbol(Scip.SymbolInformation symbolInfo, String filePath, boolean includeLocalSymbols) {
        String symbolName = symbolInfo.getSymbol();
        if (symbolName == null || symbolName.isEmpty()) {
            return null;
        }

        // Qualify local symbols with file path
        String qualifiedId = isLocalSymbol(symbolName)
            ? qualifyLocalSymbol(symbolName, filePath)
            : symbolName;

        String simpleName = extractSimpleName(symbolName);
        SymbolKind kind = mapSymbolKind(symbolInfo.getKind());

        // Local symbols are typically variables
        if (isLocalSymbol(symbolName) && kind == SymbolKind.UNKNOWN) {
            kind = SymbolKind.VARIABLE;
        }

        String documentation = null;
        if (symbolInfo.getDocumentationCount() > 0) {
            documentation = String.join("\n", symbolInfo.getDocumentationList());
        }

        String signature = null;
        if (symbolInfo.hasSignatureDocumentation()) {
            signature = symbolInfo.getSignatureDocumentation().getText();
        }

        return Symbol.builder()
            .id(qualifiedId)
            .name(simpleName)
            .fullyQualifiedName(qualifiedId)
            .kind(kind)
            .filePath(filePath)
            .documentation(documentation)
            .signature(signature)
            .build();
    }

    // Keep backward compatible overload
    private Symbol mapSymbol(Scip.SymbolInformation symbolInfo, String filePath) {
        return mapSymbol(symbolInfo, filePath, false);
    }

    /**
     * Extract relationships from SymbolInformation to create EXTENDS, IMPLEMENTS, TYPE_DEFINITION references.
     */
    private List<Reference> mapRelationships(Scip.SymbolInformation symbolInfo, String filePath, Set<String> definedSymbolIds, Map<String, SymbolKind> symbolKindMap) {
        List<Reference> refs = new ArrayList<>();
        String fromSymbol = symbolInfo.getSymbol();
        SymbolKind fromKind = symbolKindMap.get(fromSymbol);

        for (Scip.Relationship rel : symbolInfo.getRelationshipsList()) {
            String toSymbol = rel.getSymbol();
            if (toSymbol == null || toSymbol.isEmpty()) {
                continue;
            }

            ReferenceKind kind = null;
            SymbolKind toKind = symbolKindMap.get(toSymbol);

            // Map relationship flags to ReferenceKind
            if (rel.getIsImplementation()) {
                // Distinguish IMPLEMENTS vs EXTENDS based on target symbol kind
                if (toKind == SymbolKind.INTERFACE) {
                    kind = ReferenceKind.IMPLEMENTS;
                } else if (toKind == SymbolKind.CLASS) {
                    kind = ReferenceKind.EXTENDS;
                } else {
                    // Fallback: if source is class/interface, check target symbol pattern
                    if (toSymbol.endsWith("#") || toSymbol.contains("#")) {
                        // Could be either - default to IMPLEMENTS for is_implementation flag
                        kind = ReferenceKind.IMPLEMENTS;
                    } else {
                        kind = ReferenceKind.IMPLEMENTS;
                    }
                }
            } else if (rel.getIsTypeDefinition()) {
                kind = ReferenceKind.TYPE_DEFINITION;
            } else if (rel.getIsReference()) {
                // Determine more specific kind based on target symbol
                if (toKind != null) {
                    kind = ReferenceKind.fromTargetSymbolKind(toKind);
                } else {
                    kind = ReferenceKind.REFERENCE;
                }
            } else if (rel.getIsDefinition()) {
                kind = ReferenceKind.DEFINITION;
            }

            if (kind != null) {
                Reference ref = Reference.builder()
                    .fromSymbolId(fromSymbol)
                    .toSymbolId(toSymbol)
                    .kind(kind)
                    .filePath(filePath)
                    .line(0)
                    .column(0)
                    .build();
                refs.add(ref);

                logger.debug("Mapped relationship: {} -> {} [{}]", fromSymbol, toSymbol, kind);
            }
        }

        return refs;
    }

    private String extractSimpleName(String symbolName) {
        if (symbolName == null || symbolName.isEmpty()) {
            return "unknown";
        }

        String[] parts = symbolName.split("[/#.]");
        for (int i = parts.length - 1; i >= 0; i--) {
            String part = parts[i].trim();
            if (!part.isEmpty() && !part.equals("()") && !part.matches("\\(.*\\)")) {
                int parenIdx = part.indexOf('(');
                if (parenIdx > 0) {
                    return part.substring(0, parenIdx);
                }
                return part;
            }
        }
        return symbolName;
    }

    private SymbolKind mapSymbolKind(Scip.SymbolInformation.Kind scipKind) {
        return switch (scipKind) {
            case Package -> SymbolKind.PACKAGE;
            case Class -> SymbolKind.CLASS;
            case Interface -> SymbolKind.INTERFACE;
            case Enum -> SymbolKind.ENUM;
            case EnumMember -> SymbolKind.ENUM_MEMBER;
            case Method -> SymbolKind.METHOD;
            case Field -> SymbolKind.FIELD;
            case Constructor -> SymbolKind.CONSTRUCTOR;
            case Function -> SymbolKind.FUNCTION;
            case Variable -> SymbolKind.VARIABLE;
            case Parameter -> SymbolKind.PARAMETER;
            case TypeParameter -> SymbolKind.TYPE_PARAMETER;
            case Module -> SymbolKind.MODULE;
            default -> SymbolKind.UNKNOWN;
        };
    }
}
