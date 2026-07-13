#!/usr/bin/env bash
#
# test-hf-inference.sh — test Hugging Face Serverless Inference API
# for Orbit's embedding and reranker models.
#
# Usage:
#   1. Get an HF token: https://huggingface.co/settings/tokens (Create new token → Read)
#   2. export HF_TOKEN=hf_xxxxxxxxxxxxxxxxxxxx
#   3. bash docs/devops/test-hf-inference.sh
#
# This script tests:
#   - Embedding: intfloat/multilingual-e5-large (feature-extraction, 1024 dims)
#   - Reranker:  BAAI/bge-reranker-v2-m3 (text-classification, cross-encoder)
#
# Both models are confirmed "warm" on HF Inference as of July 2025.

set -euo pipefail

# ─── Config ───────────────────────────────────────────────────────────────────

EMBED_MODEL="intfloat/multilingual-e5-large"
RERANKER_MODEL="BAAI/bge-reranker-v2-m3"
HF_BASE="https://router.huggingface.co/hf-inference/models"

if [ -z "${HF_TOKEN:-}" ]; then
  echo "❌ HF_TOKEN not set. Get one at https://huggingface.co/settings/tokens"
  echo "   export HF_TOKEN=hf_xxxxxxxxxxxxxxxxxxxx"
  exit 1
fi

echo "🔑 Using HF token: ${HF_TOKEN:0:8}..."
echo ""

# ─── 1. Embedding — single query ─────────────────────────────────────────────
# This is what SearchService does: embed the translated/condensed search query.
# The "query: " prefix is required by E5 models for asymmetric retrieval.

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "1. EMBEDDING — query embedding (what SearchService calls per search)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

QUERY_TEXT="React developer for SaaS product"

echo "POST $HF_BASE/$EMBED_MODEL"
echo "Body: {\"inputs\": \"query: $QUERY_TEXT\"}"
echo ""

EMBED_RESPONSE=$(curl -s \
  -X POST "$HF_BASE/$EMBED_MODEL" \
  -H "Authorization: Bearer $HF_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"inputs\": \"query: $QUERY_TEXT\"}")

echo "Response (first 200 chars):"
echo "$EMBED_RESPONSE" | head -c 200
echo ""
echo ""

# Parse dimensions
if command -v python3 &>/dev/null; then
  DIMS=$(echo "$EMBED_RESPONSE" | python3 -c "import sys,json; v=json.load(sys.stdin); print(len(v[0]) if isinstance(v, list) and len(v)>0 and isinstance(v[0], list) else len(v))" 2>/dev/null || echo "parse error")
  echo "✅ Embedding dimensions: $DIMS"
  FIRST5=$(echo "$EMBED_RESPONSE" | python3 -c "import sys,json; v=json.load(sys.stdin); vec=v[0] if isinstance(v[0],list) else v; print([round(x,6) for x in vec[:5]])" 2>/dev/null || echo "parse error")
  echo "   First 5 values: $FIRST5"
else
  echo "   (install python3 to parse dimensions)"
fi

echo ""

# ─── 2. Embedding — passage embedding (what IngestionOrchestrator calls per chunk) ──

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "2. EMBEDDING — passage embedding (what IngestionOrchestrator calls per chunk)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

PASSAGE_TEXT="Experienced React developer specializing in building scalable SaaS web applications using TypeScript, React, and modern frontend tooling."

echo "POST $HF_BASE/$EMBED_MODEL"
echo "Body: {\"inputs\": \"passage: $PASSAGE_TEXT\"}"
echo ""

PASSAGE_RESPONSE=$(curl -s \
  -X POST "$HF_BASE/$EMBED_MODEL" \
  -H "Authorization: Bearer $HF_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"inputs\": \"passage: $PASSAGE_TEXT\"}")

echo "Response (first 200 chars):"
echo "$PASSAGE_RESPONSE" | head -c 200
echo ""
echo ""

if command -v python3 &>/dev/null; then
  PDIMS=$(echo "$PASSAGE_RESPONSE" | python3 -c "import sys,json; v=json.load(sys.stdin); print(len(v[0]) if isinstance(v, list) and len(v)>0 and isinstance(v[0], list) else len(v))" 2>/dev/null || echo "parse error")
  echo "✅ Passage embedding dimensions: $PDIMS"
fi

echo ""

# ─── 3. Reranker — single pair ──────────────────────────────────────────────
# This is what CrossEncoderReranker calls: score a (query, document) pair.
# The HF text-classification API accepts text + text_pair.

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "3. RERANKER — single pair (query vs one document)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

RERANK_QUERY="React developer for SaaS product"
RERANK_DOC_GOOD="Frontend engineer specializing in React, TypeScript, and building scalable SaaS web applications"
RERANK_DOC_BAD="Java backend developer with Spring Boot and PostgreSQL experience"

echo "POST $HF_BASE/$RERANKER_MODEL"
echo "Body: {\"inputs\": {\"text\": \"$RERANK_QUERY\", \"text_pair\": \"$RERANK_DOC_GOOD\"}}"
echo ""

