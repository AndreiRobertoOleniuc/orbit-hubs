-- Stage 8: Contact handoff (§11) — the platform's role ends at first contact.
-- A contact request is a one-time handoff, not an inbox thread: the recipient
-- sees the sender's context + email in their Postfach and continues off-platform.
-- sender_context is a snapshot frozen at send time so later profile edits
-- don't rewrite history (§11.2).

CREATE TABLE contact_requests (
    id             bigint PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    from_entity_id bigint NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    to_entity_id   bigint NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    request_id     bigint REFERENCES requests(id) ON DELETE SET NULL,  -- set when initiated from a board request
    message        text NOT NULL,
    sender_context jsonb NOT NULL DEFAULT '{}',  -- frozen {name, role, org, description, searched_text}
    created_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_contact_requests_to_entity_id ON contact_requests (to_entity_id);
