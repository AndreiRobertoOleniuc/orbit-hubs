"""
Orbit Reranker Microservice
============================
A lightweight FastAPI service that wraps BAAI/bge-reranker-v2-m3 as a
cross-encoder reranker.  The Orbit backend (Java/Spring) calls POST /rerank
with a query + list of candidate documents; the service returns relevance
scores in [0, 1] (sigmoid-normalised).

Industry-standard 2-stage retrieval:
  Stage 1 — Bi-encoder retrieval (E5 + BM25/FTS + RRF)  [in the Java backend]
  Stage 2 — Cross-encoder reranking (this service)     [deterministic, ~50 ms]

Run:
  pip install sentence-transformers fastapi uvicorn
  python server.py            # -> http://localhost:8001
  python server.py --port 8001 --model BAAI/bge-reranker-v2-m3
"""

from __future__ import annotations

import argparse
import math
import logging
from typing import List

from fastapi import FastAPI
from pydantic import BaseModel

logging.basicConfig(level=logging.INFO, format="%(asctime)s  %(levelname)-7s %(message)s")
log = logging.getLogger("reranker")

# -- Models -------------------------------------------------------------------

class RerankRequest(BaseModel):
    query: str
    documents: List[str]
    top_n: int | None = None


class RerankResult(BaseModel):
    index: int
    score: float


class RerankResponse(BaseModel):
    results: List[RerankResult]


# -- App ----------------------------------------------------------------------

app = FastAPI(title="Orbit Reranker", version="1.0")
_model = None  # set on startup (pre-loaded)


def get_model():
    assert _model is not None, "Model not loaded"
    return _model


@app.post("/rerank", response_model=RerankResponse)
def rerank(req: RerankRequest):
    if not req.documents:
        return RerankResponse(results=[])

    model = get_model()

    # Cross-encoder scores each (query, document) pair -> raw logit.
    pairs = [(req.query, doc) for doc in req.documents]
    raw_scores = model.predict(pairs)

    # Sigmoid normalisation -> [0, 1]
    results = [
        RerankResult(index=i, score=1.0 / (1.0 + math.exp(-s)))
        for i, s in enumerate(raw_scores)
    ]
    results.sort(key=lambda r: r.score, reverse=True)

    if req.top_n is not None:
        results = results[: req.top_n]

    log.info("Reranked %d candidates for query (%.40s...) - top score: %.4f",
             len(req.documents), req.query, results[0].score if results else 0)
    return RerankResponse(results=results)


@app.get("/health")
def health():
    return {"status": "ok", "model": app.state.model_name}


# -- CLI ----------------------------------------------------------------------

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Orbit Reranker Microservice")
    parser.add_argument("--port", type=int, default=8001)
    parser.add_argument("--model", default="BAAI/bge-reranker-v2-m3",
                        help="HuggingFace model id")
    args = parser.parse_args()

    app.state.model_name = args.model

    # Pre-load the model so the first request is fast (not lazy)
    log.info("Pre-loading cross-encoder model: %s", args.model)
    from sentence_transformers import CrossEncoder
    _model = CrossEncoder(args.model, max_length=512)
    log.info("Model loaded - ready for requests")

    import uvicorn
    log.info("Starting Orbit Reranker on port %d", args.port)
    uvicorn.run(app, host="0.0.0.0", port=args.port, log_level="info")