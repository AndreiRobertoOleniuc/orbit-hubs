package com.orbit.app.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.orbit.app.inference.InferenceUnavailableException;

class GlobalExceptionHandlerTest {

    @Test
    void mapsRequiredInferenceOutageToServiceUnavailable() {
        var response = new GlobalExceptionHandler()
                .handleInferenceUnavailable(new InferenceUnavailableException("model loading"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void mapsOversizedUploadToPayloadTooLargeWithUsefulMessage() {
        var response = new GlobalExceptionHandler()
                .handleMaxUploadSize(new MaxUploadSizeExceededException(20 * 1024 * 1024L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("maximal 20 MB");
    }
}
