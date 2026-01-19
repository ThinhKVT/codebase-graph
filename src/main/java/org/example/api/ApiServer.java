package org.example.api;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.json.JavalinJackson;
import org.example.api.handlers.DependencyHandler;
import org.example.api.handlers.ReferenceHandler;
import org.example.api.handlers.SymbolHandler;
import org.example.graph.GraphStore;
import org.example.query.DependencyQuery;
import org.example.query.ReferenceQuery;
import org.example.query.SymbolQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP API server exposing graph queries via REST endpoints.
 */
public class ApiServer {

    private static final Logger logger = LoggerFactory.getLogger(ApiServer.class);

    private final GraphStore graphStore;
    private final int port;
    private Javalin app;

    public ApiServer(GraphStore graphStore, int port) {
        this.graphStore = graphStore;
        this.port = port;
    }

    /**
     * Start the API server.
     */
    public void start() {
        logger.info("Starting API server on port {}", port);

        // Initialize query services
        SymbolQuery symbolQuery = new SymbolQuery(graphStore);
        ReferenceQuery referenceQuery = new ReferenceQuery(graphStore, symbolQuery);
        DependencyQuery dependencyQuery = new DependencyQuery(graphStore, symbolQuery);

        // Initialize handlers
        SymbolHandler symbolHandler = new SymbolHandler(symbolQuery);
        ReferenceHandler referenceHandler = new ReferenceHandler(referenceQuery);
        DependencyHandler dependencyHandler = new DependencyHandler(dependencyQuery);

        // Create and configure Javalin app
        app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson());
            config.http.defaultContentType = "application/json";
        });

        // Register error handlers
        registerErrorHandlers();

        // Register routes
        registerRoutes(symbolHandler, referenceHandler, dependencyHandler);

        // Start server
        app.start(port);
        logger.info("API server started on http://localhost:{}", port);
    }

    /**
     * Stop the API server.
     */
    public void stop() {
        if (app != null) {
            logger.info("Stopping API server");
            app.stop();
            app = null;
        }
    }

    /**
     * Check if the server is running.
     */
    public boolean isRunning() {
        return app != null;
    }

    /**
     * Get the port the server is running on.
     */
    public int getPort() {
        return port;
    }

    private void registerErrorHandlers() {
        app.exception(IllegalArgumentException.class, (e, ctx) -> {
            logger.warn("Bad request: {}", e.getMessage());
            ctx.status(400).json(new ErrorResponse("Bad Request", e.getMessage()));
        });

        app.exception(NotFoundException.class, (e, ctx) -> {
            logger.warn("Not found: {}", e.getMessage());
            ctx.status(404).json(new ErrorResponse("Not Found", e.getMessage()));
        });

        app.exception(Exception.class, (e, ctx) -> {
            logger.error("Internal server error", e);
            ctx.status(500).json(new ErrorResponse("Internal Server Error", e.getMessage()));
        });
    }

    private void registerRoutes(SymbolHandler symbolHandler,
                                ReferenceHandler referenceHandler,
                                DependencyHandler dependencyHandler) {
        // Health check
        app.get("/health", ctx -> ctx.json(new HealthResponse("ok", "API server is running")));

        // Symbol endpoints
        app.get("/symbols", symbolHandler::listSymbols);
        app.get("/symbols/{id}", symbolHandler::getSymbol);

        // Reference endpoints
        app.get("/symbols/{id}/references", referenceHandler::getReferences);

        // Dependency endpoints
        app.get("/symbols/{id}/dependencies", dependencyHandler::getDependencies);
        app.get("/symbols/{id}/dependents", dependencyHandler::getDependents);

        // Statistics endpoint
        app.get("/stats", this::getStats);

        logger.info("Registered API routes");
    }

    private void getStats(Context ctx) {
        long symbolCount = graphStore.countSymbols();
        long referenceCount = graphStore.countReferences();
        ctx.json(new StatsResponse(symbolCount, referenceCount));
    }

    // Response records
    public record ErrorResponse(String error, String message) {}
    public record HealthResponse(String status, String message) {}
    public record StatsResponse(long symbols, long references) {}

    /**
     * Exception for resource not found.
     */
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }
}

