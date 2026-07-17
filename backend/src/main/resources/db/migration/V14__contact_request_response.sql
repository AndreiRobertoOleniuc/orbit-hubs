-- Stage 8 refinement: the contact handshake (§11, evolved).
-- The one-shot handoff becomes a structured handshake: the recipient responds
-- exactly once — 'accepted' (with a reply message) or 'declined'. Emails are
-- revealed to both sides only on acceptance (mutual opt-in). Still not a thread:
-- everything after the single response runs off-platform.

ALTER TABLE contact_requests
    ADD COLUMN status       text NOT NULL DEFAULT 'pending',  -- 'pending' | 'accepted' | 'declined'
    ADD COLUMN reply_message text,                            -- recipient's reply (accepted only)
    ADD COLUMN responded_at timestamptz;

-- The sent-list query (Postfach "Gesendet") reads by sender.
CREATE INDEX idx_contact_requests_from_entity_id ON contact_requests (from_entity_id);
