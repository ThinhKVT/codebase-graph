package org.example.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * LLM client using Ollama /api/generate for text completion (e.g. Cypher generation).
 */
public class OllamaLLMClient implements LLMClient {

    private static final Logger logger = LoggerFactory.getLogger(OllamaLLMClient.class);

    private final String baseUrl;
    private final String model;
    private final int timeoutSeconds;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OllamaLLMClient(String baseUrl, String model, int timeoutSeconds) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.model = model;
        this.timeoutSeconds = Math.max(30, timeoutSeconds);
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.objectMapper = new ObjectMapper();
        logger.info("Ollama LLM client: {} model={}", this.baseUrl, this.model);
    }

    public OllamaLLMClient(String baseUrl, String model) {
        this(baseUrl, model, 60);
    }

    @Override
    public String complete(String prompt) {
        return complete("", prompt);
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        String fullPrompt = systemPrompt.isBlank()
            ? userPrompt
            : systemPrompt + "\n\n" + userPrompt;
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                "model", model,
                "prompt", fullPrompt,
                "stream", false
            ));
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/generate"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                logger.warn("Ollama generate failed: {} {}", response.statusCode(), response.body());
                return "";
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (root.has("response")) {
                return root.get("response").asText().trim();
            }
            return "";
        } catch (Exception e) {
            logger.error("Ollama generate error", e);
            return "";
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/tags"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
