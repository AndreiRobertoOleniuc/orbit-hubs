package com.orbit.app.search.dto;

/**
 * A search scope selection (reference §5.3). {@code kind} is one of
 * {@code all} | {@code hub} | {@code region}; the matching id is set for the
 * hub / region kinds. {@code all} (or a null selection) applies no filter.
 *
 * @param kind     scope kind: {@code all} | {@code hub} | {@code region}
 * @param hubId    hub id when kind = hub
 * @param regionId region id when kind = region (its subtree is included)
 */
public record ScopeSelection(
        String kind,
        Long hubId,
        Long regionId) {
}
