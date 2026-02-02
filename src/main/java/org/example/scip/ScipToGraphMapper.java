package org.example.scip;

import org.example.model.*;
import org.example.scip.mapper.LanguageMappingStrategy;
import org.example.scip.mapper.MappingStrategyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scip.Scip;

import java.util.*;

/**
 * Maps SCIP entities to domain model objects.
 * 
 * <p>Uses {@link LanguageMappingStrategy} for language-specific processing
 * such as dependency injection detection and CONTAINS relationships.</p>
 */
public class ScipToGraphMapper {

    private static final Logger logger = LoggerFactory.getLogger(ScipToGraphMapper.class);

    // Prefix for local symbols in SCIP
    private static final String LOCAL_SYMBOL_PREFIX = "local ";

    // Strategy for language-specific mapping
    private final LanguageMappingStrategy strategy;

    /**
     * Create mapper with default (Java) strategy.
     */
    public ScipToGraphMapper() {
        this.strategy = MappingStrategyFactory.create(LanguageSupport.JAVA);
    }

    /**
     * Create mapper with specific strategy.
     */
    public ScipToGraphMapper(LanguageMappingStrategy strategy) {
        this.strategy = strategy;
    }

    /**
     * Factory method to create mapper for a specific language.
     */
    public static ScipToGraphMapper forLanguage(String language) {
        LanguageMappingStrategy strat = MappingStrategyFactory.create(language);
        return new ScipToGraphMapper(strat);
    }

    /**
     * Factory method to create mapper for a specific language.
     */
    public static ScipToGraphMapper forLanguage(LanguageSupport language) {
        LanguageMappingStrategy strat = MappingStrategyFactory.create(language);
        return new ScipToGraphMapper(strat);
    }

    /**
     * Check if a symbol is a local symbol (local variable within a method/function).
     */
    private boolean isLocalSymbol(String symbolId) {
        return symbolId != null && symbolId.startsWith(LOCAL_SYMBOL_PREFIX);
    }

    /**
     * Create a qualified ID for local symbols to avoid conflicts across files.
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
     */
    public MappingResult map(Scip.Index index, String repositoryPath) {
        return map(index, repositoryPath, false);
    }

