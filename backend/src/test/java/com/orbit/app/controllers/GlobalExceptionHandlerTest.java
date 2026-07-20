package com.orbit.app.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.orbit.app.inference.InferenceUnavailableException;

class GlobalExceptionHandlerTest {

    @Test
    void mapsRequiredInferenceOutageToServiceUnavailable() {
        var response = new GlobalExceptionHandler()
                .handleInferenceUnavailable(new InferenceUnavailableException("model loading"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
