package com.orbit.app.profile;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.orbit.domain.entity.DiscoveryEntity;
import com.orbit.domain.entity.Source;
import com.orbit.domain.repository.EntityTagRepository;
import com.orbit.domain.repository.SourceRepository;

class EntityServiceTest {

    private static final long ENTITY_ID = 7L;

    private SourceRepository sources;
    private EntityTagRepository entityTags;
    private EntityService service;

    @BeforeEach
    void setUp() {
        sources = mock(SourceRepository.class);
        entityTags = mock(EntityTagRepository.class);
        service = new EntityService(
                null, sources, null, entityTags, null, null, null,
                null, null, null, null, null, null);
    }

    @Test
    void demandPersonCanPublishWithoutGeneratedSummaryOrTags() {
        DiscoveryEntity entity = demandPerson();
        when(sources.findByEntityIdAndActiveTrueOrderByCreatedAtAsc(ENTITY_ID))
                .thenReturn(List.of(source("cv_pdf")));

        assertThatCode(() -> validate(entity)).doesNotThrowAnyException();
    }

    @Test
    void demandPersonWebsiteAloneDoesNotReplaceTheIdentityBasis() {
        DiscoveryEntity entity = demandPerson();
        when(sources.findByEntityIdAndActiveTrueOrderByCreatedAtAsc(ENTITY_ID))
                .thenReturn(List.of(source("personal_site")));

        assertThatThrownBy(() -> validate(entity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identity source");
    }

    @Test
    void demandOrganizationAcceptsZefixWithoutGeneratedFields() {
        DiscoveryEntity entity = new DiscoveryEntity("organization", "Orbit AG", "Innovation", null);
        ReflectionTestUtils.setField(entity, "id", ENTITY_ID);
        entity.setProfileMode("demand");
        when(sources.findByEntityIdAndActiveTrueOrderByCreatedAtAsc(ENTITY_ID))
                .thenReturn(List.of(source("zefix")));

        assertThatCode(() -> validate(entity)).doesNotThrowAnyException();
    }

    @Test
    void supplyStillRequiresAutomaticSummaryAndTags() {
        DiscoveryEntity entity = demandPerson();
        entity.setProfileMode("supply");
        when(sources.findByEntityIdAndActiveTrueOrderByCreatedAtAsc(ENTITY_ID))
                .thenReturn(List.of(source("cv_pdf")));

        assertThatThrownBy(() -> validate(entity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("summary");
    }

    private DiscoveryEntity demandPerson() {
        DiscoveryEntity entity = new DiscoveryEntity("person", "Ada Lovelace", "Engineer", null);
        ReflectionTestUtils.setField(entity, "id", ENTITY_ID);
        entity.setProfileMode("demand");
        entity.setAffiliationKind("self_employed");
        return entity;
    }

    private Source source(String type) {
        return new Source(
                ENTITY_ID, type, type + ":test", type, "user_supplied",
                "user_consent", "content", "hash", "ok", Instant.now());
    }

    private void validate(DiscoveryEntity entity) {
        ReflectionTestUtils.invokeMethod(service, "validatePublishable", entity);
    }
}
