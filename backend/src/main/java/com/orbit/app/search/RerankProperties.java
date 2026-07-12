package com.orbit.app.search;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Weights for the soft re-rank signals (reference §6.3). Additive boosts on the
 * min-max-normalized base RRF score, kept small so relevance dominates. A/B-able
 * via {@code orbit.search.rerank.*}. Presence / proximity / hub-overlap are not
 * wired yet (not exposed by the search UI), so no weights for them here.
 *
 * @param availabilityWeight   boost for availability-window / Pensum overlap
 * @param openToRequestsWeight boost for the "Offen für Anfragen" flag
 */
@ConfigurationProperties(prefix = "orbit.search.rerank")
public record RerankProperties(
        @DefaultValue("0.3") double availabilityWeight,
        @DefaultValue("0.1") double openToRequestsWeight) {
}
