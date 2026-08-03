# BadenConnect

> Semantic matchmaking between Swiss startups, researchers, and industry —
> anchored to the Handelsregister, explained in plain language.

[![FHNW Projekt 5 2025](https://img.shields.io/badge/FHNW-Projekt%205%202025-blue)](https://www.fhnw.ch)
[![Baden 4.0](https://img.shields.io/badge/Baden-4.0%20Initiative-teal)](https://www.baden.ch/de/home/wirtschaft/baden-40.html/3454)
[![Client: PowerPlant](https://img.shields.io/badge/Client-PowerPlant-orange)](https://www.powerplant-baden.ch/)
[![Status: Work in Progress](https://img.shields.io/badge/Status-Work%20in%20Progress-gray)]()

---

## Handover / run locally

Start with [RUNBOOK.md](RUNBOOK.md) for the current local setup, test and deployment path. Technical evidence and design material are deliberately kept outside the runtime repository in [`../TechnicalImplementationDocs/`](../TechnicalImplementationDocs/); historical experiments are in [`../Prototyping/`](../Prototyping/).

---

## The Problem

In the Swiss innovation ecosystem, finding the right collaborator still relies almost entirely on **Vitamin B** — personal networks, Slack groups, and conference corridors. A firmware startup in Baden has no way to know that an FHNW researcher two streets away published exactly the work they need. A KMU looking for an Innosuisse co-applicant defaults to expensive consultants because the discovery mechanism simply doesn't exist.

BadenConnect is a digital version of walking into the office next door. Not LinkedIn. Not a job board. Something in between: a low-friction bulletin board backed by a semantic discovery engine that explains every match in plain language.

---

## What BadenConnect Is

Two surfaces, one data layer.

### Half 1 — Inserat Board _(Ricardo-model)_

A public feed of "looking for", "offering", and "open call" posts.

- Readable **without login**
- Tag-subscribable
- Post in **under 2 minutes**

### Half 2 — Discovery Engine

Free-text semantic search across FHNW → Aargau → Switzerland.

- Returns **ranked people and companies** with a text rationale and source evidence for every result
- No percentage scores as the primary signal
- Scope controls as re-ranking weights: FHNW first, then Aargau, then CH-wide

---

## Three Core Scenarios

### Scenario 1 — Startup ↔ Expert _(Primary — Demo Focus)_

**Persona:** Founder needs short-term embedded firmware help.

**Today:** Vitamin B — co-founders, Slack groups, networking events, LinkedIn spam. 2–3 weeks, often gives up.

**BadenConnect:** Posts inserat (2 min) → public, no login needed → engine surfaces matches with rationales (GitHub repos, company pages) → direct contact or someone reacts to the inserat.

**Why first:** Norbert and Nitish explicitly asked to anchor here. Inserat alone beats Vitamin B even before matching is polished.

---

### Scenario 2 — Researcher → Industry Partner

**Persona:** Norbert has a paper and needs a KMU to validate and co-write an Innosuisse proposal.

**Today:** Ad-hoc LinkedIn, asks colleagues, conferences. Outbound discovery is broken.

**BadenConnect:** Posts inserat describing the research opportunity → KMUs with matching tags get notified (solves awareness gap around Innosuisse) → reverse also works: company posts need, researcher's auto-built profile surfaces with a bridging-concept rationale.

**Why second:** Closest to the original brief. The harvester stack (OpenAlex/ORCID/ROR/IRF) is most load-bearing here.

---

### Scenario 3 — Industry → Expert Under Time Pressure

**Persona:** Project lead needs a 2-month multimodal mobility specialist.

**Today:** Generic Google searches, info@ emails, defaults to expensive consultancies.

**BadenConnect:** Free-text query → semantic search across academia and freelancers → FHNW first, then CH-wide → text rationale explains why each match is relevant → direct contact or post inserat.

**Why third:** Important, but falls out of building Scenarios 1 and 2 well.

---

## Data Sources

| Scope              | Source                                              | What it covers                    |
| ------------------ | --------------------------------------------------- | --------------------------------- |
| Companies          | LINDAS SPARQL → Handelsregister                     | Swiss company anchor layer        |
| Researchers        | ORCID · OpenAlex · ROR                              | Publication graph, affiliations   |
| FHNW outputs       | IRF FHNW via OAI-PMH                                | Institutional research repository |
| On-demand (opt-in) | GitHub · LinkedIn · Google Scholar · personal sites | User-linked at registration       |

> **Addressing the GitHub-only gap:** Developers with no academic papers but real, demonstrable skills are first-class citizens. GitHub profile enrichment is a core data source, not an afterthought.

**Profiles are generated on-demand, not authored.** Registration = drop in a LinkedIn URL, ORCID iD, or GitHub handle — the system builds your profile. No manual form filling.

---

## UX Principles

- **Evidence-forward, not score-forward.** Every match explains why with cited sources. No opaque percentage bars.
- **Read without login.** The Inserat Board is fully public. Friction only at the moment of contact or posting.
- **Post in under 2 minutes.** If it takes longer, the inserat primitive has failed.
- **DE default / EN toggle.** Swiss context first. The multilingual embedding model handles cross-language matching transparently.
- **Bulletin-board aesthetic.** Quiet. No LinkedIn gamification. No feed algorithms optimizing for engagement.

---

## Architecture

The matching pipeline:

```
Post / Query
    ↓
Embedding Retrieval  (multilingual-e5-large)
    ↓
Hybrid Search  (BM25 + pgvector)
    ↓
Reciprocal Rank Fusion  (scope weights: FHNW → Aargau → CH)
    ↓
LLM Rationale Generation  (top results + source evidence → plain-language explanation)
```

### Build Order (Common Foundation)

1. **Inserat Primitive** — Ricardo-style post model. Touches all three scenarios. Load-bearing first deliverable.
2. **Handelsregister Entity Index** — LINDAS SPARQL backbone. All discovery is anchored here.
3. **Researcher Profile Aggregation** — ORCID + OpenAlex + ROR + IRF FHNW (OAI-PMH) + opt-in GitHub/LinkedIn.
4. **Semantic Matching** — `multilingual-e5-large` embeddings + pgvector. Hybrid BM25 + vector search. RRF reranking with geographic scope weights.
5. **Rationale Generator** — Top ranked results passed to LLM with source evidence. Output: plain-language "why this match" with cited sources.
6. **Lightweight Onboarding** — Integration links only. No manual profile forms.

---

## Tech Stack

| Layer          | Choice                                 | Rationale                                                                                                                                                    |
| -------------- | -------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Frontend**   | React · ShadCN · Tailwind              | Zero custom CSS overhead. Keeps the bulletin-board aesthetic achievable in sprint time.                                                                      |
| **Auth**       | Clerk                                  | Pre-built OAuth for GitHub + LinkedIn social login — the exact registration pattern the UX requires. Saves weeks of OAuth headache.                          |
| **Backend**    | Spring Boot Modulith                   | Harvester, matching, and inserat logic in isolated modules. Start monolith, grow like microservices. JPA for CRUD, JDBC for raw pgvector queries.            |
| **Database**   | PostgreSQL + pgvector                  | Single source of truth. Hybrid vector + relational filters in one query: "people like this AND in Aargau AND tagged Firmware." No separate vector DB sprawl. |
| **Embeddings** | `multilingual-e5-large`                | Gold standard for DE/EN cross-lingual retrieval. Integrates via Spring AI with local inference (Ollama / TEI).                                               |
| **Data Layer** | LINDAS SPARQL · OAI-PMH · OpenAlex API | Swiss-specific open data infrastructure. LINDAS anchors to Handelsregister. OAI-PMH covers IRF FHNW repository outputs.                                      |

> **Note on data residency:** Clerk is US-based SaaS. For an MVP this is a non-issue. For a government-funded FHNW production deployment, FADP compliance is a talking point worth raising early.

---

## Team

| Role       | Person                            |
| ---------- | --------------------------------- |
| Engineer   | Andrei Oleniuc                    |
| Engineer   | Luca Nana                         |
| Supervisor | Norbert Seyff (FHNW)              |
| Supervisor | Nitish Patkar (FHNW)              |
| Client     | PowerPlant · Baden 4.0 Initiative |

---

_FHNW Project 5 Thesis · Spring 2025 · Windisch, Aargau_
