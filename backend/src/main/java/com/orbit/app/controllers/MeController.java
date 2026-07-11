package com.orbit.app.controllers;

import java.util.Map;

import com.orbit.app.admin.AdminAccess;
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
    private final AdminAccess adminAccess;

    MeController(CurrentUser currentUser, AdminAccess adminAccess) {
        this.currentUser = currentUser;
        this.adminAccess = adminAccess;
    }

    @GetMapping("/api/v1/me")
    Map<String, Object> me() {
        String userId = currentUser.userId().orElse("");
        return Map.of(
                "userId", userId,
                "email", currentUser.email().orElse(""),
                "isAdmin", adminAccess.isAdmin(userId));
    }

}
