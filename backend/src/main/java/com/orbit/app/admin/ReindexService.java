package com.orbit.app.admin;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.orbit.app.auth.CurrentUser;
import com.orbit.app.controllers.dto.ReindexDto;
import com.orbit.app.controllers.dto.ReindexRequest;
import com.orbit.app.ingestion.job.IngestionJobStore;

/**
 * Re-evaluates stored profiles with the current ingestion pipeline — the
 * migration path after a change to how sources are fetched, extracted or
 * tagged, for every profile at once instead of each owner pressing
 * "Quellen neu auswerten".
 * <p>
 * It queues one ordinary {@code resync} job per entity, so the work is paced,
 * retried and resumed by the ingestion worker like any other job:
 * <ul>
 *   <li>GitHub, OpenAlex, Zefix and website sources are fetched again. A source
 *       whose content hash differs is extracted and embedded anew (in place —
 *       its id and its active flag are kept); an unchanged one is skipped.</li>
 *   <li>Uploads (LinkedIn/CV PDF) and manual text cannot be fetched again and
 *       keep their chunks. Their vectors are refreshed separately, by
 *       {@code orbit.embedding.reembed-before}.</li>
 *   <li>The entity is then enriched: summary and derived tags are regenerated
 *       (user-confirmed tags are kept), and its profile document re-indexed.</li>
 * </ul>
 * The regenerated summary replaces a summary the owner edited by hand, exactly
 * as the owner's own resync does — pass explicit {@code entityIds} to leave
 * such profiles out.
 */
@Service
public class ReindexService {

    private static final Logger log = LoggerFactory.getLogger(ReindexService.class);

    private final IngestionJobStore jobs;
    private final AdminAccess adminAccess;
    private final CurrentUser currentUser;

    public ReindexService(IngestionJobStore jobs, AdminAccess adminAccess, CurrentUser currentUser) {
        this.jobs = jobs;
        this.adminAccess = adminAccess;
        this.currentUser = currentUser;
    }

    /**
     * Queue a resync of the given entities, or of every entity with a source.
     * An entity whose resync is already queued or running is not queued twice.
     */
    public ReindexDto reindex(ReindexRequest request) {
        requireAdmin();
        List<Long> entityIds = request == null ? null : request.entityIds();
        int queued = jobs.enqueueResyncs(entityIds);
        log.info("Reindex: {} resync job(s) queued by admin {} ({})", queued,
                currentUser.userId().orElse(null),
                entityIds == null || entityIds.isEmpty() ? "all entities" : entityIds.size() + " selected");
        return status(queued);
    }

    /** How far the queued re-evaluation has come. */
    public ReindexDto status() {
        requireAdmin();
        return status(0);
    }

    private ReindexDto status(int queued) {
        Map<String, Long> counts = jobs.resyncCounts();
        return new ReindexDto(queued,
                counts.getOrDefault("queued", 0L), counts.getOrDefault("running", 0L),
                counts.getOrDefault("succeeded", 0L), counts.getOrDefault("failed", 0L));
    }

    private void requireAdmin() {
        if (!adminAccess.isAdmin(currentUser.userId().orElse(null))) {
            throw new AdminAccessDeniedException();
        }
    }
}