RERANK_SINGLE=$(curl -s \
  -X POST "$HF_BASE/$RERANKER_MODEL" \
  -H "Authorization: Bearer $HF_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"inputs\": {\"text\": \"$RERANK_QUERY\", \"text_pair\": \"$RERANK_DOC_GOOD\"}}")

echo "Response:"
echo "$RERANK_SINGLE"
echo ""

echo "Now with a BAD match (Java backend vs React query):"
echo ""

RERANK_BAD=$(curl -s \
  -X POST "$HF_BASE/$RERANKER_MODEL" \
  -H "Authorization: Bearer $HF_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"inputs\": {\"text\": \"$RERANK_QUERY\", \"text_pair\": \"$RERANK_DOC_BAD\"}}")

echo "Response:"
echo "$RERANK_BAD"
echo ""

# ─── 4. Reranker — batch (multiple documents against one query) ─────────────
# This is what CrossEncoderReranker actually does: send all candidates at once.
# HF text-classification may or may not support batch text_pair — test it.

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "4. RERANKER — batch (query vs multiple documents — what CrossEncoderReranker does)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

echo "POST $HF_BASE/$RERANKER_MODEL"
echo "Body: {\"inputs\": {\"text\": query, \"text_pair\": [doc1, doc2, doc3]}}"
echo ""

RERANK_BATCH=$(curl -s \
  -X POST "$HF_BASE/$RERANKER_MODEL" \
  -H "Authorization: Bearer $HF_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "inputs": {
      "text": "React developer for SaaS product",
      "text_pair": [
        "Frontend engineer specializing in React, TypeScript, and building scalable SaaS web applications",
        "Java backend developer with Spring Boot and PostgreSQL experience",
        "Full-stack developer experienced in React, Node.js, and SaaS platform development"
      ]
    }
  }')

echo "Response:"
echo "$RERANK_BATCH"
echo ""

# ─── 5. If batch doesn't work, try sequential single pairs ──────────────────

if echo "$RERANK_BATCH" | grep -qi "error\|Error\|422\|400"; then
  echo "⚠️  Batch text_pair not supported. Falling back to sequential calls:"
  echo ""

  DOCS=(
    "Frontend engineer specializing in React, TypeScript, and building scalable SaaS web applications"
    "Java backend developer with Spring Boot and PostgreSQL experience"
    "Full-stack developer experienced in React, Node.js, and SaaS platform development"
  )

  for i in "${!DOCS[@]}"; do
    SCORE=$(curl -s \
      -X POST "$HF_BASE/$RERANKER_MODEL" \
      -H "Authorization: Bearer $HF_TOKEN" \
      -H "Content-Type: application/json" \
      -d "{\"inputs\": {\"text\": \"React developer for SaaS product\", \"text_pair\": \"${DOCS[$i]}\"}}")
    echo "  Doc $i score: $SCORE"
  done
fi

echo ""

# ─── 6. Cosine similarity between query and passage embeddings ─────────────
# Verify that the good match has higher similarity than a bad match.

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "5. VERIFY — cosine similarity (query vs good doc, query vs bad doc)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

BAD_PASSAGE="Java backend developer with Spring Boot, PostgreSQL, and microservices architecture"

BAD_RESPONSE=$(curl -s \
  -X POST "$HF_BASE/$EMBED_MODEL" \
  -H "Authorization: Bearer $HF_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"inputs\": \"passage: $BAD_PASSAGE\"}")

if command -v python3 &>/dev/null; then
  python3 -c "
import json, sys, math

q = json.loads('''$EMBED_RESPONSE''')
good = json.loads('''$PASSAGE_RESPONSE''')
bad = json.loads('''$BAD_RESPONSE''')

# Flatten if nested
q = q[0] if isinstance(q[0], list) else q
good = good[0] if isinstance(good[0], list) else good
bad = bad[0] if isinstance(bad[0], list) else bad

def cosine(a, b):
    dot = sum(x*y for x,y in zip(a,b))
    na = math.sqrt(sum(x*x for x in a))
    nb = math.sqrt(sum(y*y for y in b))
    return dot / (na * nb)

sim_good = cosine(q, good)
sim_bad = cosine(q, bad)

print(f'Query:        \"React developer for SaaS product\"')
print(f'Good passage: \"Experienced React developer specializing in SaaS...\"')
print(f'Bad passage:  \"Java backend developer with Spring Boot...\"')
print()
print(f'Cosine sim (query vs good): {sim_good:.4f}')
print(f'Cosine sim (query vs bad):  {sim_bad:.4f}')
print()
if sim_good > sim_bad:
    print(f'✅ Good match ({sim_good:.4f}) > Bad match ({sim_bad:.4f}) — embeddings work correctly!')
else:
    print(f'❌ Something is wrong — bad match scored higher')
" 2>&1
else
  echo "(install python3 to compute cosine similarity)"
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Done. If all checks passed, the HF Inference API works for both"
echo "models. Next step: implement HfEmbeddingService in the backend."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"