# Orbit — Task Breakdown (Kanban backlog)

> **Status:** AP3 → AP7/AP8 bridge. This is the layer between *user stories* (what/why) and *commits* (how). Each card here is a pullable Kanban item: one owner, ~½–1 day, one PR.
> **Reads from:** `01_user_stories_moscow.md` (the stories), `02_data_model.md` (the data model / tables).
> **Convention:** every task references its parent story and carries its own Definition of Done.

## How this maps to your workflow

```
Epic  ─▶  User Story  ─▶  Task  ─▶  commit / PR
(area)    (what + why)    (how + done)   (the code)
US-SRCH-1                 T-SRCH-1-BE, T-SRCH-1-FE, ...
```

- **Card ID:** `T-<story>-<layer>`. Layers: `BE` (Spring), `FE` (React), `INT` (wiring/integration), `DATA` (schema/migration/seed), `QA` (acceptance check).
- **Estimate (E):** rough size — `S` ≈ ½ day, `M` ≈ 1 day, `L` ≈ 2 days. For burndown, not a contract.
- **Done:** the per-card acceptance condition. A card is "Done" only when its check passes — this is what you demo at M4 and test in AP9.
- **Board columns:** `Backlog → To Do (sprint) → In Progress → Review → Done`. Pull by MoSCoW priority; Must first.

## Foundation epic (do once, before feature cards) — EPIC-0

These aren't tied to a single story; they unblock everything. They are AP7/AP8 setup (the "Projektsetup" activity).

| Card | E | Description | Done |
|---|---|---|---|
| T-FND-DATA-1 | M | Author production schema (Orbit v2) from `02_…` §2 as Flyway/Liquibase migration; drop `v10_` prototype prefix | `mvn flyway:migrate` builds all tables clean on the compose pgvector |
| T-FND-DATA-2 | M | Seed script: port prototype seed people/companies/inserate into v2 tables (entities, documents, requests) | ≥ 10 entities + ≥ 3 requests load; embeddings populated |
| T-FND-BE-1 | M | Spring modulith module skeletons (`profile`, `request`, `discovery`, `onboarding`, `contact`, `notification`) with published-interface packages | Modulith verification test passes; no cross-module bean injection |
| T-FND-BE-2 | S | Add Ollama to `compose.yaml` (or external config) + Spring AI embedding bean wired | App boots; a test embed call returns vector(1024) |
| T-FND-BE-3 | M | Decide + wire auth (Clerk JWT vs. Spring Security OAuth — see `02_…` §6 Q1); `(auth)` vs `(public)` route guards | Public routes reachable token-free; auth routes 401 without token |
| T-FND-FE-1 | M | Frontend app shell: routing, layout, API client, theme (ShadCN already scaffolded) | Routes for /search, /board, /profile, /onboarding render placeholders |
| T-FND-INT-1 | S | CI on FHNW GitLab: build BE + FE, run tests on push | Pipeline green on main |

---

## MUST cards — these define M4 (MVP lauffähig)

Grouped by epic = story area. Backend and frontend cards for the same story can run in parallel once the API contract (`02_…` §4) is the agreed seam.

### EPIC SRCH — Discovery search

**US-SRCH-1 — Free-text semantic search without login**

| Card | E | Description | Done |
|---|---|---|---|
| T-SRCH-1-BE | L | `POST /api/v1/search` in `discovery`: DTOs, hybrid retrieve (pgvector dense + FTS), RRF fusion, entity rollup. Public route. | Curl with a query returns ranked `results[]` matching the §4 shape; no auth |
| T-SRCH-1-FE | M | Search input + submit; render `SearchResult[]` list; loading/empty states | Typing a query shows ranked cards from the live endpoint |
| T-SRCH-1-QA | S | Acceptance: query returns mixed people/companies, login-free, query text retained for escalation | Manual check + 1 integration test |

**US-SRCH-2 — Scope chips hard-filter results**

| Card | E | Description | Done |
|---|---|---|---|
| T-SRCH-2-BE | M | Apply scope as a hard filter before retrieval (fhnw ⊆ aargau ⊆ ch): a scoped query restricts the candidate set to in-scope rows; `all` = no filter; `connected` inert (treated as `all`) | Test: out-of-scope match is absent under FHNW/Baden scope, present under "Alle" |
| T-SRCH-2-FE | S | Scope chip component (Alle/FHNW/Baden+20km/Verbundene Kreise); pass to `/search` | Selecting a chip re-runs search and the result set shrinks to in-scope only; "Verbundene Kreise" visibly inert |

