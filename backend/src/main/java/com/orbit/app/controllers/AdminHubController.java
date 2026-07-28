package com.orbit.app.controllers;

import java.util.List;

import com.orbit.app.controllers.dto.HubDto;
import com.orbit.app.controllers.dto.HubMutationRequest;
import com.orbit.app.controllers.dto.RegionDto;
import com.orbit.app.hub.HubService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin REST controller for admin hub management. All business logic (CRUD,
 * admin authorization, slug resolution) lives in {@link HubService}.
 * Exception → HTTP status mapping is handled globally by {@link GlobalExceptionHandler}.
 */
@RestController
class AdminHubController {

    private final HubService hubService;

    AdminHubController(HubService hubService) {
        this.hubService = hubService;
    }

    @GetMapping("/api/v1/admin/hubs")
    ResponseEntity<List<HubDto>> list() {
        return ResponseEntity.ok(hubService.listHubsAsAdmin());
    }

    @GetMapping("/api/v1/admin/regions")
    ResponseEntity<List<RegionDto>> listRegions() {
        return ResponseEntity.ok(hubService.listRegionsAsAdmin());
    }

    @PostMapping("/api/v1/admin/hubs")
    ResponseEntity<HubDto> create(@RequestBody HubMutationRequest request) {
        HubDto dto = hubService.createHub(request);
        return dto != null ? ResponseEntity.ok(dto) : ResponseEntity.badRequest().build();
    }

    @PutMapping("/api/v1/admin/hubs/{id}")
    ResponseEntity<HubDto> update(@PathVariable Long id, @RequestBody HubMutationRequest request) {
        HubDto dto = hubService.updateHub(id, request);
        return dto != null ? ResponseEntity.ok(dto) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/api/v1/admin/hubs/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id) {
        return hubService.deleteHub(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}