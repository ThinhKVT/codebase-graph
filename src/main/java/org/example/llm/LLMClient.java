package org.example.llm;

/**
 * Interface for LLM completion (e.g. Ollama) used for natural-language-to-Cypher.
 */
public interface LLMClient {

    /**
     * Complete with a single prompt.
     */
    String complete(String prompt);

    /**
     * Complete with system and user prompts.
     */
    String complete(String systemPrompt, String userPrompt);

    /**
     * Whether the client is available (e.g. Ollama reachable).
     */
    boolean isAvailable();
}
