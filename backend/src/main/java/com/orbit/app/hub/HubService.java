package com.orbit.app.hub;

import java.util.List;

import com.orbit.app.admin.AdminAccess;
import com.orbit.app.auth.CurrentUser;
import com.orbit.app.util.HubDtos;
import com.orbit.app.util.SlugUtils;
import com.orbit.app.controllers.dto.HubDto;
import com.orbit.app.controllers.dto.HubMutationRequest;
import com.orbit.app.controllers.dto.RegionDto;
import com.orbit.domain.entity.Hub;
import com.orbit.domain.entity.Region;
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

    /** Root of the region tree — parent of every region created on demand. */
    private static final String ROOT_REGION_SLUG = "ch";

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
    public HubDto createHub(HubMutationRequest request) {
        requireAdmin();
        String name = request.name();
        if (name == null || name.isBlank()) return null;
        Region region = resolveRegion(request);
        if (region == null) return null;

        Hub hub = new Hub(resolveSlug(request.slug(), name), name.trim(), request.hubType(), region.getId());
        hubs.save(hub);
        return HubDtos.from(hub, region);
    }

    @Transactional
    public HubDto updateHub(Long id, HubMutationRequest request) {
        requireAdmin();
        Hub hub = hubs.findById(id).orElse(null);
        if (hub == null) return null;

        Region region = resolveRegion(request);
        if (region == null && specifiesRegion(request)) {
            throw new IllegalArgumentException("Region not found: " + request.regionId());
        }

        String name = request.name();
        if (name != null && !name.isBlank()) hub.setName(name.trim());
        if (request.slug() != null && !request.slug().isBlank()) hub.setSlug(SlugUtils.slugify(request.slug()));
        hub.setHubType(request.hubType());
        if (region != null) hub.setRegionId(region.getId());
        hubs.save(hub);
        return HubDtos.from(hub, region != null
                ? region
                : regions.findById(hub.getRegionId()).orElse(null));
    }

    @Transactional
    public boolean deleteHub(Long id) {
        requireAdmin();
        if (hubs.findById(id).isEmpty()) return false;
        hubs.deleteById(id);
        return true;
    }

    /**
     * The hub's owning region: an existing one when {@code regionId} is set,
     * otherwise the region named by the admin — created on demand so hubs are not
     * limited to the seeded region tree. Returns {@code null} when the request
     * names no resolvable region.
     */
    private Region resolveRegion(HubMutationRequest request) {
        if (request.regionId() != null) {
            return regions.findById(request.regionId()).orElse(null);
        }
        String name = request.regionName();
        if (name == null || name.isBlank()) return null;
        return findOrCreateRegion(name.trim(), request.regionLatitude(), request.regionLongitude());
    }

    private Region findOrCreateRegion(String name, Double latitude, Double longitude) {
        String slug = SlugUtils.slugify(name);
        return regions.findBySlug(slug).orElseGet(() -> {
            Long parentId = regions.findBySlug(ROOT_REGION_SLUG).map(Region::getId).orElse(null);
            return regions.save(new Region(slug, name, parentId, latitude, longitude));
        });
    }

    private static boolean specifiesRegion(HubMutationRequest request) {
        return request.regionId() != null
                || (request.regionName() != null && !request.regionName().isBlank());
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