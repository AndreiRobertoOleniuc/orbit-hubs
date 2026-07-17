-- Stage 6: Distribution — who was notified about a request, why, and at
-- which widening level. Distribution is rule-based on tags + scope (§8.2):
-- push must be explainable ("Verteilt, weil dein Profil die Tags … trägt"),
-- so it matches on request_tags ∩ entity_tags and tag_subscriptions, never
-- on embeddings.

CREATE TABLE request_distributions (
    id             bigint PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    request_id     bigint NOT NULL REFERENCES requests(id) ON DELETE CASCADE,
    entity_id      bigint NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    reason         text NOT NULL,                    -- 'profile_match' | 'subscription'
    matched_tags   text[],                           -- tag labels: request_tags ∩ entity_tags
    widening_level text NOT NULL,                    -- effective scope kind at notify time: 'hub' | 'region' | 'all'
    notified_at    timestamptz NOT NULL DEFAULT now(),
    created_at     timestamptz NOT NULL DEFAULT now(),
    UNIQUE (request_id, entity_id)
);
CREATE INDEX idx_request_distributions_entity_id  ON request_distributions (entity_id);
CREATE INDEX idx_request_distributions_request_id ON request_distributions (request_id);

-- Escalation state (§8.3). The original scope_kind/scope_hub_id/scope_region_id
-- stay untouched for display; the effective scope is the original scope widened
-- escalation_level times (hub → its region → parent region → all).
ALTER TABLE requests
    ADD COLUMN escalation_level int NOT NULL DEFAULT 0,      -- widening steps applied so far
    ADD COLUMN last_wave_at     timestamptz,                 -- last distribution wave
    ADD COLUMN reminder_sent    boolean NOT NULL DEFAULT false; -- 'soon' one-shot reminder guard
