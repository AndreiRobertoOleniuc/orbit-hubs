-- Stage 4: Request lifecycle — the demand-side request entity.
-- A request is created when search doesn't find a match (sufficient=false).
-- It appears on the public board (status='open'), gets distributed to matching
-- profiles (status='distributed'), receives contact requests (status='contacted'),
-- and is eventually closed (status='closed'). See reference doc §8.

CREATE TABLE requests (
    id              bigint PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    created_by      text NOT NULL,                    -- → users.id (Clerk sub)
    title           text,
    description     text,
    query_text      text NOT NULL,
    scope_kind      text NOT NULL DEFAULT 'all',       -- 'all' | 'hub' | 'region'
    scope_hub_id    bigint REFERENCES hubs(id),
    scope_region_id bigint REFERENCES regions(id),
    urgency         text NOT NULL DEFAULT 'flexible', -- 'flexible' | 'soon' | 'urgent'
    deadline        date,
    status          text NOT NULL DEFAULT 'open',      -- 'open' | 'distributed' | 'contacted' | 'closed'
    distributed_at  timestamptz,
    want_from       date,
    want_to         date,
    want_pensum_pct int,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_requests_created_by ON requests (created_by);
CREATE INDEX idx_requests_status     ON requests (status);

-- Tags carried by a request — steer distribution (§8.2).
-- A request is distributed to entities whose entity_tags or tag_subscriptions
-- overlap with these tags.
CREATE TABLE request_tags (
    request_id bigint NOT NULL REFERENCES requests(id) ON DELETE CASCADE,
    tag_id     bigint NOT NULL REFERENCES tags(id),
    PRIMARY KEY (request_id, tag_id)
);
CREATE INDEX idx_request_tags_tag_id ON request_tags (tag_id);