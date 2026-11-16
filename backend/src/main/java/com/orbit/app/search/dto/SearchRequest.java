package com.orbit.app.search.dto;

import java.time.LocalDate;

/**
 * A discovery search request. {@code query} is the only required field; the rest
 * are the UI filters (reference §7): {@code scope} is the Hub/Region hard filter
 * (§5), while {@code wantFrom}/{@code wantTo}/{@code wantPensumPct} are the
 * Zeitraum + Umfang soft re-rank signals (§6.3) — they reorder, never exclude.
 *
 * @param query         the natural-language query
 * @param matchCount    max entities to return (default 10)
 * @param scope         scope selection (null / {@code all} = unscoped)
 * @param wantFrom      requested availability window start (optional)
 * @param wantTo        requested availability window end (optional)
 * @param wantPensumPct requested Pensum % (optional)
 * @param debug         ask for the per-stage {@link SearchTrace}; honoured only for
 *                      admins or when {@code orbit.search.trace-open} is set (optional)
 */
public record SearchRequest(
        String query,
        Integer matchCount,
        ScopeSelection scope,
        LocalDate wantFrom,
        LocalDate wantTo,
        Integer wantPensumPct,
        Boolean debug) {
}
