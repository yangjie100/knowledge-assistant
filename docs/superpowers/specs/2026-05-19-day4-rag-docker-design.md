# Day 4: RAG Quality + Docker

Date: 2026-05-19 | Status: Approved

## Phase A: RAG Quality

### A1: Hybrid Retrieval (RRF Fusion)
- Vector search (VectorStore.similaritySearch, topK=5) + Redis FT.SEARCH (topK=5)
- RRF fusion: score = sum(1/(60 + rank_i))
- EmbeddingService stores chunk text in Redis Hash: chunk:text:{chunkId} -> {content, docId}
- App startup: check/create FT index on chunk:text:* prefix

### A2: Retrieval Cache
- Redis cache: cache:retrieval:{sha256(query)} -> JSON [{chunkId, score, docId}]
- TTL: 300s
- Invalidation: on document upload/delete, clear cache:retrieval:*

### A3: Chunk Config
- @ConfigurationProperties(prefix="ka.rag.chunk"): defaultSize=800, minSize=200, overlap=100
- DocumentSplitter uses config values

### A4: Source Attribution
- ChatService formats context with [Source: {title} (score: {score})]
- Frontend renders source list

## Phase B: Docker

### B1: Dockerfile (multi-stage, maven + jre-alpine)
### B2: docker-compose (redis + ollama + app)
### B3: Actuator + OllamaHealthIndicator
### B4: application-prod.yml
