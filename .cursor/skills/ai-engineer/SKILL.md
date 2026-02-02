---
name: ai-engineer
description: Guides on ML/AI engineering: model training, MLOps, LLMs, embeddings, and production ML systems. Use when building ML pipelines, deploying models, working with LLMs/embeddings, or when the user asks about AI/ML engineering.
---

# AI Engineer

## Principles

1. **Problem first** - Define success metrics and baseline before model
2. **Data quality** - Garbage in, garbage out; invest in data
3. **Reproducibility** - Code, data versioning, seeds, env
4. **Production from day one** - Monitoring, fallbacks, latency

## ML Pipeline

1. **Data** - Collect, clean, label, version (e.g. DVC, Delta Lake)
2. **Feature store** - Reuse features train/serve; avoid skew
3. **Train** - Experiment tracking (MLflow, Weights & Biases); hyperparameter tuning
4. **Evaluate** - Holdout test set; metrics aligned to business (precision, recall, AUC, etc.)
5. **Deploy** - Model registry, versioning, A/B tests
6. **Monitor** - Drift, performance decay, latency, errors

## Model Selection

| Problem type | Examples | Typical choices |
|-------------|----------|-----------------|
| Classification | Fraud, churn | Logistic regression, tree ensembles, neural nets |
| Regression | Demand, pricing | Linear, tree ensembles, neural nets |
| Ranking | Search, recommendations | LambdaMART, two-tower, listwise |
| Generation | Text, image | Transformers, diffusion |
| Embeddings | Search, similarity | Sentence-BERT, OpenAI embeddings |

## LLMs & Embeddings

### Embeddings
- Use for search, similarity, clustering, dedup
- Normalize vectors for cosine similarity
- Choose dimension and model by latency/cost/quality
- Cache embeddings when inputs repeat

### LLM Integration
- **Prompting** - Clear instructions, few-shot, structure output (JSON)
- **RAG** - Retrieve relevant chunks; cite sources; reduce hallucination
- **Fine-tuning** - When prompt engineering insufficient; need labeled data
- **Cost/latency** - Caching, smaller models, batching, streaming

### Production LLM
- Rate limits, retries, fallbacks
- Log prompts/completions (PII redaction)
- Guardrails: content filter, output schema, fact-check
- Evaluate with rubric or model-based eval

## MLOps

- **Versioning** - Code (git), data (DVC/snapshots), model (registry)
- **CI/CD** - Train on trigger/schedule; run tests and eval; deploy on metric gate
- **Serving** - REST/gRPC; batch vs real-time; GPU utilization
- **Monitoring** - Input distribution, prediction distribution, latency, errors, business metrics

## Data & Labeling

- **Bias** - Check train data represent target population; measure fairness
- **Label quality** - Inter-annotator agreement; review edge cases
- **Synthetic** - When real data scarce; validate on real data

## Security & Ethics

- Don’t log raw PII in prompts/responses without need and controls
- Sanitize inputs; validate outputs
- Document intended use and limitations
- Comply with regulations (e.g. GDPR, sector-specific)
