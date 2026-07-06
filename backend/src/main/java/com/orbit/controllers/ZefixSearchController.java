package com.orbit.controllers;

import java.util.List;

import com.orbit.app.ingestion.zefix.ZefixAdapter;
import com.orbit.app.ingestion.zefix.dto.ZefixCompanySummary;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handelsregister autocomplete for the organization onboarding step.
 * Authenticated (not whitelisted): only signed-in users onboarding an
 * organization need it.
 */
@RestController
class ZefixSearchController {

    private static final int MAX_RESULTS = 6;

    private final ZefixAdapter zefixAdapter;

    ZefixSearchController(ZefixAdapter zefixAdapter) {
        this.zefixAdapter = zefixAdapter;
    }

    @GetMapping("/api/v1/zefix/search")
    List<ZefixCompanySummary> search(@RequestParam("name") String name) {
        if (name == null || name.trim().length() < 3) {
            return List.of();
        }
        return zefixAdapter.searchCompanies(name.trim(), MAX_RESULTS);
    }
}
