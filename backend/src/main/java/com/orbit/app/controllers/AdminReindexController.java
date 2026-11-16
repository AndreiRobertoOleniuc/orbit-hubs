package com.orbit.app.controllers;

import com.orbit.app.admin.ReindexService;
import com.orbit.app.controllers.dto.ReindexDto;
import com.orbit.app.controllers.dto.ReindexRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin REST controller for the admin re-evaluation of stored profiles. The
 * admin check and the queueing live in {@link ReindexService}.
 */
@RestController
class AdminReindexController {

    private final ReindexService reindexService;

    AdminReindexController(ReindexService reindexService) {
        this.reindexService = reindexService;
    }

    /** Queue a resync of the entities in the body, or of all when the body is absent. */
    @PostMapping("/api/v1/admin/reindex")
    ResponseEntity<ReindexDto> reindex(@RequestBody(required = false) ReindexRequest request) {
        return ResponseEntity.accepted().body(reindexService.reindex(request));
    }

    @GetMapping("/api/v1/admin/reindex")
    ResponseEntity<ReindexDto> status() {
        return ResponseEntity.ok(reindexService.status());
    }
}
