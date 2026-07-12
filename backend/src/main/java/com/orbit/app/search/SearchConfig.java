package com.orbit.app.search;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers search-related configuration properties. */
@Configuration
@EnableConfigurationProperties(RerankProperties.class)
class SearchConfig {
}
