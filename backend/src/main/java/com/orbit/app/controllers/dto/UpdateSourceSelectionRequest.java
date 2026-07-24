package com.orbit.app.controllers.dto;

import java.util.List;

/** Batch selection of the GitHub repositories and ORCID works used by a profile. */
public record UpdateSourceSelectionRequest(List<Long> activeSourceIds) {
}
