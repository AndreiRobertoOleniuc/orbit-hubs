# Supply-Side Build — Availability, Affiliation, Hubs (+ admin), Tag Subscriptions

## Context

Orbit is asymmetric: **demand acts, supply waits** (reference doc §1). Today the
supply side is only half-built — an entity has sources, an LLM summary, and tags
(`entity_tags`, V5), but **none of the standing-profile signals** the Discovery
Engine needs to *find and rank* it: structured availability, affiliation/languages/
location, hub membership, and topic subscriptions all don't exist yet. Without them a
profile can be shown but can't participate in re-ranking (§6.3) or rule-based
distribution (§8).

This build adds that supply layer end-to-end so a person/org can be **found, ranked,
and notified**. Per the user's decisions:

- **Hubs are in scope now** (not deferred), and we add a **hubs admin** surface so an
  operator can create/edit/delete the hub vocabulary. Regions back the Hub → Region →
  CH tree (§5).
- **Availability is profile-only** for now — an editor + badge on the profile, **no new
  onboarding step**. The onboarding state machine (`type → [firm] → sources`) is left
  untouched.

### Ground truth confirmed by exploration (corrects older docs)

- **PKs are `bigint GENERATED ALWAYS AS IDENTITY`**, not text slugs. `entities`,
  `tags`, `sources`, `chunks` all follow this; `users.id` is the only text PK (Clerk
  `sub`). `EntityDto.id` is already `number`. The reference doc's `bigint` design fits.
- **Availability does NOT exist** despite old CLAUDE.md mentioning a 1:1 child. We add
  it as **columns on `entities`** (reference §14 ALTER), not a child table.
- **Auth derives no authorities** (`setJwtGrantedAuthoritiesConverter(→ emptyList())`),
  and `CurrentUser` exposes only `userId()`/`email()`. So the admin gate is an **env
  allowlist of Clerk user IDs**, checked in the controller — no roles/migration.
- Tags/`entity_tags` are real (V5) with an `origin ∈ {derived,manual}` flag and
  `TagRepository.findBySlug`. Subscriptions build on this vocabulary.
- Existing entity-edit endpoints (`PUT /entities/{id}/tags`, `/summary`, `POST
  /resync`) establish the controller + RTK pattern to mirror.

## Decisions (locked)

| Question | Decision |
|---|---|
| Availability storage | Columns on `entities` (ALTER), not a child table |
| PK strategy | `bigint IDENTITY` everywhere (match existing) |
| Hubs | Include now: `regions` + `hubs` + `hub_memberships` + `entities.home_region_id`, seeded |
| Admin gate | Env allowlist `orbit.admin.user-ids` (CSV of Clerk subs); `isAdmin` on `/me` |
| Subscriptions scope | `tag_subscriptions(entity_id, tag_id)` — hangs off the claimed entity (reference §9) |
| Availability entry point | Profile editor only; onboarding wizard unchanged |
| Date inputs | Native `<input type="date">` (avoid pulling in Calendar/Popover) |
| Mode-dependent profile | Own profile shows supply editors; demand-only hiding is a noted follow-up |

---

## Backend (`backend/src/main/java/com/orbit`, `.../resources/db/migration`)

Mirror the existing `EntityController` + DTO record pattern throughout. Every schema
change is a **new Flyway migration** (Hibernate is `validate`).

### 1. Migrations (next is V6)

- **`V6__supply_availability.sql`** — `ALTER TABLE entities ADD`:
  `open_to_requests boolean not null default false`,
  `available_from date`, `available_to date`, `pensum_pct int`,
  `availability_note text`, `preferred_collaboration text[]`, `languages text[]`,
  `affiliation_kind text` (`company|self_employed|private`), `location_label text`.
- **`V7__hubs_regions.sql`** — create `regions` (self-ref `parent_region_id`, `slug`
  unique), `hubs` (`slug` unique, `region_id` FK, `hub_type`, lat/lng),
  `hub_memberships` (`entity_id`,`hub_id` composite PK, `affiliation`, `source`
  default `'derived'`). `ALTER entities ADD home_region_id bigint references
  regions(id)` + index. **Seed:** CH (root) → Aargau → Region Baden; hubs FHNW,
  Baden 4.0 / PowerPlant under Region Baden. Shapes per reference §14 (3.1/3.2).
- **`V8__tag_subscriptions.sql`** — `tag_subscriptions(entity_id bigint fk
  entities, tag_id bigint fk tags, created_at, primary key(entity_id,tag_id))`.

### 2. JPA entities + repositories (`domain/entity`, `domain/repository`)

- Extend **`DiscoveryEntity`** with the V6 columns. `text[]` → Hibernate array
  mapping (reference §13: arrays need explicit type, e.g. `@JdbcTypeCode(SqlTypes.ARRAY)`
  + `@Column(columnDefinition="text[]")`); `home_region_id` as `@ManyToOne Region`
  (lazy) or a plain `Long` if we avoid the association.
