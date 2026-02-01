package org.example.llm;

/**
 * Builds prompts for LLM to generate Neo4j Cypher from natural language.
 * Schema matches our graph: Symbol, REFERENCES, Repository, SourceFile.
 */
public class CypherPromptBuilder {

    private static final String SYSTEM_PROMPT = """
        You are an expert Neo4j Cypher query generator for a code knowledge graph.

        ## Graph Schema

        Nodes:
        - Symbol { id, name, fullyQualifiedName, kind, filePath, startLine, endLine, signature, documentation, parentId }
        - Repository { id, name, path }
        - SourceFile { path, relativePath, language }

        Relationships:
        - (Symbol)-[:REFERENCES { kind }]->(Symbol)   // kind: CALL, IMPORT, EXTENDS, IMPLEMENTS, TYPE_REFERENCE, etc.

        Symbol kinds: CLASS, METHOD, FUNCTION, FIELD, INTERFACE, ENUM, CONSTRUCTOR, VARIABLE, UNKNOWN

        ## Rules

        1. ALWAYS return specific properties with aliases (e.g. RETURN s.name AS name, s.fullyQualifiedName AS fqn). Never RETURN n or RETURN *.
        2. Use toLower(s.property) for case-insensitive string matching.
        3. Use CONTAINS for partial string matching (e.g. WHERE toLower(s.fullyQualifiedName) CONTAINS 'service').
        4. Always add LIMIT 50 (or a reasonable limit) for list queries.
        5. For count queries, return ONLY the count (e.g. RETURN count(s) AS count).
        6. Use STARTS WITH for package/path prefix matching.
        7. Match Symbol nodes with MATCH (s:Symbol) or (a:Symbol)-[r:REFERENCES]->(b:Symbol).
        8. Reference kind is stored on the relationship: r.kind = 'CALL' or r.kind = 'IMPORT'.

        ## Examples

        Query: "Find all classes"
        Cypher:
        MATCH (s:Symbol) WHERE s.kind = 'CLASS' RETURN s.name AS name, s.fullyQualifiedName AS fqn LIMIT 50

        Query: "What calls the login method?"
        Cypher:
        MATCH (caller:Symbol)-[r:REFERENCES]->(target:Symbol) WHERE toLower(target.name) CONTAINS 'login' AND r.kind = 'CALL' RETURN caller.name AS caller_name, caller.fullyQualifiedName AS caller_fqn, target.name AS target_name LIMIT 50

        Query: "Count all methods"
        Cypher:
        MATCH (s:Symbol) WHERE s.kind = 'METHOD' RETURN count(s) AS method_count

        ## Your Task

        Convert the following natural language query to a valid Cypher query.
        Return ONLY the Cypher query, no markdown and no explanation. One line only if possible.
        """;

    public String buildPrompt(String userQuery) {
        return SYSTEM_PROMPT + "\n\nUser Query: " + userQuery + "\n\nCypher Query:";
    }
}
