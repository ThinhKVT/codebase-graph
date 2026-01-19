# Error Messages and Troubleshooting

This document lists common error messages produced by the CLI and HTTP API and suggests actions to resolve them.

Common CLI errors

- "Error: Repository path does not exist"
  - Cause: The path passed to `index` was invalid or missing.
  - Action: Verify the path exists and is a directory.

- "scip tool is not installed"
  - Cause: The required language-specific scip CLI (e.g., `scip-java`) is not on PATH.
  - Action: Install the appropriate scip tool for your language (see README), or use `--scip-file` to supply an existing .scip file.

- "SCIP index file not found at: <path>"
  - Cause: The scip runner did not generate the expected .scip output.
  - Action: Check runner logs and ensure the tool had permission to write to the output path.

- "Database Error: <message>"
  - Cause: Failure to connect to Neo4j or to execute Cypher statements.
  - Action: Ensure Neo4j is running and `application.properties` contains correct connection info; verify credentials and network access.

API errors

- 400 Bad Request: Invalid query parameter
  - Cause: Client passed an unsupported enum or malformed parameter.
  - Action: Validate query params against API docs (e.g., valid `kind` values).

- 404 Not Found: Symbol not found
  - Cause: The requested symbol FQN/ID does not exist.
  - Action: Confirm the symbol exists (use CLI `query symbols`), or return meaningful fallback to user.

- 500 Internal Server Error: Unexpected exception
  - Cause: Unhandled exception in a handler or downstream component.
  - Action: Check server logs; stack traces are logged to `logs/codebase-graph.log`.

Tips

- Enable verbose output in CLI with `-v` to see more details.
- For integration tests using Testcontainers, ensure Docker is available and you have sufficient resources.
- If Neo4j queries are slow, enable query logging and use `EXPLAIN`/`PROFILE` to optimize Cypher.

