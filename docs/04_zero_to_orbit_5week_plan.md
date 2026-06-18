# Orbit — 5-Week MVP Plan: Cloud AI + k3s on Raspberry Pi

> **Constraint:** Build and deploy the Orbit MVP in **≤ 5 weeks** (not 11).  
> **Strategy:** Use external SaaS for every undifferentiated heavy lift (auth, embeddings, LLM, email). Run the application on **k3s on a Raspberry Pi with 20 GB RAM**. AI models stay in the cloud.
>
> Companion docs: `01_user_stories_moscow.md`, `02_data_model.md`, `03_task_breakdown.md`, `spec/Orbit_Spec.md`, and `04_zero_to_orbit_implementation_plan.md` (the slower 11-week baseline).

---

## 1. The 5-week equation

| Resource | Available |
|----------|-----------|
| Calendar | 5 weeks |
| Devs | 2 (you + Luca) |
| Dev-days | ~50 if full-time, ~40 realistically (meetings, reviews, scope churn) |
| Original Must effort | ~34 dev-days (`03_task_breakdown.md`) |
| Headroom | ~6–16 days for cloud integration, k3s, and iteration |

**Conclusion:** It is feasible **only** if you:

1. Cut every **Should/Could/Won't** story.
2. Replace self-hosted AI/auth with **external APIs**.
3. Ship a **minimal but end-to-end** MVP per the spec, not a polished product.
4. Deploy continuously from week 1 so there is no “deployment week” at the end.

---

## 2. External tools — the substitution table

| Concern | Self-hosted (prototype style) | **Cloud/SaaS replacement** | Time saved | Note |
|---------|-------------------------------|----------------------------|------------|------|
| Auth / OAuth | Spring Security OAuth2 | **Clerk** (frontend SDK + JWT backend verification) | ~1 week | FADP discussion deferred to M5 |
| Embeddings | Ollama `multilingual-e5-large` locally | **OpenAI `text-embedding-3-small` or `text-embedding-3-large`** via API | ~1 week | DE/EN quality is good. If spec insists on e5, use **HuggingFace Inference API** for `intfloat/multilingual-e5-large` |
| Rationale LLM | Gemini Flash locally or via API | **OpenAI `gpt-4o-mini`** with JSON-mode/structured outputs | ~2–3 days | Faster, cheaper, easier JSON schema |
| Email hand-off | Local SMTP | **Resend** or **SendGrid** | ~2 days | Reliable deliverability, no SMTP plumbing |
| Object storage (CV PDFs) | Local filesystem | **Backblaze B2** or a **MinIO sidecar** in k3s | ~1 day | Preserves files across pod restarts |
| CI/CD + image registry | Manual builds | **GitHub Actions** → Docker Hub / GitHub Container Registry | ~2 days | Every push builds and pushes images |
| Database | pgvector on compose | **Postgres + pgvector in k3s** (self-hosted container) | — | 20 GB RAM is plenty; no network latency to external DB |

### Recommended minimal stack

| Layer | Choice |
|-------|--------|
| Frontend | React + Vite + Tailwind v4 + ShadCN + **Clerk React SDK** |
| Backend | Spring Boot 4 + Spring Modulith + Spring AI + **OpenAI** embedding/LLM beans |
| Database | PostgreSQL 16 + pgvector in a k3s StatefulSet |
| Auth | Clerk (frontend) → JWT → Spring Security filter |
| Email | Resend HTTP API |
| File storage | Backblaze B2 or MinIO |
| Container orchestration | k3s on Raspberry Pi (20 GB RAM) |
| Ingress / TLS | k3s Traefik + Cloudflare Tunnel or Let's Encrypt (if public IP) |

---

## 3. What changes from the 11-week plan

### Keep from the prototype v10

- Entity / document split.
- Hybrid retrieval: **pgvector dense** + **PostgreSQL FTS**.
- **RRF** (`k=60`) fusion.
- Entity rollup with top-3 documents.
- Scope as a **hard filter** (out-of-scope rows excluded before retrieval; "Alle" = no filter). *Changed from the v10 re-rank-weight design; → US-SRCH-2.*
- Lazy rationale with citation validation.
- Seed personas (Marc Hofer, Sofia Keller, etc.).

### Change for Orbit v2 and speed

