package com.orbit.app.search.dto;

/**
 * A {@link ScopeSelection} resolved to the two id arrays that the scoped
 * {@code hybrid_search} function consumes (reference §5.3, §6.2). Both empty =
 * unscoped ("Alle").
 *
 * @param hubIds    hubs in scope (empty = no hub constraint)
 * @param regionIds region subtree in scope (empty = no region constraint)
 */
public record ResolvedScope(long[] hubIds, long[] regionIds) {

    public boolean isUnscoped() {
        return hubIds.length == 0 && regionIds.length == 0;
    }
}
