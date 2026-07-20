package com.orbit.app.admin;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAccessTest {

    @Test
    void parsesTrimmedAllowlistAndRejectsEverythingElse() {
        AdminAccess access = new AdminAccess(" user-a, user-b ,,user-c ");

        assertThat(access.isAdmin("user-a")).isTrue();
        assertThat(access.isAdmin("user-b")).isTrue();
        assertThat(access.isAdmin("user-c")).isTrue();
        assertThat(access.isAdmin("user-d")).isFalse();
        assertThat(access.isAdmin(null)).isFalse();
    }

    @Test
    void emptyConfigurationMeansNoAdmins() {
        AdminAccess access = new AdminAccess("");

        assertThat(access.isAdmin("anyone")).isFalse();
    }
}
