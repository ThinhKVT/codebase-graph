---
name: data-engineer
description: Guides on data engineering: ETL/ELT, pipelines, data warehousing, batch/streaming, and data quality. Use when building data pipelines, designing warehouses, or when the user asks about data engineering.
---

# Data Engineer

## Principles

1. **Data as product** - Reliable, documented, discoverable
2. **Idempotency** - Re-run safely; use keys and upserts
3. **Incremental when possible** - Reduce cost and latency
4. **Observability** - Lineage, freshness, quality checks

## Pipeline Design

### Batch
- **Schedule** - Cron, workflow (Airflow, Dagster); align to SLA
- **Incremental** - Watermarks, change data capture (CDC), append-only + dedup
- **Partitioning** - By date/entity for efficient scan and prune
- **Idempotency** - Partition overwrite or merge keys

### Streaming
- **Source** - Kafka, Kinesis, Pub/Sub; consumer groups
- **Processing** - Windowing (tumbling, sliding, session); exactly-once semantics where needed
- **Sink** - DB, warehouse, another stream; backpressure handling
- **Checkpointing** - Offsets/state for replay and recovery

## ETL / ELT

- **ETL** - Transform before load; use when target is strict or compute is cheap upstream
- **ELT** - Load raw, transform in warehouse; use when warehouse is powerful and schema flexible
- **Staging** - Raw layer (immutable), then cleaned, then curated/mart
- **Orchestration** - DAGs, dependencies, retries, alerts

## Data Warehouse

### Layers
- **Raw** - Copy of source; partitioned by ingestion time
- **Staging/Cleaned** - Typed, validated, deduped
- **Curated/Mart** - Business logic, dimensions, facts
- **Semantic** - Views or marts for BI and analytics

### Modeling
- **Star schema** - Facts + dimensions; simple for BI
- **Snowflake** - Normalized dimensions; less redundancy
- **Data Vault** - Hubs, links, satellites; audit and historization
- **One Big Table (OBT)** - Denormalized for specific query patterns

### Key Concepts
- **Surrogate keys** - Stable IDs across systems
- **Slowly changing dimensions (SCD)** - Type 1 (overwrite), Type 2 (history), Type 3 (previous)
- **Facts** - Grain explicit; additive measures
- **Partitioning & clustering** - By date, key columns for pruning and cost

## Data Quality

- **Freshness** - Data expected within SLA; alert on delay
- **Volume** - Anomaly on sudden drop/spike
- **Schema** - Validate types and nullability
- **Uniqueness** - Keys, dedup checks
- **Referential** - FK consistency across layers
- **Plausibility** - Ranges, distributions (e.g. dbt tests, Great Expectations)

## Tech Stack (Examples)

- **Orchestration** - Airflow, Dagster, Prefect
- **Processing** - Spark, Flink, dbt, SQL in warehouse
- **Warehouse** - Snowflake, BigQuery, Redshift, Databricks
- **Streaming** - Kafka, Kafka Connect, Flink, Kafka Streams
- **Format** - Parquet/ORC for lake; Delta/Iceberg for ACID

## Best Practices

- **Lineage** - Track source → transform → target (e.g. OpenLineage)
- **Documentation** - Column definitions, business rules, owners
- **Backfill** - Support re-run by date range without full scan when possible
- **Secrets** - No credentials in code; use secret manager
- **Cost** - Partition pruning, avoid SELECT * in large tables, use incremental models
