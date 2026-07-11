package com.orbit.app.controllers;

import java.util.List;

import com.orbit.app.controllers.dto.HubDto;
import com.orbit.app.util.HubDtos;
import com.orbit.domain.repository.HubRepository;
import com.orbit.domain.repository.RegionRepository;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public read of the hub vocabulary — backs the profile hub selector (and later
 * the search scope picker). Whitelisted in {@code ClerkSecurityConfig}.
 */
@RestController
class HubController {

    private final HubRepository hubs;
    private final RegionRepository regions;

    HubController(HubRepository hubs, RegionRepository regions) {
        this.hubs = hubs;
        this.regions = regions;
    }

    @GetMapping("/api/v1/hubs")
    List<HubDto> list() {
        return HubDtos.from(hubs.findAllByOrderByNameAsc(), regions);
    }
}
