package com.orbit.app.controllers;

import java.util.List;

import com.orbit.app.controllers.dto.RegionDto;
import com.orbit.app.hub.HubService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin REST controller for the public region tree (Hub → Region → CH, §5). Backs
 * the search scope selector and its widen ladder. All logic lives in
 * {@link HubService}.
 */
@RestController
class RegionController {

    private final HubService hubService;

    RegionController(HubService hubService) {
        this.hubService = hubService;
    }

    @GetMapping("/api/v1/regions")
    List<RegionDto> list() {
        return hubService.listRegions();
    }
}
