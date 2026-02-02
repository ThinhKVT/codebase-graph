package org.example.vector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.config.QdrantConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Qdrant vector store implementation using REST API.
 */
public class QdrantVectorStore implements VectorStore {

    private static final Logger logger = LoggerFactory.getLogger(QdrantVectorStore.class);
    private static final int BATCH_SIZE = 100;

    private final QdrantConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private volatile boolean connected = false;

    public QdrantVectorStore(QdrantConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.objectMapper = new ObjectMapper();
    }

    public QdrantVectorStore() {
        this(QdrantConfig.fromEnv());
    }

    @Override
    public void connect() throws VectorStoreException {
        try {
            // Qdrant uses root endpoint for health check
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getHttpUrl() + "/"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                connected = true;
                logger.info("Connected to Qdrant at {}", config.getHttpUrl());
            } else {
                throw VectorStoreException.connectionFailed(config.getHttpUrl(), 
                    new RuntimeException("Health check failed: " + response.statusCode()));
            }
        } catch (IOException | InterruptedException e) {
            throw VectorStoreException.connectionFailed(config.getHttpUrl(), e);
        }
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void close() {
        connected = false;
    }

    @Override
    public void ensureCollection(String collectionName, int vectorSize) throws VectorStoreException {
        ensureConnected();
        
        // Check if collection exists
        if (collectionExists(collectionName)) {
            logger.info("Collection {} already exists", collectionName);
            return;
        }

        // Create collection
        try {
            Map<String, Object> body = Map.of(
                "vectors", Map.of(
                    "size", vectorSize,
                    "distance", "Cosine"
                )
            );

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getHttpUrl() + "/collections/" + collectionName))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                logger.info("Created collection: {} (vectorSize={})", collectionName, vectorSize);
            } else {
                throw VectorStoreException.operationFailed("createCollection", 
                    "HTTP " + response.statusCode() + ": " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            throw new VectorStoreException("Failed to create collection", e);
        }
    }

    @Override
    public void deleteCollection(String collectionName) throws VectorStoreException {
        ensureConnected();
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getHttpUrl() + "/collections/" + collectionName))
                .DELETE()
                .build();

            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                logger.info("Deleted collection: {}", collectionName);
            } else if (response.statusCode() != 404) {
                throw VectorStoreException.operationFailed("deleteCollection", 
                    "HTTP " + response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            throw new VectorStoreException("Failed to delete collection", e);
        }
    }

    @Override
    public void upsert(String collectionName, VectorPoint point) throws VectorStoreException {
        upsertBatch(collectionName, List.of(point));
    }

    @Override
    public void upsertBatch(String collectionName, List<VectorPoint> points) throws VectorStoreException {
        ensureConnected();
        
        if (points.isEmpty()) return;

        // Process in batches
        for (int i = 0; i < points.size(); i += BATCH_SIZE) {
            List<VectorPoint> batch = points.subList(i, Math.min(i + BATCH_SIZE, points.size()));
            upsertBatchInternal(collectionName, batch);
        }
    }

    private void upsertBatchInternal(String collectionName, List<VectorPoint> batch) 
            throws VectorStoreException {
        try {
            List<Map<String, Object>> pointsData = new ArrayList<>();
            for (VectorPoint point : batch) {
                Map<String, Object> pointData = new HashMap<>();
                pointData.put("id", point.getId());
                pointData.put("vector", toFloatList(point.getVector()));
                pointData.put("payload", point.getPayload());
                pointsData.add(pointData);
            }

            Map<String, Object> body = Map.of("points", pointsData);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getHttpUrl() + "/collections/" + collectionName + "/points?wait=true"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw VectorStoreException.operationFailed("upsert", 
                    "HTTP " + response.statusCode() + ": " + response.body());
            }
            
            logger.debug("Upserted {} points to {}", batch.size(), collectionName);
        } catch (IOException | InterruptedException e) {
            throw new VectorStoreException("Failed to upsert points", e);
        }
    }

    @Override
    public List<SearchResult> search(String collectionName, float[] queryVector, int limit) 
            throws VectorStoreException {
        return search(collectionName, queryVector, limit, null);
    }

    @Override
    public List<SearchResult> search(String collectionName, float[] queryVector, int limit, 
            Map<String, Object> filters) throws VectorStoreException {
        ensureConnected();

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("vector", toFloatList(queryVector));
            body.put("limit", limit);
            body.put("with_payload", true);

            if (filters != null && !filters.isEmpty()) {
                body.put("filter", buildFilter(filters));
            }

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getHttpUrl() + "/collections/" + collectionName + "/points/search"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw VectorStoreException.operationFailed("search", 
                    "HTTP " + response.statusCode() + ": " + response.body());
            }

            return parseSearchResults(response.body());
        } catch (IOException | InterruptedException e) {
            throw new VectorStoreException("Search failed", e);
        }
    }

    @Override
    public void delete(String collectionName, List<String> ids) throws VectorStoreException {
        ensureConnected();

        try {
            Map<String, Object> body = Map.of("points", ids);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getHttpUrl() + "/collections/" + collectionName + 
                    "/points/delete?wait=true"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw VectorStoreException.operationFailed("delete", 
                    "HTTP " + response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            throw new VectorStoreException("Delete failed", e);
        }
    }

    @Override
    public void deleteByFilter(String collectionName, Map<String, Object> filters) 
            throws VectorStoreException {
        ensureConnected();

        try {
            Map<String, Object> body = Map.of("filter", buildFilter(filters));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getHttpUrl() + "/collections/" + collectionName + 
                    "/points/delete?wait=true"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw VectorStoreException.operationFailed("deleteByFilter", 
                    "HTTP " + response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            throw new VectorStoreException("Delete by filter failed", e);
        }
    }

    @Override
    public Map<String, Object> getCollectionInfo(String collectionName) throws VectorStoreException {
        ensureConnected();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getHttpUrl() + "/collections/" + collectionName))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                return objectMapper.convertValue(root.get("result"), 
                    new TypeReference<Map<String, Object>>() {});
            } else if (response.statusCode() == 404) {
                throw VectorStoreException.collectionNotFound(collectionName);
            } else {
                throw VectorStoreException.operationFailed("getCollectionInfo", 
                    "HTTP " + response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            throw new VectorStoreException("Failed to get collection info", e);
        }
    }

    @Override
    public long count(String collectionName) throws VectorStoreException {
        Map<String, Object> info = getCollectionInfo(collectionName);
        Object pointsCount = info.get("points_count");
        if (pointsCount instanceof Number) {
            return ((Number) pointsCount).longValue();
        }
        return 0;
    }

    private boolean collectionExists(String collectionName) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getHttpUrl() + "/collections/" + collectionName))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());

            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private void ensureConnected() throws VectorStoreException {
        if (!connected) {
            throw VectorStoreException.notConnected();
        }
    }

    private List<Float> toFloatList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float f : array) {
            list.add(f);
        }
        return list;
    }

    private Map<String, Object> buildFilter(Map<String, Object> filters) {
        List<Map<String, Object>> mustConditions = new ArrayList<>();
        
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            mustConditions.add(Map.of(
                "key", entry.getKey(),
                "match", Map.of("value", entry.getValue())
            ));
        }
        
        return Map.of("must", mustConditions);
    }

    private List<SearchResult> parseSearchResults(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode resultNode = root.get("result");
        
        List<SearchResult> results = new ArrayList<>();
        if (resultNode != null && resultNode.isArray()) {
            for (JsonNode item : resultNode) {
                // Null check for required fields
                JsonNode idNode = item.get("id");
                JsonNode scoreNode = item.get("score");
                if (idNode == null || scoreNode == null) {
                    logger.warn("Skipping malformed search result: missing id or score");
                    continue;
                }
                
                String id = idNode.asText();
                float score = (float) scoreNode.asDouble();
                
                Map<String, Object> payload = new HashMap<>();
                JsonNode payloadNode = item.get("payload");
                if (payloadNode != null) {
                    payload = objectMapper.convertValue(payloadNode, 
                        new TypeReference<Map<String, Object>>() {});
                }
                
                results.add(new SearchResult(id, score, payload));
            }
        }
        
        return results;
    }
}
