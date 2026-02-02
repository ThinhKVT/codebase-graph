package org.example.api.handlers;

import io.javalin.http.Context;
import org.example.graph.GraphStore;
import org.example.llm.NLToCypherService;
import org.example.search.QueryRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * HTTP handler for POST /query/natural - natural language to Cypher execution.
 */
public class NaturalQueryHandler {

    private static final Logger logger = LoggerFactory.getLogger(NaturalQueryHandler.class);

    private final GraphStore graphStore;
    private final QueryRouter queryRouter;

    public NaturalQueryHandler(GraphStore graphStore, QueryRouter queryRouter) {
        this.graphStore = graphStore;
        this.queryRouter = queryRouter;
    }

    /**
     * POST /query/natural
     * Body: { "query": "Find all classes", "options": { "force_llm": false, "validate": true, "explain": true } }
     */
    public void handle(Context ctx) {
        NaturalRequest body = ctx.bodyAsClass(NaturalRequest.class);
        if (body == null || body.query == null || body.query.isBlank()) {
            ctx.status(400).json(new ErrorResponse("Body must contain non-empty 'query'"));
            return;
        }
        boolean forceLLM = body.options != null && Boolean.TRUE.equals(body.options.force_llm);
        long start = System.currentTimeMillis();
        try {
            var cypherResult = queryRouter.route(body.query, forceLLM);
            List<Map<String, Object>> results = graphStore.runQuery(cypherResult.cypher(), Map.of());
            long elapsed = System.currentTimeMillis() - start;
            String explanation = (body.options != null && Boolean.TRUE.equals(body.options.explain))
                ? "Executed generated Cypher via " + cypherResult.method() + "."
                : null;
            ctx.json(new NaturalResponse(
                body.query,
                cypherResult.method(),
                cypherResult.cypher(),
                explanation,
                results,
                elapsed
            ));
        } catch (NLToCypherService.InvalidCypherException e) {
            logger.warn("Invalid Cypher: {}", e.getMessage());
            ctx.status(400).json(new ErrorResponse("Invalid Cypher: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Natural query failed", e);
            ctx.status(500).json(new ErrorResponse("Query failed: " + e.getMessage()));
        }
    }

    public record NaturalRequest(String query, NaturalOptions options) {}
    public record NaturalOptions(Boolean force_llm, Boolean validate, Boolean explain) {}

    public record NaturalResponse(
        String original_query,
        String method,
        String generated_cypher,
        String explanation,
        List<Map<String, Object>> results,
        long execution_time_ms
    ) {}

    public record ErrorResponse(String error) {}
}