**US-SRCH-3 — Rationale with cited evidence (lazy)** — *primary QC-2 card*

| Card | E | Description | Done |
|---|---|---|---|
| T-SRCH-3-BE | L | `GET /search/rationale/{entityId}`: assemble top docs → Gemini Flash → cited plain-language text | Returns rationale + ≥1 citation with label+url |
| T-SRCH-3-FE | M | Expand-on-click reveals rationale + footnote citations (links to source) | Expanding a result shows text + clickable sources, no % score |
| T-SRCH-3-QA | S | QC-2 check: a non-author reader can state why the match was proposed | Verified in AP9 test protocol |

**US-SRCH-4 — Distance label**

| Card | E | Description | Done |
|---|---|---|---|
| T-SRCH-4-BE | S | Compute `distanceLabel` (straight-line from lat/lng MVP default — §6 Q3) | Result carries "12 Min./X km von …" string |
| T-SRCH-4-FE | S | Show distance on each result card | Distance visible per card |

### EPIC REQ — Anfrage lifecycle

**US-REQ-1 — Escalate no-hit search into a public Anfrage**

| Card | E | Description | Done |
|---|---|---|---|
| T-REQ-1-BE | M | `POST /api/v1/requests` (auth): create with title/desc/scope/presenceMode/tags/originatorType → status OPEN; embed on create | Creating returns a request in OPEN; embedding populated |
| T-REQ-1-FE | M | "Kein Treffer → Anfrage erstellen" form, pre-filled from the preserved query; login gate appears here only | No-hit search routes into a pre-filled form; login prompted |

**US-REQ-2 — Four-stage lifecycle**

| Card | E | Description | Done |
|---|---|---|---|
| T-REQ-2-BE | L | `PATCH /requests/{id}/status` state machine (§3): enforce legal transitions, owner-only CLOSE, write `request_transitions` | Illegal transition → 409; valid one logs a transition row |
| T-REQ-2-FE | M | Lifecycle badge + owner controls (mark distributed/closed) on request detail | State shown; owner can advance/close; non-owner cannot |
| T-REQ-2-QA | S | Acceptance: full Offen→Verteilt→Kontaktiert→Geschlossen walk-through | Demoable end-to-end on seed data |

**US-REQ-3 — Tag distribution with visible reason** — *QC-2*

| Card | E | Description | Done |
|---|---|---|---|
| T-REQ-3-BE | M | On distribute: match request tags ↔ entity tags + subscribers, write `request_distributions` with `reason` + `matched_tags`; transition to DISTRIBUTED | Distributing populates rows; count returned ("4 Profile benachrichtigt") |
| T-REQ-3-FE | S | Recipient sees "Verteilt, weil dein Profil die Tags … trägt" on their side | Reason string visible to notified profile |

### EPIC BOARD — Anfrage-Board

**US-BOARD-1/2/3 — Public feed, filter/sort, detail**

| Card | E | Description | Done |
|---|---|---|---|
| T-BOARD-1-BE | M | `GET /api/v1/board` (public): paged OPEN/DISTRIBUTED requests; `?tag=&scope=&sort=newest\|relevant` | Public list with filters + sort works; CLOSED excluded |
| T-BOARD-1-FE | M | Board feed page: card list, tag + scope filters, sort toggle | Filtering/sorting re-queries and re-renders |
| T-BOARD-3-FE | S | Request detail view (full desc, tags, presence, scope, originator type) | `GET /requests/{id}` rendered |

**US-BOARD-4 — Tag subscription**

| Card | E | Description | Done |
|---|---|---|---|
| T-BOARD-4-BE | S | `POST /subscriptions` + `DELETE /subscriptions/{tag}` (auth) | Subscribe/unsubscribe persists in `tag_subscriptions` |
| T-BOARD-4-FE | S | Subscribe button on tags/board | Toggling reflects subscription state |

### EPIC PROF — Profiles (supply)

**US-PROF-1 — Auto-generated profile**

