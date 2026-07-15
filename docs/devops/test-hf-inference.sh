#!/usr/bin/env bash
#
# test-hf-inference.sh — test Hugging Face Serverless Inference API
# for Orbit's embedding and reranker models.
#
# Usage:
#   1. Get an HF token: https://huggingface.co/settings/tokens (Create → Read access)
#   2. export HF_TOKEN=hf_xxxxxxxxxxxxxxxxxxxx
#   3. bash docs/devops/test-hf-inference.sh
#
# Confirmed working formats (tested July 2025):
#   Embedding:  {"inputs": "query: text"}              → [1024 floats]
#   Reranker:   {"inputs": [{"text": q, "text_pair": d}]}  → [[{"label":"LABEL_0","score": 0.57}]]

set -euo pipefail

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

# ─── 1. Embedding — query ────────────────────────────────────────────────────

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "1. EMBEDDING — query embedding"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

EMBED_RESPONSE=$(curl -s \
  -X POST "$HF_BASE/$EMBED_MODEL" \
  -H "Authorization: Bearer $HF_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"inputs": "query: React developer for SaaS product"}')

echo "Response (first 200 chars):"
echo "$EMBED_RESPONSE" | head -c 200
echo ""

if command -v python3 &>/dev/null; then
  DIMS=$(echo "$EMBED_RESPONSE" | python3 -c "import sys,json; v=json.load(sys.stdin); print(len(v[0]) if isinstance(v,list) and len(v)>0 and isinstance(v[0],list) else len(v))" 2>/dev/null || echo "parse error")
  echo "✅ Dimensions: $DIMS"
fi
echo ""

# ─── 2. Embedding — passage ──────────────────────────────────────────────────

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "2. EMBEDDING — passage embedding"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

PASSAGE_RESPONSE=$(curl -s \
  -X POST "$HF_BASE/$EMBED_MODEL" \
  -H "Authorization: Bearer $HF_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"inputs": "passage: Experienced React developer specializing in building scalable SaaS web applications using TypeScript, React, and modern frontend tooling."}')

echo "Response (first 200 chars):"
echo "$PASSAGE_RESPONSE" | head -c 200
echo ""
echo ""

# ─── 3. Reranker — single pair (Format C: the one that works) ────────────────

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "3. RERANKER — single pair (good match)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo 'Body: {"inputs": [{"text": "React developer...", "text_pair": "Frontend engineer..."}]}'
echo ""

RERANK_GOOD=$(curl -s \
  -X POST "$HF_BASE/$RERANKER_MODEL" \
  -H "Authorization: Bearer $HF_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"inputs": [{"text": "React developer for SaaS product", "text_pair": "Frontend engineer specializing in React, TypeScript, and building scalable SaaS web applications"}]}')

echo "Response: $RERANK_GOOD"
echo ""

echo "--- Now with a BAD match (Java backend vs React query): ---"
echo ""

RERANK_BAD=$(curl -s \
  -X POST "$HF_BASE/$RERANKER_MODEL" \
  -H "Authorization: Bearer $HF_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"inputs": [{"text": "React developer for SaaS product", "text_pair": "Java backend developer with Spring Boot and PostgreSQL experience"}]}')

echo "Response: $RERANK_BAD"
echo ""

# ─── 4. Reranker — batch (all candidates at once, list of dicts) ─────────────

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "4. RERANKER — batch (3 pairs at once — what CrossEncoderReranker does)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo 'Body: {"inputs": [{"text": q, "text_pair": d1}, {"text": q, "text_pair": d2}, ...]}'
echo ""

# Use python3 to avoid shell JSON escaping issues
python3 << 'PYEOF'
import json, os, urllib.request

HF_TOKEN = os.environ["HF_TOKEN"]
url = "https://router.huggingface.co/hf-inference/models/BAAI/bge-reranker-v2-m3"

query = "React developer for SaaS product"
docs = [
    "Frontend engineer specializing in React, TypeScript, and building scalable SaaS web applications",
    "Java backend developer with Spring Boot and PostgreSQL experience",
    "Full-stack developer experienced in React, Node.js, and SaaS platform development",
]

payload = {
    "inputs": [
        {"text": query, "text_pair": doc}
        for doc in docs
    ]
}

print(f"Sending {len(docs)} pairs in one batch request...")
print()

data = json.dumps(payload).encode("utf-8")
req = urllib.request.Request(url, data=data, headers={
    "Authorization": f"Bearer {HF_TOKEN}",
    "Content-Type": "application/json",
})

try:
    with urllib.request.urlopen(req, timeout=30) as resp:
        result = json.loads(resp.read().decode())

    print("Response:")
    print(json.dumps(result, indent=2))
    print()

    print("Scores:")
    labels = ["GOOD", "BAD", "MID"]
    scores = []
    for i, item in enumerate(result):
        score = item[0]["score"] if isinstance(item, list) else item["score"]
        scores.append(score)
        print(f"  Doc {i} ({labels[i]}): {score:.4f}")
    print()

    if scores[0] > scores[1]:
        print(f"✅ GOOD ({scores[0]:.4f}) > BAD ({scores[1]:.4f}) — reranker works!")
    else:
        print(f"❌ BAD scored higher than GOOD")

except Exception as e:
    print(f"❌ Error: {e}")
PYEOF

echo ""

# ─── 5. Cosine similarity verification ───────────────────────────────────────

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "5. VERIFY — cosine similarity (embedding quality check)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

BAD_RESPONSE=$(curl -s \
  -X POST "$HF_BASE/$EMBED_MODEL" \
  -H "Authorization: Bearer $HF_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"inputs": "passage: Java backend developer with Spring Boot, PostgreSQL, and microservices architecture"}')

if command -v python3 &>/dev/null; then
  python3 -c "
import json, math

q = json.loads('''$EMBED_RESPONSE''')
good = json.loads('''$PASSAGE_RESPONSE''')
bad = json.loads('''$BAD_RESPONSE''')

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

print(f'Cosine sim (query vs good): {sim_good:.4f}')
print(f'Cosine sim (query vs bad):  {sim_bad:.4f}')
print()
if sim_good > sim_bad:
    print(f'✅ Good match ({sim_good:.4f}) > Bad match ({sim_bad:.4f})')
else:
    print(f'❌ Bad match scored higher')
" 2>&1
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "SUMMARY"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Embedding API:  POST .../models/intfloat/multilingual-e5-large"
echo "  Request:  {\"inputs\": \"query: text\"} or \"passage: text\""
echo "  Response: [0.02, -0.01, ... 1024 floats]"
echo ""
echo "Reranker API:   POST .../models/BAAI/bge-reranker-v2-m3"
echo "  Request:  {\"inputs\": [{\"text\": query, \"text_pair\": doc}, ...]}"
echo "  Response: [[{\"label\": \"LABEL_0\", \"score\": 0.57}], ...]"
echo "  Note:     Score is already sigmoid-normalized [0,1]"
echo "            No need to apply sigmoid in Java (unlike your Python server)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"