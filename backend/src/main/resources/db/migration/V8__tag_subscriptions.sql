-- Orbit — Tag subscriptions ("Abonnierte Themen")
-- Reference: orbit-discovery-engine-reference.md §9 (Tags, skills & subscriptions)
--
-- entity↔tag: a subscriber is notified when a matching Anfrage is distributed,
-- even if their profile wasn't a direct skill match (§8.2 bySubs). Subscriptions
-- hang off the claimed entity (the person's standing profile), joining the same
-- controlled `tags` vocabulary used for Kompetenzen and distribution.

CREATE TABLE tag_subscriptions (
  entity_id   bigint not null references entities(id) on delete cascade,
  tag_id      bigint not null references tags(id) on delete cascade,
  created_at  timestamptz not null default now(),
  primary key (entity_id, tag_id)
);
CREATE INDEX ON tag_subscriptions (tag_id);
CREATE INDEX ON tag_subscriptions (entity_id);
