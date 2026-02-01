package org.example.api.handlers;

import io.javalin.http.Context;
import org.example.api.ApiServer;
import org.example.model.CodeSnippet;
import org.example.model.Symbol;
import org.example.graph.GraphStore;
import org.example.service.SourceCodeExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * HTTP handler for code retrieval endpoints: source by symbol ID, by FQN, and batch.
 */
public class CodeRetrievalHandler {

    private static final Logger logger = LoggerFactory.getLogger(CodeRetrievalHandler.class);
    private static final int DEFAULT_CONTEXT_LINES = 5;
    private static final int MAX_CONTEXT_LINES = 50;

    private final GraphStore graphStore;
    private final SourceCodeExtractor extractor;

    public CodeRetrievalHandler(GraphStore graphStore, SourceCodeExtractor extractor) {
        this.graphStore = graphStore;
        this.extractor = extractor;
    }

    /**
     * GET /symbols/{id}/source?context_lines=5
     */
    public void getSymbolSource(Context ctx) {
        String id = ctx.pathParam("id");
        int contextLines = parseContextLines(ctx.queryParam("context_lines"));
        logger.debug("Get source for symbol id={}, context_lines={}", id, contextLines);

        Optional<Symbol> symbolOpt = graphStore.findSymbolById(id);
        if (symbolOpt.isEmpty()) {
            throw new ApiServer.NotFoundException("Symbol not found: " + id);
        }
        Symbol symbol = symbolOpt.get();
        if (symbol.filePath() == null || symbol.filePath().isBlank()) {
            ctx.status(404).json(new ErrorResponse("Symbol has no file path"));
            return;
        }
        if (symbol.startLine() <= 0 || symbol.endLine() <= 0) {
            ctx.status(404).json(new ErrorResponse("Symbol has no line range"));
            return;
        }

        try {
            CodeSnippet snippet = extractor.extract(
                symbol.filePath(),
                symbol.startLine(),
                symbol.endLine(),
                contextLines
            );
            ctx.json(toSourceResponse(symbol, snippet));
        } catch (SourceCodeExtractor.SourceNotFoundException e) {
            logger.warn("Source file not found: {}", e.getMessage());
            ctx.status(404).json(new ErrorResponse("Source file not found: " + e.getMessage()));
        } catch (IOException e) {
            logger.error("Failed to read source file", e);
            ctx.status(500).json(new ErrorResponse("Failed to read source: " + e.getMessage()));
        }
    }

    /**
     * GET /code?qualified_name=...&context_lines=5
     */
    public void getCode(Context ctx) {
        String qualifiedName = ctx.queryParam("qualified_name");
        if (qualifiedName == null || qualifiedName.isBlank()) {
            ctx.status(400).json(new ErrorResponse("Query parameter 'qualified_name' is required"));
            return;
        }
        int contextLines = parseContextLines(ctx.queryParam("context_lines"));
        logger.debug("Get code for fqn={}, context_lines={}", qualifiedName, contextLines);

        Optional<Symbol> symbolOpt = graphStore.findSymbolByFQN(qualifiedName);
        if (symbolOpt.isEmpty()) {
            throw new ApiServer.NotFoundException("Symbol not found for FQN: " + qualifiedName);
        }
        Symbol symbol = symbolOpt.get();
        if (symbol.filePath() == null || symbol.filePath().isBlank()) {
            ctx.status(404).json(new ErrorResponse("Symbol has no file path"));
            return;
        }
        if (symbol.startLine() <= 0 || symbol.endLine() <= 0) {
            ctx.status(404).json(new ErrorResponse("Symbol has no line range"));
            return;
        }

        try {
            CodeSnippet snippet = extractor.extract(
                symbol.filePath(),
                symbol.startLine(),
                symbol.endLine(),
                contextLines
            );
            ctx.json(toSourceResponse(symbol, snippet));
        } catch (SourceCodeExtractor.SourceNotFoundException e) {
            logger.warn("Source file not found: {}", e.getMessage());
            ctx.status(404).json(new ErrorResponse("Source file not found: " + e.getMessage()));
        } catch (IOException e) {
            logger.error("Failed to read source file", e);
            ctx.status(500).json(new ErrorResponse("Failed to read source: " + e.getMessage()));
        }
    }

    /**
     * POST /code/batch
     * Body: { "symbol_ids": ["id1", "id2"], "context_lines": 3 }
     */
    public void getCodeBatch(Context ctx) {
        BatchRequest body = ctx.bodyAsClass(BatchRequest.class);
        if (body == null || body.symbolIds == null || body.symbolIds.isEmpty()) {
            ctx.status(400).json(new ErrorResponse("Body must contain non-empty 'symbol_ids' array"));
            return;
        }
        int contextLines = body.contextLines != null && body.contextLines >= 0
            ? Math.min(body.contextLines, MAX_CONTEXT_LINES)
            : DEFAULT_CONTEXT_LINES;

        List<SourceResponse> results = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (String symbolId : body.symbolIds) {
            Optional<Symbol> symbolOpt = graphStore.findSymbolById(symbolId);
            if (symbolOpt.isEmpty()) {
                errors.add("Symbol not found: " + symbolId);
                continue;
            }
            Symbol symbol = symbolOpt.get();
            if (symbol.filePath() == null || symbol.filePath().isBlank()) {
                errors.add("Symbol has no file path: " + symbolId);
                continue;
            }
            if (symbol.startLine() <= 0 || symbol.endLine() <= 0) {
                errors.add("Symbol has no line range: " + symbolId);
                continue;
            }
            try {
                CodeSnippet snippet = extractor.extract(
                    symbol.filePath(),
                    symbol.startLine(),
                    symbol.endLine(),
                    contextLines
                );
                results.add(toSourceResponse(symbol, snippet));
            } catch (SourceCodeExtractor.SourceNotFoundException e) {
                errors.add("File not found for " + symbolId + ": " + e.getMessage());
            } catch (IOException e) {
                errors.add("Read failed for " + symbolId + ": " + e.getMessage());
            }
        }

        ctx.json(new BatchResponse(results, errors));
    }

    private static int parseContextLines(String param) {
        if (param == null || param.isBlank()) return DEFAULT_CONTEXT_LINES;
        try {
            int n = Integer.parseInt(param);
            return Math.max(0, Math.min(n, MAX_CONTEXT_LINES));
        } catch (NumberFormatException e) {
            return DEFAULT_CONTEXT_LINES;
        }
    }

    private static SourceResponse toSourceResponse(Symbol symbol, CodeSnippet snippet) {
        return new SourceResponse(
            symbol.id(),
            symbol.fullyQualifiedName(),
            symbol.filePath(),
            symbol.startLine(),
            symbol.endLine(),
            snippet.sourceCode(),
            new ContextDto(snippet.contextBefore(), snippet.contextAfter())
        );
    }

    public record SourceResponse(
        String symbolId,
        String qualifiedName,
        String filePath,
        int startLine,
        int endLine,
        String sourceCode,
        ContextDto context
    ) {}

    public record ContextDto(String before, String after) {}

    public record BatchRequest(List<String> symbolIds, Integer contextLines) {}

    public record BatchResponse(List<SourceResponse> results, List<String> errors) {}

    public record ErrorResponse(String error) {}
}
