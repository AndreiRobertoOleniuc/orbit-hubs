package com.orbit.app.admin;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The admin gate. Since Clerk tokens carry no roles/authorities in this app
 * (see {@code ClerkSecurityConfig}), admin access is a config-driven allowlist
 * of Clerk user ids ({@code orbit.admin.user-ids}, env {@code ORBIT_ADMIN_USER_IDS}).
 * Controllers call {@link #isAdmin(String)} and return 403 when it is false.
 */
@Component
public class AdminAccess {

    private final Set<String> adminUserIds;

    AdminAccess(@Value("${orbit.admin.user-ids:}") String csv) {
        this.adminUserIds = Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAdmin(String userId) {
        return userId != null && adminUserIds.contains(userId);
    }
}
