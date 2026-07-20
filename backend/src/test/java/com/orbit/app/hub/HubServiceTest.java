package com.orbit.app.hub;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import com.orbit.app.admin.AdminAccess;
import com.orbit.app.auth.CurrentUser;
import com.orbit.domain.entity.Hub;
import com.orbit.domain.entity.Region;
import com.orbit.domain.repository.HubRepository;
import com.orbit.domain.repository.RegionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HubServiceTest {

    private HubRepository hubs;
    private RegionRepository regions;
    private AdminAccess adminAccess;
    private CurrentUser currentUser;
    private HubService service;

    @BeforeEach
    void setUp() {
        hubs = mock(HubRepository.class);
        regions = mock(RegionRepository.class);
        adminAccess = mock(AdminAccess.class);
        currentUser = mock(CurrentUser.class);
        service = new HubService(hubs, regions, adminAccess, currentUser);
        when(currentUser.userId()).thenReturn(Optional.of("admin"));
    }

    @Test
    void publicListingsResolveRegionNames() {
        Region region = region(10L, "aargau", "Aargau");
        Hub hub = hub(20L, "fhnw", "FHNW", 10L);
        when(regions.findAll()).thenReturn(List.of(region));
        when(regions.findById(10L)).thenReturn(Optional.of(region));
        when(regions.findAllById(List.of(10L))).thenReturn(List.of(region));
        when(hubs.findAllByOrderByNameAsc()).thenReturn(List.of(hub));

        assertThat(service.listRegions()).singleElement().satisfies(dto ->
                assertThat(dto.name()).isEqualTo("Aargau"));
        assertThat(service.listHubs()).singleElement().satisfies(dto -> {
            assertThat(dto.name()).isEqualTo("FHNW");
            assertThat(dto.regionName()).isEqualTo("Aargau");
        });
    }

    @Test
    void nonAdminCannotUseAnyAdminReadOrMutation() {
        when(adminAccess.isAdmin("admin")).thenReturn(false);

        assertThatThrownBy(service::listHubsAsAdmin).isInstanceOf(HubAccessDeniedException.class);
        assertThatThrownBy(service::listRegionsAsAdmin).isInstanceOf(HubAccessDeniedException.class);
        assertThatThrownBy(() -> service.createHub("X", null, null, 1L))
                .isInstanceOf(HubAccessDeniedException.class);
        assertThatThrownBy(() -> service.updateHub(1L, "X", null, null, 1L))
                .isInstanceOf(HubAccessDeniedException.class);
        assertThatThrownBy(() -> service.deleteHub(1L)).isInstanceOf(HubAccessDeniedException.class);
        verify(hubs, never()).save(any());
    }

    @Test
    void adminCreatesHubWithGeneratedSlug() {
        allowAdmin();
        Region region = region(10L, "aargau", "Aargau");
        when(regions.findById(10L)).thenReturn(Optional.of(region));
        when(hubs.save(any())).thenAnswer(invocation -> {
            Hub saved = invocation.getArgument(0);
            setField(saved, "id", 20L);
            return saved;
        });

        var dto = service.createHub("Innovation Hub", "", "ecosystem", 10L);

        assertThat(dto.slug()).isEqualTo("innovation-hub");
        assertThat(dto.regionName()).isEqualTo("Aargau");
    }

    @Test
    void invalidCreateReturnsNullAndInvalidUpdateRegionThrows() {
        allowAdmin();
        assertThat(service.createHub("", null, null, 1L)).isNull();

        Hub hub = hub(20L, "old", "Old", 10L);
        when(hubs.findById(20L)).thenReturn(Optional.of(hub));
        when(regions.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateHub(20L, "New", null, null, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Region not found");
    }

    @Test
    void adminUpdatesAndDeletesExistingHub() {
        allowAdmin();
        Hub hub = hub(20L, "old", "Old", 10L);
        Region region = region(11L, "zurich", "Zürich");
        when(hubs.findById(20L)).thenReturn(Optional.of(hub));
        when(regions.findById(11L)).thenReturn(Optional.of(region));

        var dto = service.updateHub(20L, "New Name", "New Slug", "city", 11L);

        assertThat(dto.slug()).isEqualTo("new-slug");
        assertThat(dto.name()).isEqualTo("New Name");
        assertThat(dto.regionName()).isEqualTo("Zürich");
        verify(hubs).save(hub);

        assertThat(service.deleteHub(20L)).isTrue();
        verify(hubs).deleteById(20L);
    }

    @Test
    void deletingMissingHubReturnsFalse() {
        allowAdmin();
        when(hubs.findById(404L)).thenReturn(Optional.empty());

        assertThat(service.deleteHub(404L)).isFalse();
        verify(hubs, never()).deleteById(404L);
    }

    private void allowAdmin() {
        when(adminAccess.isAdmin("admin")).thenReturn(true);
    }

    private static Hub hub(Long id, String slug, String name, Long regionId) {
        Hub hub = new Hub(slug, name, "institution", regionId);
        setField(hub, "id", id);
        return hub;
    }

    private static Region region(Long id, String slug, String name) {
        Region region = new Region(slug, name, null);
        setField(region, "id", id);
        return region;
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
