# Hugging Face Phase 1 Validation

Validated on July 20, 2026 against the local Orbit database. All SQL used for
this report ran in read-only transactions; no chunk or embedding rows were changed.

## Dataset baseline

| Measure | Value |
|---|---:|
| Entities | 54 |
| Sources | 1,150 |
| Chunks | 1,259 |
| Chunks with embeddings | 1,259 |
| Stored dimensions | 1,024 for every chunk |
| Stored model label | `multilingual-e5-large` |

## Live API contracts

- `intfloat/multilingual-e5-large` returned a flat array of 1,024 numbers.
- `BAAI/bge-reranker-v2-m3` returned one outer batch array containing one
  `{label, score}` object per input pair.
- The backend parser also accepts singleton-nested embeddings and older
  per-pair reranker nesting, while strictly validating result counts.

## Embedding compatibility

Thirty deterministic, representative database passages were embedded through
local Ollama and HF Inference using the same `passage: ` prefix.

| Measure | Result |
|---|---:|
| Local dimensions | 1,024 for all 30 |
| HF dimensions | 1,024 for all 30 |
| Minimum corresponding cosine | 0.9999861605 |
| Average corresponding cosine | 0.9999940568 |
| Maximum corresponding cosine | 0.9999959712 |
| Maximum absolute coordinate delta | 0.0007018844 |
| Local norm range | 0.9999995209–1.0000004015 |
| HF norm range | 0.9999999263–1.0000000920 |

Result: existing database vectors are compatible and do not need re-embedding.

## Reranker compatibility

Four fixed queries were scored against eight real entity summaries. Local and
HF produced identical top-three ordering and identical `0.55` pass/fail decisions.

The local Python service was found to apply sigmoid to a score already normalized
by the installed `sentence-transformers` version. Therefore local scores equal
`sigmoid(HF score)`. The HF client explicitly preserves this historical transform
so Phase 1 does not silently change Orbit's tuned threshold or final-score scale.
Removing the double sigmoid should be handled as a separate quality recalibration.

## End-to-end search comparison

The same 12 public search requests were run first with local providers and then
with HF providers, with grounding rationales disabled to reduce unrelated LLM work.
The HF backend used deliberately invalid local-model URLs, proving neither Ollama
nor the Python service was contacted.

- All targeted English searches retained their expected top entity.
- All negative/vague searches retained `sufficient=false`.
- Eleven of twelve complete responses retained the same sufficiency decision.
- The German social-work query changed from no result to the correct Peter
  Sommerfeld result. Provider-isolated fixed-query scoring matched; the complete
  search invokes Gemini separately for translation/condensation on each run, so
  this variation is not evidence of incompatible embedding/reranker providers.
- Matching result scores were identical or very close; the largest targeted
  delta was 0.0157 and did not change the top result.

## Verification outcome

- HF-only application startup: passed.
- Invalid local model URLs during HF run: passed.
- Existing database vectors preserved: passed.
- Required reranker outage maps to HTTP 503 after bounded retry: covered by tests.
- Smart `estimated_time`, `Retry-After`, deadline and non-retryable status handling:
  covered by tests.