| Area | 11-week plan | **5-week plan** |
|------|--------------|-----------------|
| Auth | Decide later, maybe Spring OAuth | **Clerk immediately** — no debate |
| Embeddings | Self-hosted Ollama | **Cloud API** — one config line |
| LLM rationale | Gemini Flash API | **OpenAI gpt-4o-mini** with structured outputs |
| Person onboarding | GitHub + website + CV + ORCID | **GitHub + website + CV + manual edit only**; ORCID/Google Scholar deferred |
| Org onboarding | Full Zefix integration | **Minimal Zefix/Handelsregister match** via existing LINDAS SPARQL prototype (`prototyping/lindasEmbedding/`). Manual fallback for demo. |
| Notifications | In-app notification centre | **Email only for MVP**; in-app list is a 1-day add if time |
| Board sorting | newest + relevant | **newest only** for MVP; relevant sorting a 1-day add |
| Presence matching | full re-rank by mode | **store and display only**; matching weighting a 1-day add |
| Distance label | straight-line formatter | **straight-line only**, no routing API |

### Explicitly defer (out of 5-week scope)

- All **Should/Could/Won't** stories from `01_user_stories_moscow.md`.
- ORCID / Google Scholar / OpenAlex ingest for persons.
- LinkedIn company-page import for organisations.
- Empfehlungsketten / intelligent routing / Verbundene Kreise logic.
- Recommendation chains.
- Advanced analytics.
- Full FADP compliance audit (Clerk is US-based; document the risk).

---

## 4. Target k3s deployment architecture

```
Internet
   │
   ▼
Cloudflare Tunnel  (or your public IP + port forwarding)
   │
   ▼
k3s node (Raspberry Pi, 20 GB RAM)
   │
   ├── Namespace: orbit
   │   │
   │   ├── Deployment: orbit-frontend (nginx static, 1 replica)
   │   ├── Deployment: orbit-backend   (Spring Boot, 1 replica)
   │   ├── StatefulSet: orbit-postgres (Postgres 16 + pgvector, PVC)
   │   └── (optional) MinIO sidecar or separate pod for file storage
   │
   └── Traefik ingress routes:
       /  → frontend
       /api/v1/* → backend
```

### Why this fits on 20 GB RAM

| Component | Estimated RAM |
|-----------|---------------|
| Spring Boot backend | 512 MB–1 GB |
| PostgreSQL | 1–2 GB |
| nginx frontend | 64 MB |
| MinIO (optional) | 256 MB |
| k3s overhead | 1–2 GB |
| **Headroom** | **> 14 GB** |

No Ollama model means no 4–8 GB GPU/CPU load. AI is entirely cloud.

---

## 5. Week-by-week plan

### Week 1 — Foundation + deploy scaffold

**Goal:** The app builds, the schema exists, seed data loads, Clerk auth works, and a first version is running on k3s.

| Day | Backend (Dev A) | Frontend / DevOps (Dev B) |
|-----|-------------------|---------------------------|
| 1 | Schema migration (`V1__orbit_v2_schema.sql`) | App shell: routes, layout, theme, API client skeleton |
| 2 | Seed loader from v10 JSON → `entities`, `documents`, `requests` | Clerk setup: sign-in/sign-up components, token attach to API calls |
| 3 | Spring Modulith skeleton + boundary verification test | Dockerfile for frontend (nginx) + GitHub Actions build |
| 4 | OpenAI embedding + LLM client beans; fallback stubs | Backend JWT filter for Clerk tokens |
| 5 | Dockerfile / Jib for backend; k3s manifests (Postgres StatefulSet, backend, frontend) | k3s bootstrap scripts, local registry push, first deploy |

**End-of-week demo (M0):**
- `git push` triggers CI → image built → k3s rolling update.
- App reachable on Pi address.
- `/search` renders empty state.
- Seed script populates ≥10 entities and ≥3 requests with **cloud embeddings**.
- Clerk sign-in works.

---

### Week 2 — Discovery engine (public, no login)

**Goal:** Anyone can search and see ranked matches with cited rationales.

