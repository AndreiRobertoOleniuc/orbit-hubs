package com.orbit;

import com.orbit.testsupport.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "HF_TOKEN=test-offline-token",
        "GEMINI_API_KEY=test-offline-key"
})
class OrbitApplicationTests extends PostgresIntegrationTest {

    @Test
    void contextLoads() {
    }

}
