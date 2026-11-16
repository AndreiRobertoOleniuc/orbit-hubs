package com.orbit.app.search;

import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;

import com.orbit.app.embedding.EmbeddingKind;
import com.orbit.app.embedding.EmbeddingService;
import com.orbit.app.inference.InferenceUnavailableException;
import com.orbit.domain.entity.DiscoveryEntity;
import com.orbit.domain.repository.DiscoveryEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileDocumentServiceTest {

    private DiscoveryEntityRepository entities;
    private EmbeddingService embeddings;
    private JdbcTemplate jdbc;
    private ProfileDocumentService service;

    /** The name of the embedding model in use, as stored with each vector. */
    private static final String MODEL = "test-model";

    @BeforeEach
    void setUp() {
        entities = mock(DiscoveryEntityRepository.class);
        embeddings = mock(EmbeddingService.class);
        when(embeddings.modelName()).thenReturn(MODEL);
        jdbc = mock(JdbcTemplate.class);
        // Refreshes run inline so the test can observe them.
        service = new ProfileDocumentService(entities, embeddings, jdbc, false, Runnable::run);
    }

    @Test
    void theDocumentJoinsNameHeadlineOrganisationSummaryAndTagsOnePerLine() {
        DiscoveryEntity entity = entity(7L, "Ada Muster", "Robotik · Sicherheit");
        entity.setOrgName("ETH Zürich");
        entity.setSummary("Ada  forscht an\nsicherer Robotik.");

        assertThat(ProfileDocumentService.buildContent(entity, List.of("Robotics", "Security")))
                .isEqualTo("Ada Muster\nRobotik · Sicherheit\nETH Zürich\nAda forscht an sicherer Robotik.\nRobotics, Security");
        assertThat(ProfileDocumentService.buildContent(entity(8L, "Bob", null), List.of())).isEqualTo("Bob");

        entity.setSummary("x".repeat(5000));
        assertThat(ProfileDocumentService.buildContent(entity, List.of()))
                .hasSize(ProfileDocumentService.MAX_CONTENT_CHARS);
    }

    @Test
    void aNewDocumentIsEmbeddedAsAPassageAndUpserted() {
        when(entities.findById(7L)).thenReturn(Optional.of(entity(7L, "Ada", "Robotik")));
        when(embeddings.embed("Ada\nRobotik", EmbeddingKind.PASSAGE)).thenReturn(new float[]{0.5f, -1.0f});

        assertThat(service.refresh(7L)).isTrue();

        verify(jdbc).update(contains("INSERT INTO profile_documents"),
                eq(7L), eq("Ada\nRobotik"), eq("[0.5,-1.0]"), eq(MODEL));
    }

    @Test
    void anUnchangedDocumentIsNotEmbeddedAgain() throws Exception {
        when(entities.findById(7L)).thenReturn(Optional.of(entity(7L, "Ada", "Robotik")));
        storedDocument(7L, "Ada\nRobotik", MODEL);

        assertThat(service.refresh(7L)).isFalse();

        verify(embeddings, never()).embed(anyString(), any());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void aDocumentEmbeddedByAnotherModelIsEmbeddedAgain() throws Exception {
        when(entities.findById(7L)).thenReturn(Optional.of(entity(7L, "Ada", "Robotik")));
        when(embeddings.embed(anyString(), eq(EmbeddingKind.PASSAGE))).thenReturn(new float[]{0.1f});
        storedDocument(7L, "Ada\nRobotik", "some-older-model");

        assertThat(service.refresh(7L)).isTrue();
    }

    @Test
    void refreshOutsideATransactionRunsRightAwayAndSwallowsFailures() {
        when(entities.findById(7L)).thenReturn(Optional.of(entity(7L, "Ada", "Robotik")));
        when(embeddings.embed(anyString(), any())).thenThrow(new InferenceUnavailableException("down"));

        service.refreshAfterCommit(7L);   // must not throw into a profile edit

        verify(embeddings).embed("Ada\nRobotik", EmbeddingKind.PASSAGE);
        verify(jdbc, never()).update(contains("INSERT INTO profile_documents"), any(Object[].class));
    }

    @Test
    void syncEmbedsEveryStaleEntityAndContinuesPastAFailure() {
        when(entities.findAll()).thenReturn(List.of(entity(7L, "Ada", "Robotik"), entity(8L, "Bob", "Recht")));
        when(embeddings.embed("Ada\nRobotik", EmbeddingKind.PASSAGE)).thenThrow(new InferenceUnavailableException("down"));
        when(embeddings.embed("Bob\nRecht", EmbeddingKind.PASSAGE)).thenReturn(new float[]{0.1f});

        assertThat(service.syncAll()).isEqualTo(1);

        verify(jdbc).update(contains("INSERT INTO profile_documents"),
                eq(8L), eq("Bob\nRecht"), eq("[0.1]"), eq(MODEL));
    }

    /** Make the stored-document lookup return one row. */
    private void storedDocument(long entityId, String content, String model) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getLong("entity_id")).thenReturn(entityId);
        when(row.getString("content")).thenReturn(content);
        when(row.getString("embedding_model")).thenReturn(model);
        when(row.getBoolean("embedded")).thenReturn(true);
        doAnswer(invocation -> {
            invocation.<RowCallbackHandler>getArgument(1).processRow(row);
            return null;
        }).when(jdbc).query(contains("FROM profile_documents"), any(RowCallbackHandler.class), any(Object[].class));
    }

    private static DiscoveryEntity entity(long id, String name, String headline) {
        DiscoveryEntity entity = new DiscoveryEntity("person", name, headline, null);
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
