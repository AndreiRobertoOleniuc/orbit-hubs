package com.orbit.app.controllers.dto;

/** Stable error body for failures whose message is safe and useful to clients. */
public record ApiErrorDto(String message) {
}
