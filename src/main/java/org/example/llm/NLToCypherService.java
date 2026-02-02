package org.example.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Translates natural language to Cypher using LLM, with validation and sanitization.
 */
public class NLToCypherService {

    private static final Logger logger = LoggerFactory.getLogger(NLToCypherService.class);

    private final LLMClient llmClient;
    private final CypherPromptBuilder promptBuilder;
    private final CypherValidator validator;

    public NLToCypherService(LLMClient llmClient, CypherPromptBuilder promptBuilder, CypherValidator validator) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.validator = validator;
    }

    public NLToCypherService(LLMClient llmClient) {
        this(llmClient, new CypherPromptBuilder(), new CypherValidator());
    }

    /**
     * Translate natural language query to Cypher. Returns the Cypher string and method ("llm").
     *
     * @throws InvalidCypherException if LLM returns invalid or non-read-only Cypher
     */
    public CypherResult translate(String naturalLanguageQuery) {
        String prompt = promptBuilder.buildPrompt(naturalLanguageQuery);
        String rawResponse = llmClient.complete(prompt);
        String cypher = extractCypher(rawResponse);
        if (cypher.isBlank()) {
            throw new InvalidCypherException("LLM returned empty Cypher");
        }
        if (!validator.isValid(cypher)) {
            throw new InvalidCypherException("Generated Cypher is invalid or not read-only: " + cypher);
        }
        cypher = validator.sanitize(cypher);
        return new CypherResult(cypher, "llm");
    }

    private String extractCypher(String response) {
        if (response == null) return "";
        String trimmed = response.trim();
        if (trimmed.contains("```")) {
            int start = trimmed.indexOf("```");
            start = trimmed.indexOf("\n", start) + 1;
            if (start <= 0) start = trimmed.indexOf("```") + 3;
            if (trimmed.substring(trimmed.indexOf("```") + 3).trim().toLowerCase().startsWith("cypher")) {
                start = trimmed.indexOf("cypher", trimmed.indexOf("```")) + 6;
                while (start < trimmed.length() && Character.isWhitespace(trimmed.charAt(start))) start++;
            }
            int end = trimmed.indexOf("```", start);
            if (end > start) {
                return trimmed.substring(start, end).trim();
            }
            return trimmed.substring(start).trim();
        }
        return trimmed;
    }

    public record CypherResult(String cypher, String method) {}

    public static class InvalidCypherException extends RuntimeException {
        public InvalidCypherException(String message) {
            super(message);
        }
    }
}
