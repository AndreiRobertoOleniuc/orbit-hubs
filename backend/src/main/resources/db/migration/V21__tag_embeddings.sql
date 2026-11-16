-- Orbit — tag vocabulary: one embedding per tag label
--
-- Derived tags were minted by slug alone, so translations and variants of one
-- topic became separate tags ("Innovationsmanagement" / "Innovation Management",
-- "Life Cycle Assessment" / "Life Cycle Assessments"): 509 tags in use on 56
-- profiles, only 35 of them shared. Request distribution matches on tag overlap,
-- so it rarely reached a second profile.
--
-- TagVocabularyService now maps a new label onto an existing tag before minting:
-- by a normalised spelling key, or by embedding similarity confirmed by the LLM.
-- The vectors live here; the service embeds tags that have none (or another
-- model's) on start-up and after each enrichment. The vocabulary is small, so
-- the nearest-tag lookup is an exact scan and needs no vector index.

ALTER TABLE tags
  ADD COLUMN embedding       vector(1024),
  ADD COLUMN embedding_model text,
  ADD COLUMN embedded_at     timestamptz;
