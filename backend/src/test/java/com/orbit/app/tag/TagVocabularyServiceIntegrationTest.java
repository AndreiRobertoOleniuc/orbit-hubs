package com.orbit.app.tag;

import java.util.List;

import com.orbit.app.embedding.EmbeddingKind;
import com.orbit.app.embedding.EmbeddingService;
import com.orbit.app.embedding.VectorLiteral;
import com.orbit.app.inference.InferenceUnavailableException;
import com.orbit.app.llm.LlmSummaryService;
import com.orbit.app.llm.dto.TopicPair;
import com.orbit.domain.entity.Tag;
import com.orbit.domain.repository.TagRepository;
import com.orbit.testsupport.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tag canonicalisation on the real V21 schema, with the embedding model and the LLM stubbed. */
@SpringBootTest(properties = {
        "orbit.ingestion.jobs.enabled=false",
        "orbit.tags.sync-on-startup=false",
        "orbit.search.profile-documents.sync-on-startup=false"
})
class TagVocabularyServiceIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private TagRepository tags;

    @Autowired
    private JdbcTemplate jdbc;

    /** The name of the embedding model in use, as stored with each vector. */
    private static final String MODEL = "test-model";

    private EmbeddingService embeddings;
    private LlmSummaryService llm;
    private TagVocabularyService vocabulary;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE tags RESTART IDENTITY CASCADE");
        embeddings = mock(EmbeddingService.class);
        when(embeddings.modelName()).thenReturn(MODEL);
        llm = mock(LlmSummaryService.class);
        vocabulary = new TagVocabularyService(tags, jdbc, embeddings, llm, 0.95, false, Runnable::run);
    }

    @Test
    void theSpellingKeyIgnoresCaseUmlautsAcronymsHyphensAndPlurals() {
        assertThat(TagVocabularyService.canonicalKey("Life Cycle Assessments (LCA)"))
                .isEqualTo(TagVocabularyService.canonicalKey("life-cycle assessment"));
        assertThat(TagVocabularyService.canonicalKey("Wasserfußabdruck"))
                .isEqualTo(TagVocabularyService.canonicalKey("Wasserfussabdruck"));
        assertThat(TagVocabularyService.canonicalKey("Digitale Zwillinge"))
                .isEqualTo(TagVocabularyService.canonicalKey("Digitale Zwilling"));
        assertThat(TagVocabularyService.canonicalKey("Ökobilanz")).isEqualTo("oekobilanz");
        assertThat(TagVocabularyService.canonicalKey("Genomik"))
                .isNotEqualTo(TagVocabularyService.canonicalKey("Genetik"));
        assertThat(TagVocabularyService.canonicalKey("IoT")).isEqualTo("iot");
    }

    @Test
    void findOrMintReusesASpellingVariantAndMintsAnythingElse() {
        Tag lca = tags.save(new Tag("life-cycle-assessment", "Life Cycle Assessment"));

        assertThat(vocabulary.findOrMint("Life Cycle Assessments (LCA)")).map(Tag::getId).contains(lca.getId());
        Tag robotik = vocabulary.findOrMint(" Robotik ").orElseThrow();
        assertThat(robotik.getLabel()).isEqualTo("Robotik");
        assertThat(vocabulary.findOrMint("robotik")).map(Tag::getId).contains(robotik.getId());
        assertThat(vocabulary.findOrMint("  ")).isEmpty();
        assertThat(tags.count()).isEqualTo(2);
    }

    @Test
    void aNewLabelIsMappedByMeaningOnlyWhenTheLlmConfirmsIt() {
        existing("Innovationsmanagement", axis(0));
        existing("Genomik", axis(1));
        when(embeddings.embedAll(List.of("Innovation Management", "Genetik", "Robotik"), EmbeddingKind.QUERY))
                .thenReturn(List.of(near(0, 0.97), near(1, 0.96), axis(2)));
        when(llm.sameTopics(List.of(
                new TopicPair("Innovation Management", "Innovationsmanagement"),
                new TopicPair("Genetik", "Genomik"))))
                .thenReturn(List.of(true, false));

        assertThat(vocabulary.canonicalLabels(List.of("Innovation Management", "Genetik", "Robotik")))
                .containsExactly("Innovationsmanagement", "Genetik", "Robotik");
    }

    @Test
    void aSpellingVariantNeedsNoEmbeddingAndDuplicatesAfterMappingAreDropped() {
        existing("Life Cycle Assessment", axis(0));

        assertThat(vocabulary.canonicalLabels(List.of("Life Cycle Assessments", "Life Cycle Assessment (LCA)")))
                .containsExactly("Life Cycle Assessment");
        verify(embeddings, never()).embedAll(anyList(), any());
        verify(llm, never()).sameTopics(anyList());
    }

    @Test
    void candidatesBelowTheSimilarityFloorAreNotOfferedToTheLlm() {
        existing("Innovationsmanagement", axis(0));
        when(embeddings.embedAll(List.of("Supply Chain"), EmbeddingKind.QUERY)).thenReturn(List.of(near(0, 0.90)));

        assertThat(vocabulary.canonicalLabels(List.of("Supply Chain"))).containsExactly("Supply Chain");
        verify(llm, never()).sameTopics(anyList());
    }

    @Test
    void anUnavailableEmbeddingModelLeavesTheLabelsAsDerived() {
        existing("Innovationsmanagement", axis(0));
        when(embeddings.embedAll(anyList(), any())).thenThrow(new InferenceUnavailableException("down"));

        assertThat(vocabulary.canonicalLabels(List.of("Innovation Management")))
                .containsExactly("Innovation Management");
    }

    @Test
    void tagsWithoutAVectorAreEmbeddedAndThenFoundAsCandidates() {
        tags.save(new Tag("robotik", "Robotik"));
        when(embeddings.embedAll(List.of("Robotik"), EmbeddingKind.QUERY)).thenReturn(List.of(axis(3)));

        assertThat(vocabulary.embedMissing()).isEqualTo(1);
        assertThat(vocabulary.embedMissing()).isZero();
        assertThat(jdbc.queryForObject("SELECT embedding_model FROM tags WHERE slug = 'robotik'", String.class))
                .isEqualTo(MODEL);
    }

    // ─── helpers ───

    private void existing(String label, float[] vector) {
        Tag tag = tags.save(new Tag(com.orbit.app.util.SlugUtils.slugify(label), label));
        jdbc.update("UPDATE tags SET embedding = CAST(? AS vector), embedding_model = ? WHERE id = ?",
                VectorLiteral.of(vector), MODEL, tag.getId());
    }

    private static float[] axis(int index) {
        float[] vector = new float[1024];
        vector[index] = 1f;
        return vector;
    }

    /** A unit vector at cosine {@code similarity} to {@code axis(index)}. */
    private static float[] near(int index, double similarity) {
        float[] vector = axis(index);
        vector[index] = (float) similarity;
        vector[1023] = (float) Math.sqrt(1 - similarity * similarity);
        return vector;
    }
}
