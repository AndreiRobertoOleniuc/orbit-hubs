package com.orbit.app.controllers.dto;

/**
 * Hub read model, with its owning region resolved for display. Used both by the
 * entity profile (an entity's confirmed hubs) and the hub selector / admin list.
 */
public record HubDto(
        Long id,
        String slug,
        String name,
        String hubType,
        Long regionId,
        String regionName) {
}
