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
        List<SourceFile> sourceFiles = new ArrayList<>();
        List<Symbol> symbols = new ArrayList<>();
        List<Reference> references = new ArrayList<>();

        int documentsProcessed = 0;
        int symbolsProcessed = 0;
        int occurrencesProcessed = 0;

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

            for (Scip.SymbolInformation symbolInfo : doc.getSymbolsList()) {
                Symbol symbol = mapSymbol(symbolInfo, relativePath);
                if (symbol != null) {
                    symbols.add(symbol);
                    symbolsProcessed++;
                }
            }

            for (Scip.Occurrence occ : doc.getOccurrencesList()) {
                Reference ref = mapOccurrence(occ, relativePath);
                if (ref != null) {
                    references.add(ref);
                    occurrencesProcessed++;
                }
            }

            documentsProcessed++;
        }

        logger.info("Mapped {} documents, {} symbols, {} occurrences",
            documentsProcessed, symbolsProcessed, occurrencesProcessed);

        return new MappingResult(sourceFiles, symbols, references,
            documentsProcessed, symbolsProcessed, occurrencesProcessed);
    }

    private Symbol mapSymbol(Scip.SymbolInformation symbolInfo, String filePath) {
        String symbolName = symbolInfo.getSymbol();
        if (symbolName == null || symbolName.isEmpty()) {
            return null;
        }

        String simpleName = extractSimpleName(symbolName);
        SymbolKind kind = mapSymbolKind(symbolInfo.getKind());

        String documentation = null;
        if (symbolInfo.getDocumentationCount() > 0) {
            documentation = String.join("\n", symbolInfo.getDocumentationList());
        }

        String signature = null;
        if (symbolInfo.hasSignatureDocumentation()) {
            signature = symbolInfo.getSignatureDocumentation().getText();
        }

        return Symbol.builder()
            .id(symbolName)
            .name(simpleName)
            .fullyQualifiedName(symbolName)
            .kind(kind)
            .filePath(filePath)
            .documentation(documentation)
            .signature(signature)
            .build();
    }

    private Reference mapOccurrence(Scip.Occurrence occ, String filePath) {
        String symbol = occ.getSymbol();
        if (symbol == null || symbol.isEmpty()) {
            return null;
        }

        ReferenceKind kind = mapOccurrenceRole(occ.getSymbolRoles());

        int line = 0;
        int column = 0;
        if (occ.getRangeCount() >= 2) {
            line = occ.getRange(0);
            column = occ.getRange(1);
        }

        return Reference.builder()
            .fromSymbolId(filePath)
            .toSymbolId(symbol)
            .kind(kind)
            .filePath(filePath)
            .line(line + 1)
            .column(column + 1)
            .build();
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

    private ReferenceKind mapOccurrenceRole(int role) {
        if ((role & 0x1) != 0) return ReferenceKind.DEFINITION;
        if ((role & 0x2) != 0) return ReferenceKind.IMPORT;
        if ((role & 0x4) != 0) return ReferenceKind.WRITE;
        if ((role & 0x8) != 0) return ReferenceKind.READ;
        return ReferenceKind.REFERENCE;
    }
}