- New: **`Region`** (self-ref `@ManyToOne parent`), **`Hub`** (`@ManyToOne region`),
  **`HubMembership`** (`@IdClass`, like `EntityTag`/`EntityTagId`),
  **`TagSubscription`** (`@IdClass`).
- Repos: `RegionRepository`, `HubRepository` (`findByRegionId`, `findBySlug`),
  `HubMembershipRepository` (`findByEntityId`), `TagSubscriptionRepository`
  (`findByEntityId`). Follow `EntityTagRepository` conventions.

### 3. DTOs + endpoints

Extend **`EntityDto`** (record in `app/controllers/dto`) with: an `AvailabilityDto`
(open, from, to, pensumPct, note, preferredCollaboration[]), `affiliationKind`,
`languages[]`, `locationLabel`, `hubs: List<HubDto>`, `homeRegion: RegionDto?`. Add
`HubDto`, `RegionDto`, `SubscriptionDto` records. Update `toDto()` in
`EntityController` to populate them.

New endpoints (owner-guarded; reuse `CurrentUser` + 404/403 pattern):

| Method | Path | Purpose |
|---|---|---|
| PUT | `/api/v1/entities/{id}/availability` | open toggle + dates + pensum + collaboration[] |
| PUT | `/api/v1/entities/{id}/profile-meta` | affiliationKind, languages[], locationLabel |
| PUT | `/api/v1/entities/{id}/hubs` | replace hub memberships (`source='confirmed'`) |
| GET | `/api/v1/entities/{id}/subscriptions` | list subscribed tags |
| PUT | `/api/v1/entities/{id}/subscriptions` | replace subscription set |
| GET | `/api/v1/hubs` | list all hubs (for selector) — **whitelist as public** |

### 4. Admin surface (hub management)

- New `AdminProperties` (or `@Value`) binding `orbit.admin.user-ids` (CSV) in
  `application.yaml`, env-overridable (`ORBIT_ADMIN_USER_IDS`). Helper
  `isAdmin(currentUser.userId())`.
- **`AdminHubController`** (`/api/v1/admin/hubs`): `GET` (list w/ region), `POST`
  (create), `PUT /{id}` (edit), `DELETE /{id}`. Each returns **403** if the caller
  isn't in the allowlist. Regions read via `GET /api/v1/admin/regions` (seed-only for
  now — hubs must attach to an existing region).
- **`MeController`**: add `isAdmin` to the `/me` payload.
- **`ClerkSecurityConfig`**: add `.requestMatchers(GET, "/api/v1/hubs").permitAll()`.
  `/admin/**` stays `authenticated()` (controller does the allowlist check). No new
  Spring authority needed.

---

## Frontend (`frontend/src`)

Prettier: no semicolons, double quotes. Mirror the existing RTK mutation + tag-
invalidation pattern in `lib/api.ts`.

### 5. Types + API client

- **`types/api.ts`**: extend `EntityDto` with `availability`, `affiliationKind`,
  `languages`, `locationLabel`, `hubs`, `homeRegion`. Add `AvailabilityDto`, `HubDto`,
  `RegionDto`, `SubscriptionDto`; add `isAdmin: boolean` to `MeDto`.
- **`lib/api.ts`**: add tagType `"Hubs"`. New endpoints: `updateAvailability`,
  `updateProfileMeta`, `updateEntityHubs`, `getSubscriptions`, `updateSubscriptions`
  (invalidate `{Entity,id}` + `MyEntities`); `getHubs` (provides `"Hubs"`); admin
  `createHub`/`updateHub`/`deleteHub` (invalidate `"Hubs"`). Export the hooks.

### 6. ShadCN components to add

`npx shadcn add switch slider select checkbox` — for the availability toggle, Pensum
%, hub/affiliation dropdowns, and collaboration multi-select. Dates use native
`<input type="date">`; collaboration types can also be toggle-chips (Button variants)
if we prefer zero new primitives.

### 7. New components (`features/profile/`, `features/admin/`)

| Component | What it does | Ref |
|---|---|---|
| `AvailabilityEditor.tsx` | "Offen für Anfragen" Switch → date range + Pensum % + collaboration chips (Bachelorarbeit, Innosuisse, Mandat, Forschung, Praktikum) | §6 |
| `AvailabilityBadge.tsx` | Read-only pill "Offen · 60 %, Aug–Okt" for headers | §5.2 |
| `ProfileCompletenessBanner.tsx` | Client-computed progress ("2 von 5"): ≥1 source, availability set, ≥1 tag, summary, hub set | §5.1 |
| `SubscribedTopics.tsx` | Subscribed tag chips + editor (tag picker) | §9 |
| `HubSelector.tsx` | Pick hub(s) from `getHubs`, save via `updateEntityHubs` | §5 |
| `features/admin/HubAdminView.tsx` | Hubs grouped by region; create/edit/delete forms | user ask |

Completeness is computed **client-side** from `EntityDto` (no backend endpoint).

### 8. Wiring + routing

