# Contributing to Codebase Knowledge Graph

Thanks for your interest in contributing! This document outlines how to get your development environment set up, how to run tests, and the preferred workflow for changes.

Development setup
-----------------
Prerequisites:
- Java 17
- Maven
- Docker (for running Neo4j during development)

Quick start:

1. Build the project:

```bash
mvn package
```

2. Run tests:

```bash
mvn test
```

3. Start a development Neo4j

```bash
docker-compose up -d
```

Branching & PRs
----------------
- Create a topic branch from `main` named `feat/<short-description>` or `fix/<short-description>`.
- Write tests for new features or bug fixes.
- Ensure `mvn test` passes locally before opening a PR.
- PRs should include a short description, the rationale for the change, and any migration notes.

Code style and formatting
-------------------------
- Follow existing Java coding conventions used in the repository.
- Keep public APIs backwards compatible where possible.
- Add or update unit/integration tests for behavior changes.

Testing guidance
-----------------
- Unit tests should not require external services.
- Use Testcontainers for integration tests that need Neo4j.
- For API-level tests, prefer starting an in-memory Javalin instance and mocking the `GraphStore` where appropriate.

Running the CLI locally
-----------------------
After packaging, run the CLI via the shaded jar produced by Maven:

```bash
java -jar target/codebase-graph-1.0-SNAPSHOT.jar index /path/to/repo
java -jar target/codebase-graph-1.0-SNAPSHOT.jar serve --port 8080
```

Reporting issues
----------------
- Open an issue for bugs or feature requests. Provide a minimal reproduction where possible.

License
-------
By contributing you agree that your contributions will be licensed under the project's license.

