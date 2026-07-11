-- Orbit — Profile mode (Supply / Demand)
-- Reference: Orbit_Specv2.md §5.1 (Profil ist modus-abhängig)
--
-- Every actor is simultaneously demand and supply; the profile is the standing
-- Angebot. The mode is set by the onboarding fork (be-found → supply, search →
-- demand) and switchable on the profile. In demand mode the own profile hides
-- the completeness banner, the availability editor, and subscribed topics.
-- Default 'supply' keeps every existing profile showing the full view.

ALTER TABLE entities
  ADD COLUMN profile_mode text NOT NULL DEFAULT 'supply';   -- 'supply' | 'demand'
