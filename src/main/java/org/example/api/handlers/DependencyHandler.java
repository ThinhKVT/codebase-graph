package org.example.api.handlers;

import io.javalin.http.Context;
import org.example.model.Symbol;
import org.example.query.DependencyQuery;
import org.example.query.DependencyQuery.DependencyNode;
import org.example.query.DependencyQuery.DependencyResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * HTTP handler for dependency-related endpoints.
 */
public class DependencyHandler {

    private static final Logger logger = LoggerFactory.getLogger(DependencyHandler.class);
    private static final int DEFAULT_DEPTH = 1;
    private static final int MAX_DEPTH = 10;

    private final DependencyQuery dependencyQuery;

    public DependencyHandler(DependencyQuery dependencyQuery) {
        this.dependencyQuery = dependencyQuery;
    }

    /**
     * GET /symbols/{id}/dependencies - Get dependencies of a symbol
     * Query params: depth (default 1, max 10)
     */
    public void getDependencies(Context ctx) {
        String symbolId = ctx.pathParam("id");
        int depth = parseDepth(ctx.queryParam("depth"));

        logger.debug("Getting dependencies for symbol: {}, depth: {}", symbolId, depth);

        DependencyResult result = dependencyQuery.findDependencies(symbolId, depth);

        if (result.rootSymbol() == null) {
            ctx.json(new DependencyResponse(
                symbolId,
                null,
                List.of(),
                0,
                depth
            ));
            return;
        }

        List<SymbolDto> dependencies = collectDependencies(result.tree());

        ctx.json(new DependencyResponse(
            symbolId,
            toSymbolDto(result.rootSymbol()),
            dependencies,
            dependencies.size(),
            depth
        ));
    }

    /**
     * GET /symbols/{id}/dependents - Get symbols that depend on this symbol
     * Query params: depth (default 1, max 10)
     */
    public void getDependents(Context ctx) {
        String symbolId = ctx.pathParam("id");
        int depth = parseDepth(ctx.queryParam("depth"));

        logger.debug("Getting dependents for symbol: {}, depth: {}", symbolId, depth);

        DependencyResult result = dependencyQuery.findDependents(symbolId, depth);

        if (result.rootSymbol() == null) {
            ctx.json(new DependencyResponse(
                symbolId,
                null,
                List.of(),
                0,
                depth
            ));
            return;
        }

        List<SymbolDto> dependents = collectDependencies(result.tree());

        ctx.json(new DependencyResponse(
            symbolId,
            toSymbolDto(result.rootSymbol()),
            dependents,
            dependents.size(),
            depth
        ));
    }

    private int parseDepth(String depthStr) {
        if (depthStr == null || depthStr.isEmpty()) {
            return DEFAULT_DEPTH;
        }
        try {
            int depth = Integer.parseInt(depthStr);
            if (depth < 1) {
                throw new IllegalArgumentException("Depth must be at least 1");
            }
            if (depth > MAX_DEPTH) {
                throw new IllegalArgumentException("Depth cannot exceed " + MAX_DEPTH);
            }
            return depth;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid depth value: " + depthStr);
        }
    }

    private List<SymbolDto> collectDependencies(DependencyNode node) {
        if (node == null || node.children() == null) {
            return List.of();
        }
        return node.children().stream()
            .map(this::toSymbolDtoWithChildren)
            .collect(Collectors.toList());
    }

    private SymbolDto toSymbolDto(Symbol symbol) {
        return new SymbolDto(
            symbol.id(),
            symbol.name(),
            symbol.fullyQualifiedName(),
            symbol.kind().name(),
            symbol.filePath(),
            symbol.startLine(),
            null
        );
    }

    private SymbolDto toSymbolDtoWithChildren(DependencyNode node) {
        List<SymbolDto> children = null;
        if (node.children() != null && !node.children().isEmpty()) {
            children = node.children().stream()
                .map(this::toSymbolDtoWithChildren)
                .collect(Collectors.toList());
        }
        return new SymbolDto(
            node.symbol().id(),
            node.symbol().name(),
            node.symbol().fullyQualifiedName(),
            node.symbol().kind().name(),
            node.symbol().filePath(),
            node.symbol().startLine(),
            children
        );
    }

    // DTOs
    public record SymbolDto(
        String id,
        String name,
        String fullyQualifiedName,
        String kind,
        String filePath,
        int line,
        List<SymbolDto> dependencies
    ) {}

    public record DependencyResponse(
        String symbolId,
        SymbolDto symbol,
        List<SymbolDto> dependencies,
        int count,
        int depth
    ) {}
}

