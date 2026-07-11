-- Orbit — Supply-side profile: structured availability + affiliation
-- Reference: orbit-discovery-engine-reference.md §6 (availability), §10 (affiliation)
--
-- These columns turn a shown profile into a rankable one: structured
-- availability drives re-ranking (availabilityOverlap, §6.3), affiliation_kind
-- gives every person a shown origin (Firma / Selbständig / Privatperson, §10),
-- and languages steer cross-language matching. All live directly on the entity
-- (fixed-cardinality, 1:1) rather than a child table.

ALTER TABLE entities
  ADD COLUMN open_to_requests       boolean not null default false,   -- "Offen für Anfragen" toggle (§4.2)
  ADD COLUMN available_from         date,                             -- availability window start
  ADD COLUMN available_to           date,                             -- availability window end
  ADD COLUMN pensum_pct             int,                              -- capacity 0–100 (%)
  ADD COLUMN availability_note      text,                             -- free-text ("ab Studienende")
  ADD COLUMN preferred_collaboration text[],                         -- Bachelorarbeit | Innosuisse | Mandat | …
  ADD COLUMN languages              text[],                           -- DE | EN | FR working languages
  ADD COLUMN affiliation_kind       text,                             -- 'company' | 'self_employed' | 'private'
  ADD COLUMN location_label         text;                             -- human location ("Baden, AG")