- **`ProfileHeader.tsx`**: add `AvailabilityBadge`, a Zugehörigkeit chip
  (Firma/Selbständig/Privatperson from `affiliationKind`), location + language tags.
- **`OwnProfileView.tsx`**: insert completeness banner (top), `AvailabilityEditor`
  (after header), `HubSelector` + `SubscribedTopics` (new sections). Keep existing
  Kompetenzen / Quellen / Nachweise intact.
- **`PublicProfileView.tsx`**: add read-only `AvailabilityBadge` + hub chips.
- **`routes/index.tsx`**: add `/admin/hubs` under `AuthGuard`; inside the page,
  redirect to `/` unless `useGetMeQuery().data?.isAdmin`. Optionally surface an
  "Admin" nav link only when `isAdmin`.

### Mode-dependent profile (noted simplification)

Entities carry no persisted supply/demand flag today. For this pass the **own
profile always shows the supply editors** (supply is the default; the owner controls
visibility via the `open_to_requests` toggle). Hiding these for a pure demand profile
is a follow-up once a mode flag is persisted on the entity.

---

## Verification

**Backend**
1. `docker compose -f backend/compose.yaml up -d` then `./mvnw spring-boot:run` —
   Flyway applies V6–V8 cleanly; Hibernate `validate` passes (confirms entity
   mappings match the new columns/tables). If validation fails, the array/association
   mappings are the usual culprit.
2. With `VITE_DEV_JWT` / a bearer token: `PUT /api/v1/entities/{id}/availability`,
   `/profile-meta`, `/hubs`, `/subscriptions` round-trip; `GET /api/v1/hubs` works
   **without** a token (whitelist).
3. Admin: set `ORBIT_ADMIN_USER_IDS` to your Clerk sub → `POST/PUT/DELETE
   /api/v1/admin/hubs` succeed; unset it (or use another user) → **403**. `/me`
   returns `isAdmin` accordingly.

**Frontend**
4. `npm run typecheck` (new DTOs wire through) → `npm run lint` → `npm run format`.
5. `npm run dev`: on `/profile` toggle availability, set Pensum/dates/collaboration,
   pick a hub, subscribe to topics → each persists (RTK invalidation refetches).
   Completeness banner advances as fields fill. `AvailabilityBadge` shows on both own
   and public profile.
6. `/admin/hubs`: as an allowlisted user, create/edit/delete a hub and see it appear
   in `HubSelector`; as a non-admin, the route redirects away.

## Out of scope (this pass)

Scoped `hybrid_search` (Stage 3 SQL), re-ranking (§6.3), Anfrage lifecycle +
distribution (§8), contact handoff (§11). This build makes profiles **carry** the
supply signals; consuming them in search/distribution is the next stage.

---

## Postscript — what was actually built (July 2026)

The supply-side build plan above was the design. Here's what was actually implemented:

### Migrations built

| Migration | What it creates | Status |
|-----------|----------------|--------|
| `V6__supply_availability.sql` | Availability columns on `entities` | Built as designed |
| `V7__hubs_regions.sql` | `regions`, `hubs`, `hub_memberships`, `entities.home_region_id` + seed data | Built as designed |
| `V8__tag_subscriptions.sql` | `tag_subscriptions` (entity ↔ tag) | Built as designed |
| `V9__profile_mode.sql` | `entities.profile_mode` column (`supply` / `demand`) | Built (see `profile-mode.md`) |
| `V10__scoped_hybrid_search.sql` | Replaces V3 with scoped `hybrid_search` (English FTS, scope hard filter) | Built (see `search-pipeline-implementation.md`) |

### Profile mode (V9) — the follow-up that was done

The plan noted: *"Hiding these for a pure demand profile is a follow-up once a mode flag is persisted on the entity."* This was implemented as V9. See `profile-mode.md` for the full record.

### Admin gate — env allowlist (as designed)

`AdminAccess.isAdmin(userId)` checks against `orbit.admin.user-ids` (CSV of Clerk subs). `MeController` returns `isAdmin` in the `/me` payload. `AdminHubController` does the allowlist check and returns 403 for non-admins. No Spring authorities needed (JWT converter returns empty authorities).

### What diverged from the plan

- **JPA associations**: The plan assumed `@ManyToOne Region` for `home_region_id`. The built system uses **plain `Long` FKs** everywhere (no JPA associations), resolved via repository calls. This avoids Hibernate lazy-loading issues.
- **EntityDto assembly**: The plan said to update `toDto()` in `EntityController`. The built system moved DTO assembly to `EntityService.toDto()` (thin controllers pattern — controllers never build DTOs).
- **Availability as columns**: Built exactly as planned (ALTER TABLE, not a child table).
- **Onboarding step**: The plan said "no new onboarding step". The built system added an **availability + subscriptions step** to the onboarding wizard's `IngestionRunView` (post-ingestion completion screen), because `entityId` only exists after ingestion completes. This was an addition, not a contradiction — the wizard's step machine was extended, not changed.