| Day | Backend | Frontend |
|-----|---------|----------|
| 1 | `POST /api/v1/search`: hybrid retrieve (dense + FTS) over `documents` + `requests`; RRF | Search input page |
| 2 | Entity rollup + scope hard filter (pre-retrieval) + availability boost | Result card component |
| 3 | `distanceLabel` from lat/lng | Scope chip component |
| 4 | `GET /api/v1/search/rationale/{entityId}` with OpenAI structured outputs + citation validation | Expandable rationale + footnote citations |
| 5 | Integration tests for search; fix ranking bugs | Responsive polish; empty/no-hit states |

**End-of-week demo (M1):**
- Query `"embedded firmware security review zwei wochen"` returns Marc Hofer / Sofia Keller.
- Scope chips hard-filter results; out-of-scope hidden under a scope, visible under "Alle".
- Expanding a result shows a German rationale citing GitHub repos.
- No login required.

---

### Week 3 — Profiles + onboarding

**Goal:** Users can create/view/edit profiles and toggle availability.

| Day | Backend | Frontend |
|-----|---------|----------|
| 1 | `GET /api/v1/entities/{id}`: profile + documents + tags + availability | Profile page |
| 2 | `PATCH /entities/{id}`: owner-only edit of tags/description | Inline edit UI |
| 3 | `PUT /entities/{id}/availability`: toggle + structured fields; feed into ranker | Availability form + "Offen" badge |
| 4 | Person onboarding endpoint (`POST /onboarding/person`): GitHub + website + CV ingest, status endpoint | Onboarding wizard for persons |
| 5 | Organisation onboarding (`POST /onboarding/organisation`): minimal Zefix/LINDAS match, `verified=true` | Org onboarding form + verified badge |

**End-of-week demo (M2):**
- A seeded profile renders with cited evidence.
- Owner toggles "Offen für Anfragen" → ranking changes.
- New user signs up via Clerk, links GitHub, and gets an auto-generated profile in <10 min.
- Organisation with valid UID gets HR-verified badge.

---

### Week 4 — Anfrage lifecycle + board + contact

**Goal:** A search becomes a request; the request moves through states; board is public; first contact is emailed.

| Day | Backend | Frontend |
|-----|---------|----------|
| 1 | `POST /api/v1/requests`: create from preserved query → `OPEN`; embed on create | "Kein Treffer → Anfrage erstellen" form, pre-filled |
| 2 | `PATCH /requests/{id}/status` state machine + `request_transitions`; owner-only CLOSE | Lifecycle badge + owner controls |
| 3 | Tag distribution: match request tags → entity tags + subscribers; write `request_distributions`; transition to `DISTRIBUTED` | Distributions visible to recipient with reason |
| 4 | `GET /api/v1/board`: public paged list; filter tag/scope; sort newest | Board feed + detail view + filters |
| 5 | `POST /api/v1/contact`: Resend email + `contact_handoffs`; if `DISTRIBUTED` → `CONTACTED`; tag subscribe/unsubscribe | Contact dialog + tag subscribe buttons |

**End-of-week demo (M3):**
- Search → no hit → create Anfrage → appears on public board.
- Distribute: matching profiles notified by email; reason visible.
- A recipient clicks "Kontakt aufnehmen" → email sent → request becomes `CONTACTED`.
- Owner closes request → disappears from board.

---

### Week 5 — Integration, polish, deploy hardening, QC

**Goal:** The three spec scenarios run end-to-end on k3s; QC gates pass; demo is ready.

| Day | Focus |
|-----|-------|
| 1 | `GET /entities/{id}/matching-requests`: open requests matching my tags; "Antworten" action on profile. |
| 2 | Lightweight notification list (in-app). Email notification templates polished. |
| 3 | End-to-end tests on k3s; performance tuning (DB indexes, connection pool, container limits). |
| 4 | Bug bash; board/sort relevance if time; board sort by relevance if time; final UI polish. |
| 5 | Demo rehearsal; QC-1/2/3/4 acceptance run; documentation hand-off. |

**End-of-week demo (M4 / MVP):**
- All three scenarios (Startup↔Expert, Researcher→Industry, Industry→Expert) are demoed on the Pi.
- Search, board, profiles, onboarding, request lifecycle, contact hand-off all work.
- QC-1 ≥70 %, QC-2 ≥80 %, QC-3 <10 min, QC-4 ≥3 scenarios.

---

## 6. Team split for 5 weeks

