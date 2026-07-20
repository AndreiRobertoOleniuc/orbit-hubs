package com.orbit.app.profile;

import com.orbit.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ProfileServiceTest {

    @Test
    void provisioningDelegatesAllClaimsToIdempotentUpsert() {
        UserRepository users = mock(UserRepository.class);
        ProfileService service = new ProfileService(users);

        service.provisionUser("user-1", "ada@example.org", "Ada");
        service.provisionUser("user-2", null, "No Email");

        verify(users).upsert("user-1", "ada@example.org", "Ada");
        verify(users).upsert("user-2", null, "No Email");
    }
}
