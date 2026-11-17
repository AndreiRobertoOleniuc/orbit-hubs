package com.orbit;

import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * application.yaml holds the model choices and the values tied to them (they
 * have no default in code), the credentials the deployment passes in under its
 * own names, and what differs from Spring's defaults. A key not on this list is
 * either a code default repeated (it then drifts from the code) or a key under
 * the wrong parent, which goes unnoticed at run time.
 */
class ApplicationYamlTest {

    @Test
    void containsTheModelChoicesCredentialsAndNothingElse() {
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
                "orbit.openrouter.data-collection",
                "orbit.openrouter.chat.model",
                "orbit.openrouter.chat.fallback-models[0]",
                "orbit.openrouter.chat.fallback-models[1]",
                "orbit.openrouter.chat.temperature",
                "orbit.openrouter.chat.max-tokens",
                "orbit.openrouter.embedding.model",
                "orbit.openrouter.embedding.dimensions",
                "orbit.openrouter.embedding.query-input-type",
                "orbit.openrouter.embedding.passage-input-type",
                "orbit.openrouter.reranker.model",
                "orbit.search.reranker-threshold",
                "orbit.tags.canonical-similarity",
                "orbit.github.token",
                "orbit.openalex.mailto",
                "orbit.openalex.api-key",
                "orbit.zefix.user",
                "orbit.zefix.key");
    }
}
