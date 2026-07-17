package com.orbit.app.request;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Registers request-lifecycle configuration properties + the escalation scheduler. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(RequestsProperties.class)
class RequestsConfig {
}
