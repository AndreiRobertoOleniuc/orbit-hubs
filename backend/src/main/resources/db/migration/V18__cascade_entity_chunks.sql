-- Chunks already disappear through their source, but make the denormalized
-- entity_id foreign key explicitly cascade as well. This guarantees that an
-- owner-requested entity deletion cannot be blocked by searchable chunks.
ALTER TABLE chunks
    DROP CONSTRAINT chunks_entity_id_fkey,
    ADD CONSTRAINT chunks_entity_id_fkey
        FOREIGN KEY (entity_id) REFERENCES entities(id) ON DELETE CASCADE;
