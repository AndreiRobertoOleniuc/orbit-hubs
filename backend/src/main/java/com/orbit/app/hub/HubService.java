package com.orbit.app.hub;

import java.util.List;

import com.orbit.app.admin.AdminAccess;
import com.orbit.app.auth.CurrentUser;
import com.orbit.app.util.HubDtos;
import com.orbit.app.util.SlugUtils;
import com.orbit.app.controllers.dto.HubDto;
import com.orbit.app.controllers.dto.RegionDto;
import com.orbit.domain.entity.Hub;
import com.orbit.domain.repository.HubRepository;
import com.orbit.domain.repository.RegionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the hub vocabulary. Owns hub CRUD, admin authorization,
 * and region listing — everything that was previously inline in
 * {@code AdminHubController} and {@code HubController}.
 */
@Service
public class HubService {

    private final HubRepository hubs;
    private final RegionRepository regions;
    private final AdminAccess adminAccess;
    private final CurrentUser currentUser;

    public HubService(HubRepository hubs, RegionRepository regions,
                      AdminAccess adminAccess, CurrentUser currentUser) {
        this.hubs = hubs;
        this.regions = regions;
        this.adminAccess = adminAccess;
        this.currentUser = currentUser;
    }

    // ─── Public reads ───

    public List<HubDto> listHubs() {
        return HubDtos.from(hubs.findAllByOrderByNameAsc(), regions);
    }

    public List<RegionDto> listRegions() {
        return regions.findAll().stream()
                .map(r -> new RegionDto(r.getId(), r.getSlug(), r.getName(), r.getParentRegionId()))
                .toList();
    }

    // ─── Admin reads ───

    public List<HubDto> listHubsAsAdmin() {
        requireAdmin();
        return listHubs();
    }

    public List<RegionDto> listRegionsAsAdmin() {
        requireAdmin();
        return listRegions();
    }

    // ─── Admin CRUD ───

    @Transactional
    public HubDto createHub(String name, String slug, String hubType, Long regionId) {
        requireAdmin();
        if (name == null || name.isBlank() || regionId == null
                || regions.findById(regionId).isEmpty()) {
            return null;
        }
        Hub hub = new Hub(resolveSlug(slug, name), name.trim(), hubType, regionId);
        hubs.save(hub);
        return HubDtos.from(hub, regions.findById(hub.getRegionId()).orElse(null));
    }

    @Transactional
    public HubDto updateHub(Long id, String name, String slug, String hubType, Long regionId) {
        requireAdmin();
        Hub hub = hubs.findById(id).orElse(null);
        if (hub == null) return null;
        if (regionId != null && regions.findById(regionId).isEmpty()) {
            throw new IllegalArgumentException("Region not found: " + regionId);
        }

        if (name != null && !name.isBlank()) hub.setName(name.trim());
        if (slug != null && !slug.isBlank()) hub.setSlug(SlugUtils.slugify(slug));
        hub.setHubType(hubType);
        if (regionId != null) hub.setRegionId(regionId);
        hubs.save(hub);
        return HubDtos.from(hub, regions.findById(hub.getRegionId()).orElse(null));
    }

    @Transactional
    public boolean deleteHub(Long id) {
        requireAdmin();
        if (hubs.findById(id).isEmpty()) return false;
        hubs.deleteById(id);
        return true;
    }

    private void requireAdmin() {
        if (!adminAccess.isAdmin(currentUser.userId().orElse(null))) {
            throw new HubAccessDeniedException();
        }
    }

    private String resolveSlug(String slug, String name) {
        String base = slug != null && !slug.isBlank() ? slug : name;
        return SlugUtils.slugify(base);
    }
}