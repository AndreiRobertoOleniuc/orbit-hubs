package com.orbit.app.ingestion.job;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.orbit.testsupport.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** The queue's ordering and recovery rules, on the real V20 schema. The worker is off. */
@SpringBootTest(properties = {
        "orbit.ingestion.jobs.enabled=false"
})
class IngestionJobStoreIntegrationTest extends PostgresIntegrationTest {

    private static final String REF = "{\"url\":\"x\"}";

    @Autowired
    private IngestionJobStore store;

    @Autowired
    private JdbcTemplate jdbc;

    private long adaEntity;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE ingestion_jobs RESTART IDENTITY");
        for (String user : List.of("ada", "bob")) {
            jdbc.update("INSERT INTO users (id, email, display_name) VALUES (?, ?, ?) ON CONFLICT (id) DO NOTHING",
                    user, user + "@orbit.test", user);
        }
        adaEntity = jdbc.queryForObject(
                "INSERT INTO entities (entity_type, display_name) VALUES ('person', 'Ada') RETURNING id", Long.class);
    }

    @Test
    void oneOwnersJobsRunOneAtATimeInOrderWhileOtherOwnersProceed() {
        long adaFirst = store.enqueueSource("ada", "zefix", REF, null);
        long adaSecond = store.enqueueSource("ada", "company_site", REF, null);
        long bobs = store.enqueueSource("bob", "github", REF, null);

        assertThat(claim()).contains(adaFirst);
        assertThat(claim()).contains(bobs);         // Ada's second waits for her first
        assertThat(claim()).isEmpty();

        store.succeed(adaFirst, adaEntity, "{}");
        assertThat(claim()).contains(adaSecond);
    }

    @Test
    void enrichmentWaitsForEveryQueuedSourceOfTheOwnerAndRunsOnce() {
        long first = store.enqueueSource("ada", "github", REF, null);
        long second = store.enqueueSource("ada", "orcid_pub", REF, null);
        assertThat(claim()).contains(first);
        store.succeed(first, adaEntity, "{}");
        store.armEnrichment("ada", adaEntity, Duration.ZERO);

        assertThat(claim()).contains(second);       // the source goes first, though queued later
        assertThat(claim()).isEmpty();              // enrichment waits while it runs
        store.succeed(second, adaEntity, "{}");
        store.armEnrichment("ada", adaEntity, Duration.ZERO);   // re-arms the same job

        assertThat(jdbc.queryForObject("SELECT count(*) FROM ingestion_jobs WHERE kind = 'enrich'", Integer.class))
                .isEqualTo(1);
        assertThat(store.enrichmentPending("ada")).isTrue();
        Optional<IngestionJob> enrich = store.claimNext(2);
        assertThat(enrich).get().extracting(IngestionJob::kind, IngestionJob::entityId)
                .containsExactly(IngestionJob.ENRICH, adaEntity);
    }

    @Test
    void anEnrichmentArmedWithADelayIsNotClaimedEarly() {
        store.armEnrichment("ada", adaEntity, Duration.ofMinutes(5));

        assertThat(claim()).isEmpty();
    }

    @Test
    void atMostTheConfiguredNumberOfJobsPerSourceTypeRun() {
        long ada = store.enqueueSource("ada", "github", REF, null);
        store.enqueueSource("bob", "github", REF, null);

        assertThat(store.claimNext(1)).get().extracting(IngestionJob::id).isEqualTo(ada);
        assertThat(store.claimNext(1)).isEmpty();
        assertThat(store.claimNext(2)).isPresent();
    }

    @Test
    void aRetriedJobIsClaimedAgainAfterItsBackoffWithTheUploadKept() {
        long id = store.enqueueSource("ada", "cv_pdf", REF, new byte[]{1, 2, 3});
        assertThat(claim()).contains(id);

        store.retryLater(id, "InferenceUnavailableException: down", Duration.ofMinutes(5));
        assertThat(claim()).isEmpty();

        store.retryLater(id, "InferenceUnavailableException: down", Duration.ZERO);
        IngestionJob retried = store.claimNext(2).orElseThrow();
        assertThat(retried.attempts()).isEqualTo(2);
        assertThat(retried.payload()).containsExactly(1, 2, 3);
        assertThat(retried.error()).contains("down");
    }

    @Test
    void finishingAJobDropsItsUpload() {
        long id = store.enqueueSource("ada", "cv_pdf", REF, new byte[]{1});
        claim();
        store.fail(id, "IllegalArgumentException: not a PDF");

        assertThat(jdbc.queryForObject("SELECT payload IS NULL FROM ingestion_jobs WHERE id = ?", Boolean.class, id))
                .isTrue();
        assertThat(store.findOwned("ada", List.of(id))).singleElement().satisfies(job -> {
            assertThat(job.status()).isEqualTo(IngestionJob.FAILED);
            assertThat(job.finished()).isTrue();
            assertThat(job.finishedAt()).isNotNull();
        });
    }

    @Test
    void aJobWhoseWorkerStoppedIsQueuedAgainUntilItRunsOutOfAttempts() {
        long id = store.enqueueSource("ada", "github", REF, null);
        claim();
        jdbc.update("UPDATE ingestion_jobs SET locked_at = now() - interval '10 minutes' WHERE id = ?", id);

        assertThat(store.recoverStale(Duration.ofMinutes(2))).isEqualTo(1);
        assertThat(claim()).contains(id);

        jdbc.update("UPDATE ingestion_jobs SET attempts = max_attempts, locked_at = now() - interval '10 minutes'");
        store.recoverStale(Duration.ofMinutes(2));
        assertThat(store.findOwned("ada", List.of(id)).getFirst().status()).isEqualTo(IngestionJob.FAILED);
    }

    @Test
    void ownersSeeOnlyTheirOwnJobs() {
        long ada = store.enqueueSource("ada", "github", REF, null);
        long bob = store.enqueueSource("bob", "github", REF, null);

        assertThat(store.findOwned("ada", List.of(ada, bob))).extracting(IngestionJob::id).containsExactly(ada);
    }

    @Test
    void oldFinishedJobsArePurged() {
        long id = store.enqueueSource("ada", "github", REF, null);
        claim();
        store.succeed(id, adaEntity, "{}");
        jdbc.update("UPDATE ingestion_jobs SET finished_at = now() - interval '8 days'");

        assertThat(store.purgeFinished(Duration.ofDays(7))).isEqualTo(1);
    }

    @Test
    void aReindexQueuesOneResyncPerEntityWithASourceAndNeverTwice() {
        long bare = entity("No sources", null);
        long owned = entity("Owned", "bob");
        long seeded = entity("Seeded", null);
        for (long id : List.of(owned, seeded)) {
            jdbc.update("INSERT INTO sources (entity_id, source_type, source_url) VALUES (?, 'github', ?)",
                    id, "https://github.com/x/" + id);
        }

        assertThat(store.enqueueResyncs(null)).isEqualTo(2);
        assertThat(store.enqueueResyncs(List.of())).isZero();          // already queued
        assertThat(jdbc.queryForList(
                "SELECT entity_id FROM ingestion_jobs WHERE kind = 'resync' ORDER BY entity_id", Long.class))
                .containsExactly(owned, seeded).doesNotContain(bare);
        assertThat(jdbc.queryForObject(
                "SELECT owner_id FROM ingestion_jobs WHERE entity_id = ?", String.class, owned)).isEqualTo("bob");
        assertThat(store.resyncCounts()).containsEntry("queued", 2L);

        long first = claim().orElseThrow();
        store.succeed(first, null, "{}");
        assertThat(store.resyncCounts()).containsEntry("succeeded", 1L).containsEntry("queued", 1L);
        assertThat(store.enqueueResyncs(List.of(owned, seeded, bare))).isEqualTo(1);   // only the finished one again
    }

    private long entity(String name, String ownerId) {
        return jdbc.queryForObject(
                "INSERT INTO entities (entity_type, display_name, owner_id) VALUES ('person', ?, ?) RETURNING id",
                Long.class, name, ownerId);
    }

    private Optional<Long> claim() {
        return store.claimNext(2).map(IngestionJob::id);
    }
}
