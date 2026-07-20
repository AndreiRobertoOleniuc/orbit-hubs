package com.orbit.app.ingestion;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import com.orbit.domain.entity.DiscoveryEntity;
import com.orbit.domain.entity.EntityLink;
import com.orbit.domain.repository.DiscoveryEntityRepository;
import com.orbit.domain.repository.EntityLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityLinkResolverTest {

    private DiscoveryEntityRepository entities;
    private EntityLinkRepository links;
    private EntityLinkResolver resolver;

    @BeforeEach
    void setUp() {
        entities = mock(DiscoveryEntityRepository.class);
        links = mock(EntityLinkRepository.class);
        resolver = new EntityLinkResolver(entities, links);
    }

    @Test
    void ignoresNullAndOrganizationProfiles() {
        resolver.resolveForPerson(null);
        resolver.resolveForPerson(entity(1L, "organization", "Orbit AG", null));

        verify(links, never()).findFirstByPersonEntityId(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void linksNormalizedLegalNameToExistingOrganization() {
        DiscoveryEntity person = entity(1L, "person", "Ada", "Orbit GmbH");
        DiscoveryEntity org = entity(2L, "organization", "Orbit", null);
        when(links.findFirstByPersonEntityId(1L)).thenReturn(Optional.empty());
        when(entities.findByEntityType("organization")).thenReturn(List.of(org));

        resolver.resolveForPerson(person);

        verify(links).save(argThat(link -> link.getPersonEntityId().equals(1L)
                && link.getOrgEntityId().equals(2L) && link.getSource().equals("derived")));
    }

    @Test
    void confirmedLinkIsNeverChanged() {
        DiscoveryEntity person = entity(1L, "person", "Ada", "Different Org");
        EntityLink confirmed = new EntityLink(1L, 2L, null, "confirmed");
        when(links.findFirstByPersonEntityId(1L)).thenReturn(Optional.of(confirmed));

        resolver.resolveForPerson(person);

        verify(links, never()).delete(org.mockito.ArgumentMatchers.any());
        verify(entities, never()).findByEntityType(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void staleDerivedLinkIsRemovedWhenEmployerNoLongerMatches() {
        DiscoveryEntity person = entity(1L, "person", "Ada", "Unknown Org");
        EntityLink existing = new EntityLink(1L, 2L, null, "derived");
        when(links.findFirstByPersonEntityId(1L)).thenReturn(Optional.of(existing));
        when(entities.findByEntityType("organization")).thenReturn(List.of());

        resolver.resolveForPerson(person);

        verify(links).delete(existing);
        verify(links).flush();
    }

    private static DiscoveryEntity entity(Long id, String type, String name, String orgName) {
        DiscoveryEntity entity = new DiscoveryEntity(type, name, "Headline", orgName);
        setField(entity, "id", id);
        return entity;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
}
