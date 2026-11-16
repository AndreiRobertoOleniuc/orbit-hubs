-- Ingestion runs as background jobs: the API queues a job and returns at once,
-- a worker claims jobs with FOR UPDATE SKIP LOCKED and retries transient
-- failures with backoff. See IngestionJobStore / IngestionJobWorker.
--
-- kind:
--   source  — ingest one submitted source (source_ref, plus payload for an upload)
--   resync  — re-ingest every source of entity_id
--   enrich  — regenerate summary + tags of entity_id once the owner's queued
--             sources are all done (one per entity while queued)
CREATE TABLE ingestion_jobs (
    id              bigserial PRIMARY KEY,
    owner_id        text REFERENCES users(id) ON DELETE CASCADE,
    kind            text NOT NULL CHECK (kind IN ('source', 'resync', 'enrich')),
    source_type     text,
    source_ref      jsonb,
    -- An uploaded PDF, kept only until the job has finished (then set to NULL).
    payload         bytea,
    entity_id       bigint REFERENCES entities(id) ON DELETE CASCADE,
    status          text NOT NULL DEFAULT 'queued'
                    CHECK (status IN ('queued', 'running', 'succeeded', 'failed')),
    attempts        integer NOT NULL DEFAULT 0,
    max_attempts    integer NOT NULL DEFAULT 3,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    -- Heartbeat of the worker running it; a running job whose heartbeat stops
    -- (the process died) is queued again.
    locked_at       timestamptz,
    result          jsonb,
    error           text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    finished_at     timestamptz,
    CHECK (kind <> 'source' OR source_ref IS NOT NULL),
    CHECK (kind = 'source' OR entity_id IS NOT NULL)
);

CREATE INDEX ingestion_jobs_claimable ON ingestion_jobs (id) WHERE status = 'queued';
CREATE INDEX ingestion_jobs_running ON ingestion_jobs (locked_at) WHERE status = 'running';
CREATE INDEX ingestion_jobs_owner ON ingestion_jobs (owner_id, id);
CREATE INDEX ingestion_jobs_finished ON ingestion_jobs (finished_at) WHERE finished_at IS NOT NULL;

-- At most one queued enrichment per entity: a finished source re-arms it instead.
CREATE UNIQUE INDEX ingestion_jobs_one_queued_enrich
    ON ingestion_jobs (entity_id) WHERE kind = 'enrich' AND status = 'queued';
