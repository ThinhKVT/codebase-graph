package org.example.search;

import org.example.llm.NLToCypherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Routes natural language queries: MVP2 uses LLM to generate Cypher (no pattern-to-Cypher fast path yet).
 */
public class QueryRouter {

    private static final Logger logger = LoggerFactory.getLogger(QueryRouter.class);

    private final NLToCypherService nlToCypherService;

    public QueryRouter(NLToCypherService nlToCypherService) {
        this.nlToCypherService = nlToCypherService;
    }

    /**
     * Route query to LLM and return generated Cypher. Always uses LLM in MVP2.
     */
    public NLToCypherService.CypherResult route(String query, boolean forceLLM) {
        logger.debug("Routing query (forceLLM={}): {}", forceLLM, truncate(query, 60));
        return nlToCypherService.translate(query);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
