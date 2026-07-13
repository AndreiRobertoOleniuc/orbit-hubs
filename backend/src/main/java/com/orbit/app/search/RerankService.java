package com.orbit.app.search;

import java.time.LocalDate;
import java.util.List;

import com.orbit.domain.entity.DiscoveryEntity;

import org.springframework.stereotype.Service;

/**
 * Re-ranking stage (reference §6.3) — the soft signals that reorder, but never
 * remove, retrieved candidates (§7). Only the signals the search UI exposes are
 * wired: availability-window / Pensum overlap (the Zeitraum + Umfang filters) and
 * the "Offen für Anfragen" boost. Applied as a <strong>multiplicative</strong> boost
 * on the cross-encoder score after the CE has filtered irrelevant candidates:
 * {@code combined = ceScore * (1 + W_avail * overlap + W_open * open)}. Weights
 * come from {@link RerankProperties}.
 */
@Service
public class RerankService {

    private static final double NEUTRAL = 0.5;

    private final RerankProperties props;

    public RerankService(RerankProperties props) {
        this.props = props;
    }

    /**
     * Compute the multiplicative soft-boost factor for each candidate, aligned
     * by index with {@code entities}. Returns an array of
     * {@code (1 + W_avail * overlap + W_open * open)} values. The caller multiplies
     * the cross-encoder score by this factor to get the final combined score.
     * <p>
     * This runs <em>after</em> the cross-encoder so soft signals fine-tune the
     * order of relevant candidates, not determine who passes the relevance filter.
     * The boost scales with the CE score implicitly (caller multiplies), so a
     * weak match gets less absolute boost than a strong match — availability
     * matters more for relevant candidates, which is the right behaviour.
     */
    public double[] boostFactors(List<DiscoveryEntity> entities,
                                LocalDate wantFrom, LocalDate wantTo, Integer wantPensum) {
        double[] out = new double[entities.size()];
        for (int i = 0; i < out.length; i++) {
            DiscoveryEntity e = entities.get(i);
            double avail = availabilityOverlap(wantFrom, wantTo, wantPensum, e);
            double open = e.isOpenToRequests() ? 1.0 : 0.0;
            out[i] = 1.0
                    + props.availabilityWeight() * avail
                    + props.openToRequestsWeight() * open;
        }
        return out;
    }

    /**
     * Availability overlap (§6.3 / spec §6): fraction of the requested window
     * covered by the entity's availability window, scaled by Pensum compatibility.
     * Returns a neutral {@code 0.5} when there is nothing to compare (no window on
     * either side) — never negative, so an unset window can't push a strong match down.
     */
    public double availabilityOverlap(LocalDate wantFrom, LocalDate wantTo, Integer wantPensum,
                                      DiscoveryEntity entity) {
        LocalDate availFrom = entity.getAvailableFrom();
        LocalDate availTo = entity.getAvailableTo();
        Integer pensum = entity.getPensumPct();

        boolean noWant = wantFrom == null && wantTo == null && wantPensum == null;
        boolean noAvail = availFrom == null && availTo == null && pensum == null;
        if (noWant || noAvail) return NEUTRAL;

        double dateFraction = dateOverlapFraction(wantFrom, wantTo, availFrom, availTo);
        double pensumFactor = pensumCompatibility(wantPensum, pensum);
        return clamp01(dateFraction * pensumFactor);
    }

    /**
     * Fraction of the requested date window covered by the entity's availability.
     * Missing want-bounds fall back to a bounded ±6-month horizon so the fraction
     * stays finite; missing availability bounds are treated as open (−∞ / +∞).
     * No requested dates at all ⇒ 1.0 (only Pensum matters).
     */
    private static double dateOverlapFraction(LocalDate wantFrom, LocalDate wantTo,
                                              LocalDate availFrom, LocalDate availTo) {
        if (wantFrom == null && wantTo == null) return 1.0;

        long wantStart = (wantFrom != null ? wantFrom
                : wantTo.minusMonths(6)).toEpochDay();
        long wantEnd = (wantTo != null ? wantTo
                : wantFrom.plusMonths(6)).toEpochDay();
        if (wantEnd <= wantStart) return 0.0;

        long availStart = availFrom != null ? availFrom.toEpochDay() : Long.MIN_VALUE / 4;
        long availEnd = availTo != null ? availTo.toEpochDay() : Long.MAX_VALUE / 4;

        long overlap = Math.min(wantEnd, availEnd) - Math.max(wantStart, availStart);
        if (overlap <= 0) return 0.0;
        return clamp01((double) overlap / (double) (wantEnd - wantStart));
    }

    /** min(pensum, want)/want; unknown on either side ⇒ 1.0 (don't penalize). */
    private static double pensumCompatibility(Integer wantPensum, Integer pensum) {
        if (wantPensum == null || wantPensum <= 0 || pensum == null) return 1.0;
        return clamp01((double) Math.min(pensum, wantPensum) / (double) wantPensum);
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}
