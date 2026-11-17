package com.orbit;

import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * application.yaml holds only what differs from the defaults in code and the
 * credentials the deployment passes in under its own names. A key that is not
 * on this list is either a default repeated (it then drifts from the code) or
 * a key under the wrong parent, which goes unnoticed at run time: the code
 * falls back to its default.
 */
class ApplicationYamlTest {

    @Test
    void containsOnlyNonDefaultSettingsAndCredentials() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yaml"));
        Properties properties = yaml.getObject();

        assertThat(properties.keySet()).map(String::valueOf).containsExactlyInAnyOrder(
                "spring.application.name",
                "spring.datasource.url",
                "spring.datasource.username",
                "spring.datasource.password",
                "spring.flyway.baseline-on-migrate",
                "spring.flyway.baseline-version",
                "spring.jpa.open-in-view",
                "spring.jpa.hibernate.ddl-auto",
                "spring.servlet.multipart.max-file-size",
                "spring.servlet.multipart.max-request-size",
                "spring.security.oauth2.resourceserver.jwt.issuer-uri",
                "clerk.azp-origin",
                "orbit.admin.user-ids",
                "orbit.openrouter.api-key",
                "orbit.github.token",
                "orbit.openalex.mailto",
                "orbit.openalex.api-key",
                "orbit.zefix.user",
                "orbit.zefix.key");
    }
}