| Card | E | Description | Done |
|---|---|---|---|
| T-PROF-1-BE | M | `GET /entities/{id}` (public): profile + cited docs + availability | Returns entity with documents + tags + availability |
| T-PROF-1-FE | M | Profile page: header, derived tags, evidence-cited sections, badges (verified/open) | Renders a seeded profile with citations |

**US-PROF-2 — Edit generated profile**

| Card | E | Description | Done |
|---|---|---|---|
| T-PROF-2-BE | S | `PATCH /entities/{id}` (auth, owner): edit tags/description | Owner edit persists; non-owner 403 |
| T-PROF-2-FE | S | Inline edit UI for tags/description | Owner can edit and save |

**US-PROF-3 — Availability toggle**

| Card | E | Description | Done |
|---|---|---|---|
| T-PROF-3-BE | M | `PUT /entities/{id}/availability`: toggle + available_from/capacity/preferred_modes; feed `is_open` into ranker | Toggle persists; open profiles rank higher |
| T-PROF-3-FE | M | Toggle + structured fields form; "Offen"-Badge | Toggling on shows badge + captures fields |

**US-PROF-4 — Matching requests on profile**

| Card | E | Description | Done |
|---|---|---|---|
| T-PROF-4-BE | S | `GET /entities/{id}/matching-requests` (auth, owner): open Anfragen matching my tags | Returns tag-matched OPEN requests |
| T-PROF-4-FE | S | "Offene passende Anfragen" section with *Antworten* action | Listed on profile; Antworten opens contact |

### EPIC ONB — Onboarding

**US-ONB-1 — Person onboarding** — *QC-3 (<10 min)*

| Card | E | Description | Done |
|---|---|---|---|
| T-ONB-1-BE | L | `POST /onboarding/person`: accept source links → enqueue ingest → build entity+documents; `GET …/status` ("3 von 5") | Linking sources yields a generated profile; status reflects sync |
| T-ONB-1-FE | M | Source-linking wizard (GitHub/ORCID/LinkedIn-PDF/website/languages) + progress | Walk-through produces a profile in < 10 min |

**US-ONB-2 — Organisation onboarding (Zefix)** — *QC-3*

| Card | E | Description | Done |
|---|---|---|---|
| T-ONB-2-BE | M | `POST /onboarding/organisation`: Zefix/Handelsregister match → set `verified=true` + uid → build profile | Valid UID/name → verified org profile |
| T-ONB-2-FE | S | Org registration form + HR-verifiziert badge | Verified badge shows on success |

### EPIC CONTACT — First contact

**US-CONTACT-1 — One-time email hand-off**

| Card | E | Description | Done |
|---|---|---|---|
| T-CONTACT-1-BE | M | `POST /api/v1/contact`: write `contact_handoffs`, email recipient note+contact; if request DISTRIBUTED → transition CONTACTED | Sending delivers email + logs hand-off; request advances |
| T-CONTACT-1-FE | S | "Stell dich einmal vor" dialog (short message) | Submitting confirms hand-off; no chat thread shown |
| T-NOTIF-1-BE | M | Notification fan-out on distribution + incoming contact (profile notifications) | Notified profiles + subscribers receive notification records |

---

## Summary — Must effort (rough)

| Epic | Cards | ~Effort |
|---|---|---|
| EPIC-0 Foundation | 7 | ~6 days |
| SRCH | 9 | ~7 days |
| REQ | 7 | ~6 days |
| BOARD | 5 | ~3.5 days |
| PROF | 8 | ~5 days |
| ONB | 4 | ~4.5 days |
| CONTACT | 3 | ~2.5 days |

≈ **34 Must cards / ~34 dev-days**, split across two developers and the AP7+AP8 window (11.05–24.07). That leaves headroom for Should cards and the iteration the high-fi flows will demand — and it confirms the AP7/AP8 hours are now integration + UI, not algorithm discovery (the prototype already retired that risk).

## Working agreement (so cards stay codeable)

1. **API contract is the seam.** BE and FE cards for a story can run in parallel because both code to `02_…` §4. Change the contract → update the doc in the same PR.
2. **A card is done when its "Done" passes**, not when the code compiles. QA cards exist for the gates that need a human judgement (QC-1, QC-2).
3. **Pull by MoSCoW.** Don't start a Should card while a Must card is in To Do.
4. **One card ≈ one PR.** Keeps the GitLab commit history reviewable (the Aufgabenvereinbarung asks for an "adäquate Commit-History").