| Dev A (backend-heavy) | Dev B (frontend + DevOps-heavy) |
|-------------------------|-----------------------------------|
| Schema, seed loader, Modulith | App shell, Clerk integration, API client |
| Search backend (hybrid + RRF + rationale) | Search + board + profile UIs |
| Request lifecycle + distribution + contact backend | Request + contact + onboarding UIs |
| OpenAI / Spring AI integration | k3s manifests, CI/CD, Dockerfiles |
| Postgres performance / k3s backend deploy | Frontend deploy, ingress, TLS |

**Rule:** Pair for 1 hour each morning on the API contract, then split. Swap once mid-week in weeks 2–4 to avoid silos.

---

## 7. Week 1 concrete first commits

To avoid a big-bang PR, the first week should produce these reviewable commits:

1. **Schema migration** (`V1__orbit_v2_schema.sql`) — all Orbit v2 tables.
2. **Seed loader** — reads v10 JSON seeds, writes `entities`, `documents`, `requests`; converts old inserate → requests with `status=OPEN`.
3. **Modulith skeleton** — empty `profile`, `request`, `discovery`, `onboarding`, `contact`, `notification` packages + verification test.
4. **Clerk auth wiring** — frontend SDK + Spring Security JWT filter.
5. **Cloud AI client beans** — OpenAI embedding model + chat model with prompt/rationale helper.
6. **Docker + k3s scaffold** — backend Dockerfile/Jib, frontend nginx Dockerfile, GitHub Actions, k3s YAMLs.

By Friday of week 1, `git push main` should deploy to the Pi automatically.

---

## 8. Risk register — 5-week / Pi / cloud edition

| Risk | Impact | Mitigation |
|------|--------|------------|
| OpenAI API latency/cost on Pi | Medium | Use `text-embedding-3-small` (cheaper/faster) for MVP; set rate limits; cache rationale per `(query, entity, top-docs)` briefly. |
| Pi CPU too slow for Spring Boot cold start | Medium | Use GraalVM native image or at least `-Xmx1g`; keep image small; use k3s resource limits. |
| Clerk free tier limits / FHNW compliance concerns | Medium | Document for M5; use Clerk dev/test mode for demo. |
| Cloudflare Tunnel / public access unreliable | High | Have a local-network demo fallback (same WiFi). Set up Tailscale/Zero Trust as backup. |
| Seed data too small to demo board | Low | Generate 20–30 synthetic entities/requests with cloud embeddings if live sources are sparse. |
| k3s PVC / Postgres data loss on Pi reboot | Medium | Back up Postgres with a nightly `pg_dump` to B2/MinIO; document restore. |
| OpenAI JSON schema drift | Low | Use Spring AI `BeanOutputConverter` or manual parser with fallback to raw evidence list. |
| Cross-module dependency leaks in Modulith | Medium | T-FND-BE-1 verification test fails the build; review every PR. |

---

## 9. MVP done checklist (M4)

- [ ] Public search without login returns ranked people/companies/requests.
- [ ] Scope chips hard-filter (out-of-scope excluded; "Alle" = no filter).
- [ ] Every result shows a plain-language rationale with cited evidence.
- [ ] Distance label shown per result.
- [ ] Anfrage creation requires login only at escalation; pre-fills search query.
- [ ] Request lifecycle `OPEN → DISTRIBUTED → CONTACTED → CLOSED` enforced.
- [ ] Tag distribution writes a visible reason.
- [ ] Board is public, filterable, tag-subscribable.
- [ ] Profiles are auto-generated, cited, editable.
- [ ] Availability toggle + structured fields work and boost ranking.
- [ ] Organisation onboarding gives HR-verified badge.
- [ ] First contact is a one-time email hand-off.
- [ ] All of the above is deployed and reachable on k3s on the Pi.
- [ ] QC-1/2/3/4 acceptance gates met.

---

## 10. What “done” means each week

| Week | Done signal |
|------|-------------|
| 1 | CI builds images; k3s deploys; schema + seeds load; Clerk sign-in works. |
| 2 | Search + rationale demo works without login on the Pi. |
| 3 | A new user can sign up, link GitHub, and get a profile. |
| 4 | Full Anfrage lifecycle demo from search to contact. |
| 5 | Three scenarios demoed end-to-end; QC gates passed. |

