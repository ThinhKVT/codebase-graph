# Benchmarking Guide

This document describes a simple approach to benchmark indexing throughput and query latency for the Codebase Knowledge Graph.

Goals
- Measure indexing throughput (documents/sec, symbols/sec).
- Measure query latency for common queries (symbol lookup, reference lookup, dependency traversal).

Test environment
- Use a dedicated machine or CI runner with consistent resources.
- Use Neo4j external to the test process to avoid JVM contention.

Indexing benchmark
1. Prepare a large sample repository (or synthetic dataset) with many source files.
2. Warm up the JVM and Neo4j caches by running a preliminary index pass.
3. Measure wall-clock time for indexing: start time before `IndexCommand` run, end time after completion.
4. Report metrics: documents indexed, symbols indexed, references indexed, duration, throughput.

Query benchmark
1. Ensure the graph is populated with a realistic dataset.
2. Run multiple iterations (e.g., 1000) of:
   - Symbol lookup by FQN
   - List references for popular symbols
   - Dependency tree traversal with depth 1-3
3. Use a concurrency parameter to simulate N parallel clients.
4. Report average/min/max latency, p95/p99 percentiles, and error rates.

Tooling and automation
- Use JMH for Java-based microbenchmarks where relevant.
- Use shell scripts to orchestrate end-to-end benchmarks and capture timings.
- Optionally use grafana/prometheus to capture system-level metrics (CPU, memory, I/O).

Interpretation
- Identify bottlenecks (Neo4j query slowness, batch insert overhead, network latency).
- Use Neo4j query profiling to optimize heavy Cypher queries.

Reproducibility
- Pin Neo4j version and machine profile when reporting numbers.
- Check in sample dataset generation scripts under `src/test/resources/benchmark/` if useful.

