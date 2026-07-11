package com.orbit.app.controllers;

import java.util.List;

import com.orbit.app.admin.AdminAccess;
import com.orbit.app.auth.CurrentUser;
import com.orbit.app.controllers.dto.HubDto;
import com.orbit.app.controllers.dto.HubMutationRequest;
import com.orbit.app.controllers.dto.RegionDto;
import com.orbit.app.util.HubDtos;
import com.orbit.app.util.SlugUtils;
import com.orbit.domain.entity.Hub;
import com.orbit.domain.repository.HubRepository;
import com.orbit.domain.repository.RegionRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin surface for managing the hub vocabulary. Access is gated by the
 * {@link AdminAccess} allowlist — every endpoint returns 403 for non-admins.
 * Hubs must attach to an existing region ({@code GET /admin/regions} lists them;
 * regions are seed-managed for now).
 */
@RestController
class AdminHubController {

    private final HubRepository hubs;
    private final RegionRepository regions;
    private final AdminAccess adminAccess;
    private final CurrentUser currentUser;

    AdminHubController(HubRepository hubs, RegionRepository regions,
                       AdminAccess adminAccess, CurrentUser currentUser) {
        this.hubs = hubs;
        this.regions = regions;
        this.adminAccess = adminAccess;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/v1/admin/hubs")
    ResponseEntity<List<HubDto>> list() {
        if (forbidden()) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(HubDtos.from(hubs.findAllByOrderByNameAsc(), regions));
    }

    @GetMapping("/api/v1/admin/regions")
    ResponseEntity<List<RegionDto>> listRegions() {
        if (forbidden()) return ResponseEntity.status(403).build();
        List<RegionDto> dtos = regions.findAll().stream()
                .map(r -> new RegionDto(r.getId(), r.getSlug(), r.getName(), r.getParentRegionId()))
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/api/v1/admin/hubs")
    ResponseEntity<HubDto> create(@RequestBody HubMutationRequest request) {
        if (forbidden()) return ResponseEntity.status(403).build();
        if (request.name() == null || request.name().isBlank() || request.regionId() == null
                || regions.findById(request.regionId()).isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Hub hub = new Hub(resolveSlug(request), request.name().trim(),
                request.hubType(), request.regionId());
        hubs.save(hub);
        return ResponseEntity.ok(HubDtos.from(hub, regions.findById(hub.getRegionId()).orElse(null)));
    }

    @PutMapping("/api/v1/admin/hubs/{id}")
    ResponseEntity<HubDto> update(@PathVariable Long id, @RequestBody HubMutationRequest request) {
        if (forbidden()) return ResponseEntity.status(403).build();
        Hub hub = hubs.findById(id).orElse(null);
        if (hub == null) return ResponseEntity.notFound().build();
        if (request.regionId() != null && regions.findById(request.regionId()).isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (request.name() != null && !request.name().isBlank()) hub.setName(request.name().trim());
        if (request.slug() != null && !request.slug().isBlank()) hub.setSlug(SlugUtils.slugify(request.slug()));
        hub.setHubType(request.hubType());
        if (request.regionId() != null) hub.setRegionId(request.regionId());
        hubs.save(hub);
        return ResponseEntity.ok(HubDtos.from(hub, regions.findById(hub.getRegionId()).orElse(null)));
    }

    @DeleteMapping("/api/v1/admin/hubs/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id) {
        if (forbidden()) return ResponseEntity.status(403).build();
        if (hubs.findById(id).isEmpty()) return ResponseEntity.notFound().build();
        hubs.deleteById(id);   // hub_memberships cascade via FK on delete cascade
        return ResponseEntity.noContent().build();
    }

    private boolean forbidden() {
        return !adminAccess.isAdmin(currentUser.userId().orElse(null));
    }

    private String resolveSlug(HubMutationRequest request) {
        String base = request.slug() != null && !request.slug().isBlank()
                ? request.slug() : request.name();
        return SlugUtils.slugify(base);
    }
}
