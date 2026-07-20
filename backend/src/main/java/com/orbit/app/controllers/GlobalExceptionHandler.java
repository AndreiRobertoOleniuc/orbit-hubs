package com.orbit.app.controllers;

import com.orbit.app.profile.EntityAccessDeniedException;
import com.orbit.app.hub.HubAccessDeniedException;
import com.orbit.app.inference.InferenceUnavailableException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception → HTTP status mapping. Keeps controllers thin — services
 * throw, this advice translates. No controller needs its own
 * {@code @ExceptionHandler}.
 *
 * <ul>
 *   <li>{@link EntityAccessDeniedException} → 403 (entity exists, wrong owner)</li>
 *   <li>{@link HubAccessDeniedException} → 403 (hub admin action, not admin)</li>
 *   <li>{@link IllegalArgumentException} → 400 (bad request — invalid input)</li>
 *   <li>{@link InferenceUnavailableException} → 503 (required managed model unavailable)</li>
 *   <li>Any other {@link RuntimeException} → 500 (logged, not leaked to client)</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EntityAccessDeniedException.class)
    public ResponseEntity<Void> handleAccessDenied(EntityAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @ExceptionHandler(HubAccessDeniedException.class)
    public ResponseEntity<Void> handleHubAccessDenied(HubAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Void> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler(InferenceUnavailableException.class)
    public ResponseEntity<Void> handleInferenceUnavailable(InferenceUnavailableException ex) {
        log.warn("Required inference provider unavailable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Void> handleUnexpected(RuntimeException ex) {
        log.error("Unhandled exception in controller", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
}
