-- Orbit — entity ownership
--
-- Links each entity to the authenticated user who created it via the ingestion
-- pipeline. Nullable for backward compatibility with entities created before
-- ownership tracking (admin/test ingestions). The foreign key to users(id)
-- is safe because UserProvisioningFilter provisions the user row just-in-time
-- on every authenticated request, before any controller logic runs.

ALTER TABLE entities ADD COLUMN owner_id text REFERENCES users(id);

CREATE INDEX idx_entities_owner_id ON entities (owner_id);