    /**
     * Map a SCIP index to domain model objects.
     *
     * @param index The SCIP index to map
     * @param repositoryPath Path to the repository
     * @param includeLocalSymbols if true, include local symbols (local variables)
     */
    public MappingResult map(Scip.Index index, String repositoryPath, boolean includeLocalSymbols) {
        List<SourceFile> sourceFiles = new ArrayList<>();
        List<Symbol> symbols = new ArrayList<>();
        List<Reference> references = new ArrayList<>();

        // Collect all symbol information for strategy processing
        List<Scip.SymbolInformation> allSymbolInfos = new ArrayList<>();

        // First pass: collect all symbol IDs and their kinds
        Set<String> definedSymbolIds = new HashSet<>();
        Map<String, SymbolKind> symbolKindMap = new HashMap<>();

        int documentsProcessed = 0;
        int symbolsProcessed = 0;
        int occurrencesProcessed = 0;
        int localSymbolsSkipped = 0;

        // Map to store symbol definition locations (startLine, endLine)
        Map<String, int[]> symbolLocationMap = new HashMap<>();

        // First pass: collect symbols, their kinds, and definition locations
        for (Scip.Document doc : index.getDocumentsList()) {
            String relativePath = doc.getRelativePath();
            
            // Collect definition locations from occurrences
            for (Scip.Occurrence occ : doc.getOccurrencesList()) {
                String occSymbol = occ.getSymbol();
                if (occSymbol == null || occSymbol.isEmpty()) continue;
                
                // Check if this is a definition occurrence (symbolRoles contains Definition bit)
                int roles = occ.getSymbolRoles();
                boolean isDefinition = (roles & 1) != 0; // Definition = 1 in SymbolRole enum
                
                if (isDefinition && occ.getRangeCount() >= 1) {
                    int startLine = occ.getRange(0);
                    int endLine = occ.getRangeCount() >= 3 ? occ.getRange(2) : startLine;
                    
                    String qualifiedId = isLocalSymbol(occSymbol) 
                        ? qualifyLocalSymbol(occSymbol, relativePath) 
                        : occSymbol;
                    symbolLocationMap.put(qualifiedId, new int[]{startLine + 1, endLine + 1}); // Convert to 1-based
                }
            }
            
            for (Scip.SymbolInformation symbolInfo : doc.getSymbolsList()) {
                String symbolId = symbolInfo.getSymbol();
                if (symbolId != null && !symbolId.isEmpty()) {
                    allSymbolInfos.add(symbolInfo);
                    
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

            // Process symbols
            for (Scip.SymbolInformation symbolInfo : doc.getSymbolsList()) {
                String symbolId = symbolInfo.getSymbol();

                if (isLocalSymbol(symbolId)) {
                    if (!includeLocalSymbols) {
                        localSymbolsSkipped++;
                        continue;
                    }
                }

                Symbol symbol = mapSymbol(symbolInfo, relativePath, includeLocalSymbols, symbolKindMap, symbolLocationMap);
                if (symbol != null) {
                    symbols.add(symbol);
                    symbolsProcessed++;

                    // Extract relationships (EXTENDS, IMPLEMENTS, TYPE_DEFINITION)
                    if (!isLocalSymbol(symbolId)) {
                        List<Reference> relationshipRefs = mapRelationships(
                            symbolInfo, relativePath, definedSymbolIds, symbolKindMap);
                        references.addAll(relationshipRefs);
                        occurrencesProcessed += relationshipRefs.size();
                    }
                }
            }

            // Process occurrences
            String currentEnclosingSymbol = null;
            for (Scip.Occurrence occ : doc.getOccurrencesList()) {
                String occSymbol = occ.getSymbol();
                if (occSymbol == null || occSymbol.isEmpty()) {
                    continue;
                }

                if (isLocalSymbol(occSymbol) && !includeLocalSymbols) {
                    continue;
                }

                ReferenceKind baseKind = ReferenceKind.fromScipRole(occ.getSymbolRoles());
                String qualifiedOccSymbol = isLocalSymbol(occSymbol)
                    ? qualifyLocalSymbol(occSymbol, relativePath)
                    : occSymbol;

                if (baseKind == ReferenceKind.DEFINITION) {
                    if (!isLocalSymbol(occSymbol)) {
                        currentEnclosingSymbol = occSymbol;
                    }
                    continue;
                }

                if (currentEnclosingSymbol != null && definedSymbolIds.contains(qualifiedOccSymbol)) {
                    if (!currentEnclosingSymbol.equals(qualifiedOccSymbol)) {
                        int line = occ.getRangeCount() >= 1 ? occ.getRange(0) : 0;
                        int column = occ.getRangeCount() >= 2 ? occ.getRange(1) : 0;

                        ReferenceKind actualKind = determineReferenceKind(
                            baseKind, qualifiedOccSymbol, symbolKindMap);

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

        // ============= Language-Specific Processing (Strategy Pattern) =============

        // Extract CONTAINS relationships using strategy
        try {
            List<Reference> containsRefs = strategy.extractContainsRelationships(
                allSymbolInfos, symbolKindMap);
            references.addAll(containsRefs);
            logger.debug("Added {} CONTAINS relationships", containsRefs.size());
        } catch (Exception e) {
            logger.warn("Failed to extract CONTAINS relationships: {}", e.getMessage());
        }

        // Extract type relationships using strategy
        try {
            for (Scip.SymbolInformation symbolInfo : allSymbolInfos) {
                List<Reference> typeRefs = strategy.extractTypeRelationships(
                    symbolInfo, symbolKindMap);
                references.addAll(typeRefs);
            }
        } catch (Exception e) {
            logger.warn("Failed to extract type relationships: {}", e.getMessage());
        }

        // Extract dependency injection relationships using strategy
        try {
            List<Reference> diRefs = strategy.extractDependencyInjection(
                symbols, references, symbolKindMap);
            references.addAll(diRefs);
            logger.debug("Added {} INJECTS relationships", diRefs.size());
        } catch (Exception e) {
            logger.warn("Failed to extract DI relationships: {}", e.getMessage());
        }

        // ============= End Strategy Processing =============

        if (localSymbolsSkipped > 0) {
            logger.info("Skipped {} local symbols", localSymbolsSkipped);
        }
        logger.info("Mapped {} documents, {} symbols, {} references",
            documentsProcessed, symbolsProcessed, references.size());

        return new MappingResult(sourceFiles, symbols, references,
            documentsProcessed, symbolsProcessed, occurrencesProcessed);
    }

    /**
     * Determine the actual reference kind based on SCIP role and target symbol kind.
     */
    private ReferenceKind determineReferenceKind(
            ReferenceKind baseKind, String targetSymbolId, Map<String, SymbolKind> symbolKindMap) {
        
        if (baseKind != ReferenceKind.REFERENCE) {
            return baseKind;
        }

        SymbolKind targetKind = symbolKindMap.get(targetSymbolId);
        if (targetKind != null) {
            return ReferenceKind.fromTargetSymbolKind(targetKind);
        }

        // Fallback: infer from symbol ID pattern
        if (targetSymbolId.contains("(") && targetSymbolId.contains(").")) {
            return ReferenceKind.CALL;
        }
        if (targetSymbolId.endsWith("#")) {
            return ReferenceKind.TYPE_REF;
        }

        return baseKind;
    }

    private Symbol mapSymbol(Scip.SymbolInformation symbolInfo, String filePath, 
                            boolean includeLocalSymbols, Map<String, SymbolKind> symbolKindMap,
                            Map<String, int[]> symbolLocationMap) {
        String symbolName = symbolInfo.getSymbol();
        if (symbolName == null || symbolName.isEmpty()) {
            return null;
        }

        String qualifiedId = isLocalSymbol(symbolName)
            ? qualifyLocalSymbol(symbolName, filePath)
            : symbolName;

        String simpleName = extractSimpleName(symbolName);
        SymbolKind kind = mapSymbolKind(symbolInfo.getKind());

        if (isLocalSymbol(symbolName) && kind == SymbolKind.UNKNOWN) {
            kind = SymbolKind.VARIABLE;
        }

        String documentation = symbolInfo.getDocumentationCount() > 0
            ? String.join("\n", symbolInfo.getDocumentationList())
            : null;

        String signature = symbolInfo.hasSignatureDocumentation()
            ? symbolInfo.getSignatureDocumentation().getText()
            : null;

        // Get display name if available
        String displayName = symbolInfo.getDisplayName();
        if (displayName == null || displayName.isEmpty()) {
            displayName = null;
        }

        // Parse parent ID using strategy
        String parentId = strategy.parseParentSymbolId(symbolName);

        // Get metadata from strategy
        LanguageMappingStrategy.SymbolMetadata metadata = strategy.getSymbolMetadata(symbolInfo);

        // Get location from definition occurrence
        int[] location = symbolLocationMap.get(qualifiedId);
        int startLine = location != null ? location[0] : 0;
        int endLine = location != null ? location[1] : 0;

        return Symbol.builder()
            .id(qualifiedId)
            .name(simpleName)
            .fullyQualifiedName(qualifiedId)
            .kind(kind)
            .filePath(filePath)
            .startLine(startLine)
            .endLine(endLine)
            .documentation(documentation)
            .signature(signature)
            .displayName(displayName)
            .parentId(parentId)
            .isStatic(metadata.isStatic())
            .isAbstract(metadata.isAbstract())
            .isFinal(metadata.isFinal())
            .visibility(metadata.visibility())
            .isGenerated(metadata.isGenerated())
            .isTest(metadata.isTest())
            .build();
    }

    private List<Reference> mapRelationships(
            Scip.SymbolInformation symbolInfo, String filePath,
            Set<String> definedSymbolIds, Map<String, SymbolKind> symbolKindMap) {
        
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

            if (rel.getIsImplementation()) {
                if (toKind == SymbolKind.INTERFACE) {
                    kind = ReferenceKind.IMPLEMENTS;
                } else if (toKind == SymbolKind.CLASS) {
                    kind = ReferenceKind.EXTENDS;
                } else {
                    kind = ReferenceKind.IMPLEMENTS;
                }
            } else if (rel.getIsTypeDefinition()) {
                // Use strategy to determine HAS_TYPE vs RETURNS_TYPE
                if (fromKind != null && fromKind.isCallable()) {
                    kind = ReferenceKind.RETURNS_TYPE;
                } else if (fromKind != null && (fromKind.isField() || fromKind == SymbolKind.PARAMETER)) {
                    kind = ReferenceKind.HAS_TYPE;
                } else {
                    kind = ReferenceKind.TYPE_DEFINITION;
                }
            } else if (rel.getIsReference()) {
                kind = toKind != null 
                    ? ReferenceKind.fromTargetSymbolKind(toKind) 
                    : ReferenceKind.REFERENCE;
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
            case Struct -> SymbolKind.STRUCT;
            case Trait -> SymbolKind.TRAIT;
            case StaticMethod -> SymbolKind.STATIC_METHOD;
            case StaticField -> SymbolKind.STATIC_FIELD;
            case AbstractMethod -> SymbolKind.ABSTRACT_METHOD;
            case Constant -> SymbolKind.CONSTANT;
            default -> SymbolKind.UNKNOWN;
        };
    }
}
