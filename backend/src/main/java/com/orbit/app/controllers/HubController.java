package com.orbit.app.controllers;

import java.util.List;

import com.orbit.app.controllers.dto.HubDto;
import com.orbit.app.hub.HubService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin REST controller for the public hub vocabulary. All logic lives in
 * {@link HubService}.
 */
@RestController
class HubController {

    private final HubService hubService;

    HubController(HubService hubService) {
        this.hubService = hubService;
    }

    @GetMapping("/api/v1/hubs")
    List<HubDto> list() {
        return hubService.listHubs();
    }
}