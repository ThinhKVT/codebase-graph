package org.example.api.handlers;

import io.javalin.http.Context;
import org.example.api.ApiServer.NotFoundException;
import org.example.model.ReferenceKind;
import org.example.query.ReferenceQuery;
import org.example.query.ReferenceQuery.ReferenceResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * HTTP handler for reference-related endpoints.
 */
public class ReferenceHandler {

    private static final Logger logger = LoggerFactory.getLogger(ReferenceHandler.class);

    private final ReferenceQuery referenceQuery;

    public ReferenceHandler(ReferenceQuery referenceQuery) {
        this.referenceQuery = referenceQuery;
    }

    /**
     * GET /symbols/{id}/references - Get all references to a symbol
     * Query params: kind (filter by reference kind)
     */
    public void getReferences(Context ctx) {
        String symbolId = ctx.pathParam("id");
        String kindStr = ctx.queryParam("kind");

        logger.debug("Getting references for symbol: {}, kind filter: {}", symbolId, kindStr);

        ReferenceKind kindFilter = null;
        if (kindStr != null && !kindStr.isEmpty()) {
            try {
                kindFilter = ReferenceKind.valueOf(kindStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid reference kind: " + kindStr +
                    ". Valid values are: " + String.join(", ", getReferenceKindNames()));
            }
        }

        List<ReferenceResult> references = referenceQuery.findReferences(symbolId);

        if (references.isEmpty()) {
            logger.debug("No references found for symbol: {}", symbolId);
        }

        // Apply kind filter if specified
        if (kindFilter != null) {
            final ReferenceKind filter = kindFilter;
            references = references.stream()
                .filter(r -> r.kind() == filter)
                .collect(Collectors.toList());
        }

        ctx.json(new ReferenceListResponse(
            symbolId,
            references.stream().map(this::toReferenceDto).collect(Collectors.toList()),
            references.size()
        ));
    }

    private ReferenceDto toReferenceDto(ReferenceResult result) {
        return new ReferenceDto(
            result.targetSymbol() != null ? result.targetSymbol().id() : null,
            result.targetSymbol() != null ? result.targetSymbol().name() : null,
            result.targetSymbol() != null ? result.targetSymbol().fullyQualifiedName() : null,
            result.kind().name(),
            result.filePath(),
            result.line(),
            result.column(),
            result.context()
        );
    }

    private String[] getReferenceKindNames() {
        ReferenceKind[] kinds = ReferenceKind.values();
        String[] names = new String[kinds.length];
        for (int i = 0; i < kinds.length; i++) {
            names[i] = kinds[i].name();
        }
        return names;
    }

    // DTOs
    public record ReferenceDto(
        String symbolId,
        String symbolName,
        String symbolFQN,
        String kind,
        String filePath,
        int line,
        int column,
        String context
    ) {}

    public record ReferenceListResponse(
        String symbolId,
        List<ReferenceDto> references,
        int count
    ) {}
}
