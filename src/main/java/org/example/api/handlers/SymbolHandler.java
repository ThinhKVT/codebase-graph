package org.example.api.handlers;

import io.javalin.http.Context;
import org.example.api.ApiServer.NotFoundException;
import org.example.model.Symbol;
import org.example.model.SymbolKind;
import org.example.query.SymbolQuery;
import org.example.query.SymbolQuery.SymbolSearchCriteria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * HTTP handler for symbol-related endpoints.
 */
public class SymbolHandler {

    private static final Logger logger = LoggerFactory.getLogger(SymbolHandler.class);

    private final SymbolQuery symbolQuery;

    public SymbolHandler(SymbolQuery symbolQuery) {
        this.symbolQuery = symbolQuery;
    }

    /**
     * GET /symbols - List/search symbols
     * Query params: name, kind, file, package, fqn
     */
    public void listSymbols(Context ctx) {
        String name = ctx.queryParam("name");
        String kindStr = ctx.queryParam("kind");
        String file = ctx.queryParam("file");
        String packageName = ctx.queryParam("package");
        String fqn = ctx.queryParam("fqn");

        logger.debug("Listing symbols with filters - name: {}, kind: {}, file: {}, package: {}, fqn: {}",
                     name, kindStr, file, packageName, fqn);

        SymbolKind kind = null;
        if (kindStr != null && !kindStr.isEmpty()) {
            try {
                kind = SymbolKind.valueOf(kindStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid symbol kind: " + kindStr +
                    ". Valid values are: " + String.join(", ", getSymbolKindNames()));
            }
        }

        List<Symbol> symbols;

        // If FQN is provided, do exact lookup
        if (fqn != null && !fqn.isEmpty()) {
            Optional<Symbol> symbol = symbolQuery.findByFQN(fqn);
            symbols = symbol.map(List::of).orElse(List.of());
        } else {
            // Build search criteria
            SymbolSearchCriteria criteria = SymbolSearchCriteria.builder()
                .name(name)
                .kind(kind)
                .filePath(file)
                .packageName(packageName)
                .build();

            symbols = symbolQuery.find(criteria);
        }

        ctx.json(new SymbolListResponse(symbols, symbols.size()));
    }

    /**
     * GET /symbols/{id} - Get symbol by ID
     */
    public void getSymbol(Context ctx) {
        String id = ctx.pathParam("id");
        logger.debug("Getting symbol by ID: {}", id);

        // First try as direct ID
        Optional<Symbol> symbol = symbolQuery.findByFQN(id);

        // If not found by FQN, try by name
        if (symbol.isEmpty()) {
            List<Symbol> byName = symbolQuery.findByName(id);
            if (!byName.isEmpty()) {
                symbol = Optional.of(byName.get(0));
            }
        }

        if (symbol.isEmpty()) {
            throw new NotFoundException("Symbol not found: " + id);
        }

        ctx.json(symbol.get());
    }

    private String[] getSymbolKindNames() {
        SymbolKind[] kinds = SymbolKind.values();
        String[] names = new String[kinds.length];
        for (int i = 0; i < kinds.length; i++) {
            names[i] = kinds[i].name();
        }
        return names;
    }

    // Response records
    public record SymbolListResponse(List<Symbol> symbols, int count) {}
}

