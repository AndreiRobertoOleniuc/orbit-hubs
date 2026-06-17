package com.orbit.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.orbit.domain.entity.Document;
import com.orbit.domain.entity.Entity;
import com.orbit.domain.entity.Request;
import com.orbit.domain.repository.DocumentRepository;
import com.orbit.domain.repository.EntityRepository;
import com.orbit.domain.repository.RequestRepository;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence-layer smoke test against the live pgvector schema. Exercises the two mechanics the
 * (not-yet-built) discovery search and request-lifecycle endpoints depend on:
 *
 * <ul>
 *   <li>the {@code float[]} ↔ {@code vector(1024)} round-trip used by hybrid retrieval, and</li>
 *   <li>a request lifecycle transition persisting its audit child via cascade.</li>
 * </ul>
 *
 * {@code @Transactional} rolls everything back, so the test leaves no rows behind.
 */
@SpringBootTest
@Transactional
class JpaPersistenceSmokeTest {

    @Autowired
    private EntityRepository entities;

    @Autowired
    private DocumentRepository documents;

    @Autowired
    private RequestRepository requests;

    @Autowired
    private EntityManager em;

    @Test
    void documentEmbeddingRoundTripsThroughPgvector() {
        Entity person = new Entity("per-smoke-test", "person", "Smoke Tester");
        person.setScope("ch");
        entities.save(person);

        Document doc = new Document("doc-smoke-test", person);
        doc.setText("embedded firmware security review");
        float[] embedding = new float[1024];
        embedding[0] = 0.5f;
        embedding[1023] = -0.25f;
        doc.setEmbedding(embedding);
        documents.save(doc);

        em.flush();
        em.clear();

        Document reloaded = documents.findById("doc-smoke-test").orElseThrow();
        assertThat(reloaded.getEmbedding()).hasSize(1024);
        assertThat(reloaded.getEmbedding()[0]).isEqualTo(0.5f);
        assertThat(reloaded.getEmbedding()[1023]).isEqualTo(-0.25f);
        assertThat(reloaded.getEntity().getId()).isEqualTo("per-smoke-test");
    }

    @Test
    void requestLifecycleTransitionPersistsAuditChild() {
        Request request = new Request(
                "req-smoke-test", "Looking for embedded engineer",
                "Need help with a firmware security review.", "ch", "OPEN");
        request.addTransition("OPEN", "DISTRIBUTED", null, "matched 3 profiles");
        requests.save(request);

        em.flush();
        em.clear();

        Request reloaded = requests.findById("req-smoke-test").orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo("DISTRIBUTED");
        assertThat(reloaded.getTransitions()).hasSize(1);
        assertThat(reloaded.getTransitions().get(0).getToStatus()).isEqualTo("DISTRIBUTED");
        assertThat(reloaded.getTransitions().get(0).getId()).isNotNull(); // bigserial assigned
    }
}
