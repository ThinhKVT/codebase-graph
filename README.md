# Codebase Knowledge Graph

A tool to index source repositories using SCIP, store symbols and references in Neo4j, and expose queries via a CLI and HTTP API.

Quickstart
----------
Requirements:
- Java 17
- Maven
- Docker (for running Neo4j via docker-compose)

1. Start Neo4j (development):

```bash
# from repo root
docker-compose up -d
```

2. Build the project:

```bash
mvn package
```

3. Index a repository (example):

```bash
# CLI entrypoint in the packaged jar
java -jar target/codebase-graph-1.0-SNAPSHOT.jar index /path/to/repo --name my-repo
```

4. Start the API server:

```bash
java -jar target/codebase-graph-1.0-SNAPSHOT.jar serve --port 8080
# API endpoints:
# GET /health
# GET /stats
# GET /symbols?name=Foo
# GET /symbols/{id}
# GET /symbols/{id}/references
# GET /symbols/{id}/dependencies?depth=N
```

Running tests
-------------

Run unit tests:

```bash
mvn test
```

Run a single test class:

```bash
mvn -Dtest=org.example.api.ApiServerIT test
```

Docker image
------------

A `Dockerfile` is provided to build a runnable image of the CLI tool (fat/uber-jar built by Maven Shade plugin).

```bash
# build jar and image
mvn package -DskipTests
docker build -t codebase-graph:latest .

# run (assumes Neo4j is reachable)
docker run --rm -e NEO4J_URI=bolt://host.docker.internal:7687 codebase-graph:latest serve --port 8080
```

Configuration
-------------
Configuration is read from `src/main/resources/application.properties` by default. You can override Neo4j connection settings via system properties or environment variables in the CLI (see `IndexCommand` and `ServeCommand` options).

Contributing
------------
See `CONTRIBUTING.md` for guidelines on developing and testing locally.

License
-------
(Choose an appropriate license)

