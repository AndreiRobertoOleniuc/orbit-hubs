package com.orbit.app.search;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.orbit.app.search.dto.ResolvedScope;
import com.orbit.app.search.dto.ScopeSelection;
import com.orbit.domain.entity.Hub;
import com.orbit.domain.entity.Region;
import com.orbit.domain.repository.HubRepository;
import com.orbit.domain.repository.RegionRepository;

import org.springframework.stereotype.Service;

/**
 * Resolves a {@link ScopeSelection} (all / hub / region) into the two id arrays
 * the scoped {@code hybrid_search} function consumes (reference §5.3).
 * <p>
 * Widening is <b>strict</b> (§5.4 — the user decides when to widen, via the UI):
 * this resolver never auto-widens. A <b>region</b> scope resolves to the region
 * subtree <i>plus</i> every hub anchored in that subtree, so that widening a hub
 * up to its region never drops a previously-matched hub-only entity (monotonic
 * widen). The region tree is shallow; it is walked in-app rather than with a
 * recursive CTE, matching the repository-driven style used across the codebase.
 */
@Service
public class ScopeResolver {

    private static final ResolvedScope UNSCOPED = new ResolvedScope(new long[0], new long[0]);

    private final RegionRepository regions;
    private final HubRepository hubs;

    public ScopeResolver(RegionRepository regions, HubRepository hubs) {
        this.regions = regions;
        this.hubs = hubs;
    }

    public ResolvedScope resolve(ScopeSelection selection) {
        if (selection == null || selection.kind() == null || selection.kind().isBlank()) {
            return UNSCOPED;
        }
        String kind = selection.kind().trim().toLowerCase();
        return switch (kind) {
            case "all" -> UNSCOPED;
            case "hub" -> selection.hubId() == null
                    ? UNSCOPED
                    : new ResolvedScope(new long[] { selection.hubId() }, new long[0]);
            case "region" -> resolveRegion(selection.regionId());
            default -> throw new IllegalArgumentException("Unknown scope kind: " + selection.kind());
        };
    }

    private ResolvedScope resolveRegion(Long regionId) {
        if (regionId == null) return UNSCOPED;
        List<Long> subtree = subtree(regionId);
        List<Long> hubIds = hubs.findByRegionIdIn(subtree).stream().map(Hub::getId).toList();
        return new ResolvedScope(toArray(hubIds), toArray(subtree));
    }

    /** The region plus all descendant regions (reference §5.3 {@code subtree(R)}). */
    private List<Long> subtree(Long rootId) {
        Map<Long, List<Long>> childrenByParent = new HashMap<>();
        for (Region r : regions.findAll()) {
            if (r.getParentRegionId() != null) {
                childrenByParent.computeIfAbsent(r.getParentRegionId(), k -> new ArrayList<>()).add(r.getId());
            }
        }
        List<Long> result = new ArrayList<>();
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(rootId);
        while (!stack.isEmpty()) {
            Long id = stack.pop();
            if (result.contains(id)) continue;
            result.add(id);
            for (Long child : childrenByParent.getOrDefault(id, List.of())) {
                stack.push(child);
            }
        }
        return result;
    }

    private static long[] toArray(List<Long> ids) {
        long[] out = new long[ids.size()];
        for (int i = 0; i < ids.size(); i++) out[i] = ids.get(i);
        return out;
    }
}
