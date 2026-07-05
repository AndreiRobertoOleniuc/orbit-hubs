# Orbit — User Story Backlog (MoSCoW)

> **Status:** AP3 deliverable (Anforderungsanalyse). Living document — versioned in GitLab, refined as the MVP is built.
> **Source:** `Orbit_Spec.md` (Problem / Akteure / Szenarien / Konzept / MVP-Abgrenzung) and the five high-fidelity flows (Z-Orbit-Flows).
> **Companion docs:** `02_data_model.md` (what each story needs in the data layer), SystemDesign/ (architecture).

This backlog turns the Orbit concept into prioritized, testable user stories for the Kanban board. Prioritization uses **MoSCoW** (Must / Should / Could / Won't-this-MVP). Each story has an ID for traceability into the data model, API contract, and the Usability-Test plan (AP9).

## How to read this

- **Actor model.** There are two actor *types* — **Person** and **Organisation** — but the meaningful split is between two *modes* every actor can take: **demand** (acting: search → request) and **supply** (waiting: the auto-built profile). The platform is deliberately asymmetric — demand acts, supply waits. Stories are grouped by surface and mode, not by actor type.
- **Story IDs.** `US-<area>-<n>`. Areas: `SRCH` (Discovery search), `REQ` (Anfrage lifecycle), `BOARD` (Anfrage-Board), `PROF` (profile/supply), `ONB` (onboarding), `CONTACT` (first contact), `NOTIF` (notifications).
- **The "Definition of Done"** for the Must tier is what closes milestone **M4 (MVP lauffähig, 24.07.2026)**.

## Quality criteria these stories serve (from the Aufgabenvereinbarung)

These are the project-level acceptance gates, checked in AP9 / M5. Individual stories reference them.

| Code | Criterion | Target |
|---|---|---|
| QC-1 | Match relevance — proposed matches judged thematically relevant by testers | ≥ 70 % |
| QC-2 | Explainability — testers can understand *why* a match was proposed | ≥ 80 % |
| QC-3 | Onboarding effort — first-time profile creation / data import | < 10 min |
| QC-4 | Scenario coverage — distinct matchmaking scenarios the concept covers | ≥ 3 |

---

## MUST — the MVP is not the MVP without these

These map 1:1 to the "Umgesetzt" section of the spec and define M4.

### Discovery search (demand, step 1)

**US-SRCH-1 — Free-text semantic search without login**
*As* a person with a need, *I want* to type what I'm looking for in free text and get ranked matches immediately, *so that* I find relevant people/companies without creating an account first.
- Search runs against existing profiles via hybrid retrieval (dense + lexical, fused).
- No login required to search or view results.
- The query text is preserved if the user later escalates to an Anfrage (→ US-REQ-1).
- *Serves QC-1.*

**US-SRCH-2 — Scope chips hard-filter results**
*As* a searcher, *I want* to pick a scope (Alle / FHNW / Baden + 20 km / Verbundene Kreise), *so that* only matches inside that scope are shown and out-of-scope noise is removed.
- Scope is a **hard filter**: out-of-scope results are excluded from the result set entirely, not merely ranked lower. Every active scope filters (FHNW and Baden + 20 km both hard-exclude); "Alle" applies no filter.
- Within the filtered set, results are still ranked by relevance and the remaining re-rank weights (availability, presence_mode).
- "Verbundene Kreise" is selectable but inert this MVP (→ US-SRCH-7, Won't).
- *Trade-off (accepted):* a strong match just outside the chosen radius will not surface under that scope — the searcher widens to "Alle" to see it. This is the deliberate behaviour, replacing the earlier re-rank-only design.

**US-SRCH-3 — Every result carries a plain-language rationale with cited evidence**
*As* a searcher, *I want* each match to explain *why* it was surfaced, citing real sources (GitHub repos, company pages, publications), *so that* I trust the suggestion.
- Rationale is text, generated on demand (lazy, on result expand) — not a percentage score.
- Each rationale cites ≥ 1 source document with a footnote-style label and link.
- *Serves QC-2 (primary), QC-1.*

**US-SRCH-4 — Distance shown on each result**
*As* a searcher, *I want* to see how far a match is (e.g. "12 Min. von FHNW Brugg"), *so that* I can weigh proximity.

### Anfrage lifecycle (demand, step 2)

**US-REQ-1 — Escalate a no-hit search into a public Anfrage**
*As* a searcher whose search didn't satisfy, *I want* to turn my query into a persistent public request, *so that* my need doesn't hit a dead end on an empty platform.
- Login is required **only at this point**; the preserved query text pre-fills the Anfrage.
- An Anfrage carries: title, description, presence mode, scope, tags, and the originating-actor type (Privatperson / Firma) shown visibly.

**US-REQ-2 — Anfrage runs a four-stage lifecycle**
*As* the platform, *I want* every Anfrage to move through **Offen → Verteilt → Kontaktiert → Geschlossen**, *so that* its state is always legible to all parties.
- **Offen:** live and publicly readable on the board.
- **Verteilt:** matching profiles + tag-subscribers notified; a count is shown ("4 Profile mit passenden Tags benachrichtigt").
- **Kontaktiert:** ≥ 1 contact request has been brokered.
- **Geschlossen:** marked solved by the creator; removed from the public board.

**US-REQ-3 — Tag-based distribution with a visible reason**
*As* a profile owner, *I want* to see *why* an Anfrage was routed to me ("Verteilt, weil dein Profil die Tags … trägt"), *so that* the distribution is transparent.
- *Serves QC-2.*

### Anfrage-Board (the public surface)

**US-BOARD-1 — Public, login-free feed of open Anfragen**
*As* anyone, *I want* to read the board of open requests without logging in, *so that* the platform has value even before I join.

**US-BOARD-2 — Filter by tag and scope, sort by newest / most relevant**
*As* a board visitor, *I want* to filter by tag and scope and sort by Neueste or Relevanteste, *so that* I find requests I can answer.

**US-BOARD-3 — Anfrage detail view**
*As* a board visitor, *I want* a detail view of an Anfrage (full description, tags, presence mode, scope, originator type), *so that* I can decide whether to respond.

**US-BOARD-4 — Subscribe to a tag**
*As* an actor, *I want* to subscribe to tags, *so that* I'm notified when matching Anfragen appear (→ US-NOTIF-1).

### Profiles (supply — passive, never a post)

**US-PROF-1 — Auto-generated profile from linked sources**
*As* a person or organisation, *I want* my profile built automatically from public sources (Person: GitHub, ORCID, LinkedIn-PDF, website, languages; Org: Handelsregister/Zefix, company website), *so that* I'm findable 24/7 without filling forms.
- Tags and description are derived from the sources.
- Every claim on the profile is backed by a cited source document.
- *Serves QC-3.*

**US-PROF-2 — Manually edit the generated profile**
*As* a profile owner, *I want* to edit the auto-derived tags and description, *so that* I can correct or sharpen them.

**US-PROF-3 — Availability toggle ("Offen für Anfragen")**
*As* a profile owner, *I want* a toggle that marks me open for requests, *so that* my ranking rises and a badge shows.
- When expanded, captures structured fields: *Ab wann*, *Pensum/Notiz*, *Bevorzugte Zusammenarbeit* (Bachelor-/Masterarbeit, Innosuisse-Projekt, Bezahltes Mandat, Forschungskollaboration).
- Availability is a re-ranking weight.

**US-PROF-4 — "Offene passende Anfragen" on the profile**
*As* a profile owner, *I want* open Anfragen that match my tags shown directly on my profile, *so that* I can react with *Antworten*.

### Onboarding

**US-ONB-1 — Person onboarding: link sources → generated profile**
*As* a new person, *I want* to link my public sources and get a generated profile in under 10 minutes, *so that* onboarding is low-effort.
- *Serves QC-3.*

**US-ONB-2 — Organisation onboarding: Zefix match → verified profile**
*As* an organisation, *I want* to register via Handelsregister/Zefix and receive an **HR-verifiziert** badge, *so that* my profile is trusted.
- *Serves QC-3.*

### First contact

**US-CONTACT-1 — One-time contact hand-off by email**
*As* a searcher, *I want* to send a short "Stell dich einmal vor" message that delivers my note and contact details to the recipient by email, *so that* we continue off-platform.
- The platform's role ends at first contact: no on-platform chat, no inbox thread.
- Incoming contact requests appear as a profile notification — a one-time hand-off point, not a messaging inbox.

### Notifications

**US-NOTIF-1 — Notify matching profiles and tag-subscribers on distribution**
*As* the platform, *I want* to notify matching profiles and tag-subscribers when an Anfrage is distributed, *so that* supply reacts to demand.

---

## SHOULD — strong value, include if Must is on track

**US-SRCH-5 — Result mix across people, companies, and Anfragen**
A single query should return a sensible mix (people, a company, relevant open Anfragen), as the primary demo scenario expects.

**US-PROF-5 — Profile rationale parity**
Profile cards surfaced in search reuse the same cited-evidence rationale style as search results (consistency for QC-2).

**US-BOARD-5 — Originator-type badge on every board card**
Each Anfrage card visibly shows Privatperson vs. Firma at the feed level (not only in detail).

**US-NOTIF-2 — Notification centre / digest on the profile**
A lightweight place where distribution and contact notifications collect, rather than email-only.

---

## COULD — nice, low-risk if time remains

**US-SRCH-6 — Saved searches.** Re-run a previous query.
**US-PROF-6 — Profile completeness hint.** "3 von 5 Quellen verbunden" style nudge during onboarding.
**US-BOARD-6 — Anfrage expiry / auto-deactivate.** `valid_until` auto-expires stale Anfragen.

---

## WON'T — explicitly out of this MVP (concept-only, per spec)

Listing these protects scope and is itself a graded design decision (the spec's "Konzept-only" and "Bewusste Nicht-Funktion").

**US-SRCH-7 — "Verbundene Kreise" active scope.** Selectable but has no effect this MVP.
**US-REC-1 — Empfehlungsketten.** Routing unanswered Anfragen through referral chains to onboard new actors.
**US-REQ-4 — Intelligent escalation routing** of unanswered Anfragen via organisation profiles.
**US-ONB-3 — LinkedIn company-page + case-study import** (organisation).
**US-ONB-4 — Google-Scholar match** (person).
**US-OFFER-1 — Active "Bieten" posts.** Removed on purpose — keeps the board demand-side and spam-free. *This is a deliberate non-feature, not an omission.*

---

## Traceability — spec MVP feature → Must story

Every "Umgesetzt" item in the spec maps to a Must story (verified in `03` review pass):

| Spec MVP feature | Story |
|---|---|
| Anfrage erstellen (Freitext, Titel, Beschreibung, Präsenzmodus, Geltungsbereich, Tags) | US-REQ-1 |
| Direktes semantisches Matching mit Begründungen + zitierten Quellen | US-SRCH-1, -3 |
| Anfrage-Board (öffentlich, Tag-/Umkreis-Filter, Sortierung, Tag-Abo) | US-BOARD-1..4 |
| Vierstufiger Anfrage-Lebenszyklus | US-REQ-2 |
| Tag-basierte Verteilung + Benachrichtigung mit sichtbarer Begründung | US-REQ-3, US-NOTIF-1 |
| Profile (Person + Org, auto-generiert, mit Nachweisen, editierbar) | US-PROF-1, -2 |
| Zefix-/Handelsregister-Abgleich + HR-Verifikation | US-ONB-2 |
| Verfügbarkeits-Toggle mit strukturierten Angaben | US-PROF-3 |
| Profilseitige "Offene passende Anfragen" + eingehende Kontaktanfragen | US-PROF-4, US-CONTACT-1 |
| Kontakt-Übergabe per E-Mail | US-CONTACT-1 |
