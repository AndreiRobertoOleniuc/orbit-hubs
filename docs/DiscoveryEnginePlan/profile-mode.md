# Profile Mode — Supply / Demand (V9)

## Context

Orbit is asymmetric: every actor is simultaneously demand and supply (spec §2.1). The platform has two modes a user can switch between:

- **Supply** ("Ich biete") — the profile is the standing Angebot. The user sees availability editors, subscribed topics, completeness banner, and hub selector.
- **Demand** ("Ich suche") — the user is actively searching. The own profile hides supply-specific UI elements because the user's primary intent is finding others, not being found.

Before V9, the own profile always showed the full supply view regardless of the user's intent. The supply-side build plan noted this as a follow-up: *"Hiding these for a pure demand profile is a follow-up once a mode flag is persisted on the entity."*

## Conceptual rationale

The mode flag exists for **UI coherence, not data segregation**. The underlying entity is the same — it still has availability, tags, sources, and hub memberships. The mode only controls what the own-profile view renders:

- **Supply mode**: show `ProfileCompletenessBanner`, `AvailabilityEditor`, `SubscribedTopics`. These are the tools a person uses to manage their discoverability.
- **Demand mode**: hide those sections. The user's focus is on searching and posting Anfragen, not on curating their profile. The profile still exists and is still searchable — it just doesn't need the user's active management right now.

The mode is **switchable at any time** via the `ModeSwitch` toggle in the app header ("Ich suche" / "Ich bietee"). This matches the spec's insistence that the split is between two *modes every actor can take*, not two *user types*.

## How it was built

### Migration (V9)

```sql
-- V9__profile_mode.sql
ALTER TABLE entities
  ADD COLUMN profile_mode text NOT NULL DEFAULT 'supply';   -- 'supply' | 'demand'
```

Default `supply` keeps every existing profile showing the full view — no data migration needed.

### Backend

- **Entity**: `DiscoveryEntity.profileMode` (String, `"supply"` | `"demand"`).
- **DTO**: `EntityDto.profileMode` included in the read model.
- **Endpoint**: `PATCH /api/v1/entities/{id}/mode` with body `{"mode": "supply" | "demand"}`. Owner-guarded (403 if not the entity owner, 400 if mode is invalid).
- **Service**: `EntityService.updateEntityMode()` — validates the mode value, persists, returns the updated DTO.

### Frontend

- **`useProfileMode()` hook** (`features/profile/useProfileMode.ts`) — reads mode from the user's entity via `useGetMyEntitiesQuery`. Single source of truth is `entity.profileMode` (persisted).
- **`ModeSwitch` component** in `AppLayout` header — "Ich suche" / "Ich biete" toggle. Calls `updateEntityMode` mutation, which invalidates the `Entity` + `MyEntities` cache tags for auto-refresh.
- **`NavLinks` is mode-dependent**: demand shows "Suche" + "Meine Anfragen"; supply shows "Anfrage-Board" + "Postfach".
- **`OwnProfileView` conditional rendering**: demand mode hides `ProfileCompletenessBanner`, `AvailabilityEditor`, and `SubscribedTopics`. The `ProfileHeader`, Kompetenzen, Quellen, and Nachweise sections remain visible in both modes.

### Onboarding fork

The mode is set during onboarding:
- User picks "Ich möchte gefunden werden" (supply) → `profileMode = "supply"` → full profile view with availability + subscriptions step.
- User picks "Ich suche jemanden" (demand) → `profileMode = "demand"` → profile view without supply editors. The user can still complete a basic profile (sources, tags) but doesn't see availability or subscription management.

The onboarding wizard uses a `useRef` guard to set the mode once after the fork, preventing double-dispatch in React StrictMode.

## Files

| File | Role |
|------|------|
| `db/migration/V9__profile_mode.sql` | Schema: add `profile_mode` column |
| `domain/entity/DiscoveryEntity.java` | JPA: `profileMode` field |
| `app/profile/EntityService.java` | Service: `updateEntityMode()` |
| `app/controllers/EntityController.java` | Endpoint: `PATCH /entities/{id}/mode` |
| `app/controllers/dto/EntityDto.java` | DTO: `profileMode` field |
| `features/profile/useProfileMode.ts` | Hook: read mode from entity |
| `components/layout/AppLayout.tsx` | `ModeSwitch` in header |
| `features/profile/OwnProfileView.tsx` | Conditional rendering by mode |
| `lib/api.ts` | RTK Query: `updateEntityMode` mutation |

## Verification

1. Onboard as supply → profile shows completeness banner, availability editor, subscribed topics.
2. Toggle to demand in the header → those sections disappear; nav links switch to "Suche" + "Meine Anfragen".
3. Toggle back to supply → sections reappear (cache invalidation auto-refreshes).
4. `PATCH /api/v1/entities/{id}/mode` with `{"mode": "demand"}` → 200 + updated DTO; with `{"mode": "invalid"}` → 400; from non-owner → 403.