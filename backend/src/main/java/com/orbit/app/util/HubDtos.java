package com.orbit.app.util;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.orbit.app.controllers.dto.HubDto;
import com.orbit.domain.entity.Hub;
import com.orbit.domain.entity.Region;
import com.orbit.domain.repository.RegionRepository;

/**
 * Builds {@link HubDto}s from {@link Hub}s, resolving each hub's owning region
 * name for display. Shared by the public hub list, the admin hub CRUD, and the
 * entity profile's confirmed-hubs list.
 */
public final class HubDtos {

    private HubDtos() {
    }

    /** Map a batch of hubs to DTOs, resolving region names in one query. */
    public static List<HubDto> from(List<Hub> hubs, RegionRepository regions) {
        List<Long> regionIds = hubs.stream().map(Hub::getRegionId).distinct().toList();
        Map<Long, Region> regionById = regions.findAllById(regionIds).stream()
                .collect(Collectors.toMap(Region::getId, Function.identity()));
        return hubs.stream().map(h -> from(h, regionById.get(h.getRegionId()))).toList();
    }

    /** Map a single hub to a DTO given its (possibly null) region. */
    public static HubDto from(Hub hub, Region region) {
        return new HubDto(
                hub.getId(),
                hub.getSlug(),
                hub.getName(),
                hub.getHubType(),
                hub.getRegionId(),
                region != null ? region.getName() : null);
    }
}
