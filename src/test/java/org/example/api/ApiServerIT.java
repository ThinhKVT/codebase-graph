package org.example.api;

import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import org.example.api.handlers.DependencyHandler;
import org.example.api.handlers.ReferenceHandler;
import org.example.api.handlers.SymbolHandler;
import org.example.graph.GraphStore;
import org.example.model.Reference;
import org.example.model.ReferenceKind;
import org.example.model.Symbol;
import org.example.model.SymbolKind;
import org.example.query.DependencyQuery;
import org.example.query.ReferenceQuery;
import org.example.query.SymbolQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ApiServerIT {

    private Javalin app;

    @AfterEach
    public void tearDown() {
        if (app != null) {
            app.stop();
            app = null;
        }
    }

    @Test
    public void testHealthAndStatsAndSymbolsEndpoints() throws Exception {
        // Mock GraphStore
        GraphStore graphStore = Mockito.mock(GraphStore.class);

        // Prepare a sample symbol
        Symbol symbol = new Symbol.Builder()
            .id("sym1")
            .name("MyClass")
            .fullyQualifiedName("com.example.MyClass")
            .kind(SymbolKind.CLASS)
            .filePath("/src/main/java/com/example/MyClass.java")
            .startLine(10)
            .build();

        // Stub graph store methods
        Mockito.when(graphStore.countSymbols()).thenReturn(1L);
        Mockito.when(graphStore.countReferences()).thenReturn(0L);
        Mockito.when(graphStore.findSymbolsByName("MyClass")).thenReturn(List.of(symbol));
        Mockito.when(graphStore.findSymbolByFQN("com.example.MyClass")).thenReturn(Optional.of(symbol));
        Mockito.when(graphStore.findReferencesToSymbol(Mockito.anyString())).thenReturn(List.of());

        // Create queries and handlers
        SymbolQuery symbolQuery = new SymbolQuery(graphStore);
        ReferenceQuery referenceQuery = new ReferenceQuery(graphStore, symbolQuery);
        DependencyQuery dependencyQuery = new DependencyQuery(graphStore, symbolQuery);

        SymbolHandler symbolHandler = new SymbolHandler(symbolQuery);
        ReferenceHandler referenceHandler = new ReferenceHandler(referenceQuery);
        DependencyHandler dependencyHandler = new DependencyHandler(dependencyQuery);

        // Build Javalin app similar to ApiServer
        app = Javalin.create(cfg -> {
            cfg.jsonMapper(new JavalinJackson());
            cfg.http.defaultContentType = "application/json";
        });

        // Register a health route and stats (as ApiServer would)
        app.get("/health", ctx -> ctx.json(Map.of("status", "ok", "message", "API server is running")));
        app.get("/stats", ctx -> {
            long symbolCount = graphStore.countSymbols();
            long referenceCount = graphStore.countReferences();
            ctx.json(Map.of("symbols", symbolCount, "references", referenceCount));
        });

        // Register handlers routes
        app.get("/symbols", symbolHandler::listSymbols);
        app.get("/symbols/{id}", symbolHandler::getSymbol);
        app.get("/symbols/{id}/references", referenceHandler::getReferences);
        app.get("/symbols/{id}/dependencies", dependencyHandler::getDependencies);
        app.get("/symbols/{id}/dependents", dependencyHandler::getDependents);

        // Start on ephemeral port
        app.start(0);
        int port = app.port();
        Assertions.assertTrue(port > 0, "App should start on an ephemeral port");

        HttpClient client = HttpClient.newHttpClient();

        // /health
        HttpRequest healthReq = HttpRequest.newBuilder()
            .uri(new URI("http://localhost:" + port + "/health"))
            .GET().build();
        HttpResponse<String> healthRes = client.send(healthReq, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, healthRes.statusCode());
        Assertions.assertTrue(healthRes.body().contains("\"status\":\"ok\""));

        // /stats
        HttpRequest statsReq = HttpRequest.newBuilder()
            .uri(new URI("http://localhost:" + port + "/stats"))
            .GET().build();
        HttpResponse<String> statsRes = client.send(statsReq, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, statsRes.statusCode());
        Assertions.assertTrue(statsRes.body().contains("\"symbols\":1"));

        // /symbols?name=MyClass
        HttpRequest symbolsReq = HttpRequest.newBuilder()
            .uri(new URI("http://localhost:" + port + "/symbols?name=MyClass"))
            .GET().build();
        HttpResponse<String> symbolsRes = client.send(symbolsReq, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, symbolsRes.statusCode());
        Assertions.assertTrue(symbolsRes.body().contains("MyClass"));

        // /symbols/com.example.MyClass
        HttpRequest symbolByFqnReq = HttpRequest.newBuilder()
            .uri(new URI("http://localhost:" + port + "/symbols/com.example.MyClass"))
            .GET().build();
        HttpResponse<String> symbolByFqnRes = client.send(symbolByFqnReq, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, symbolByFqnRes.statusCode());
        Assertions.assertTrue(symbolByFqnRes.body().contains("com.example.MyClass"));

        // /symbols/com.example.MyClass/references
        HttpRequest refsReq = HttpRequest.newBuilder()
            .uri(new URI("http://localhost:" + port + "/symbols/com.example.MyClass/references"))
            .GET().build();
        HttpResponse<String> refsRes = client.send(refsReq, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, refsRes.statusCode());
        Assertions.assertTrue(refsRes.body().contains("\"count\":0") || refsRes.body().contains("[]"));
    }
}

