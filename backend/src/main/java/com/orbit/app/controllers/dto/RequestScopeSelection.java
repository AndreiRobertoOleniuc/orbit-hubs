package com.orbit.app.controllers.dto;

/** Geographic or hub scope supplied when creating a request. */
public record RequestScopeSelection(
        String kind,
        Long hubId,
        Long regionId
) {
}
