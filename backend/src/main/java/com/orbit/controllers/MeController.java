package com.orbit.controllers;

import java.util.Map;

import com.orbit.app.auth.CurrentUser;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runtime endpoint exposing the currently authenticated Clerk-mapped user.
 *
 * <p>
 * Also serves as a practical authenticated route that triggers just-in-time
 * user provisioning on first access.
 */
@RestController
class MeController {

    private final CurrentUser currentUser;

    MeController(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @GetMapping("/api/v1/me")
    Map<String, String> me() {
        return Map.of(
                "userId", currentUser.userId().orElse(""),
                "email", currentUser.email().orElse(""));
    }

}
