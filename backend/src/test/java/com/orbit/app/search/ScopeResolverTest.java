package com.orbit.app.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import com.orbit.app.search.dto.ResolvedScope;
import com.orbit.app.search.dto.ScopeSelection;
import com.orbit.domain.entity.Region;
import com.orbit.domain.repository.HubRepository;
import com.orbit.domain.repository.RegionRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for scope resolution (§5.3) — repos mocked, no DB. */
@ExtendWith(MockitoExtension.class)
class ScopeResolverTest {

    @Mock
    private RegionRepository regions;

    @Mock
    private HubRepository hubs;

    private static Region region(Long id, Long parentId) {
        Region r = mock(Region.class);
        lenient().when(r.getId()).thenReturn(id);
        lenient().when(r.getParentRegionId()).thenReturn(parentId);
        return r;
    }

    @Test
    void allSelectionIsUnscopedWithoutTouchingRepos() {
        ResolvedScope scope = new ScopeResolver(regions, hubs)
                .resolve(new ScopeSelection("all", null, null));
        assertThat(scope.isUnscoped()).isTrue();
        verifyNoInteractions(regions, hubs);
    }

    @Test
    void nullSelectionIsUnscoped() {
        assertThat(new ScopeResolver(regions, hubs).resolve(null).isUnscoped()).isTrue();
    }

    @Test
    void hubSelectionIsStrict() {
        ResolvedScope scope = new ScopeResolver(regions, hubs)
                .resolve(new ScopeSelection("hub", 5L, null));
        assertThat(scope.hubIds()).containsExactly(5L);
        assertThat(scope.regionIds()).isEmpty();
    }

    @Test
    void regionSelectionIncludesDescendantRegions() {
        // CH(1) → Aargau(2) → Region Baden(3); build mocks before stubbing findAll.
        Region ch = region(1L, null);
        Region aargau = region(2L, 1L);
        Region baden = region(3L, 2L);
        when(regions.findAll()).thenReturn(List.of(ch, aargau, baden));
        when(hubs.findByRegionIdIn(anyCollection())).thenReturn(List.of());

        ResolvedScope scope = new ScopeResolver(regions, hubs)
                .resolve(new ScopeSelection("region", null, 2L));

        assertThat(scope.regionIds()).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    void unknownKindThrows() {
        assertThatThrownBy(() -> new ScopeResolver(regions, hubs)
                .resolve(new ScopeSelection("planet", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
