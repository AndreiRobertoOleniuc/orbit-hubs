package com.orbit.app.controllers.dto;

/** Request body for transitioning a request's status. */
public record UpdateStatusRequest(String status) {}