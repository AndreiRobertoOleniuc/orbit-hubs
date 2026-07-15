package com.orbit.app.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.util.List;

import com.orbit.domain.entity.DiscoveryEntity;

import org.junit.jupiter.api.Test;

/** Unit tests for the re-rank soft signals (§6.3) — no Spring context or DB. */
class RerankServiceTest {

    private final RerankService rerank = new RerankService(new RerankProperties(0.3, 0.1));

    private static DiscoveryEntity entity(LocalDate from, LocalDate to, Integer pensum) {
        DiscoveryEntity e = new DiscoveryEntity("person", "X", null, null);
        e.setAvailableFrom(from);
        e.setAvailableTo(to);
        e.setPensumPct(pensum);
        return e;
    }

    @Test
    void neutralWhenNoRequestedWindow() {
        DiscoveryEntity e = entity(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31), 100);
        assertThat(rerank.availabilityOverlap(null, null, null, e)).isEqualTo(0.5);
    }

    @Test
    void neutralWhenEntityHasNoAvailability() {
        DiscoveryEntity e = entity(null, null, null);
        assertThat(rerank.availabilityOverlap(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 10, 31), 60, e)).isEqualTo(0.5);
    }

    @Test
    void fullOverlapWithCompatiblePensum() {
        DiscoveryEntity e = entity(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31), 100);
        double overlap = rerank.availabilityOverlap(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 10, 31), 60, e);
        assertThat(overlap).isCloseTo(1.0, within(0.001));
    }

    @Test
    void partialDateOverlapReducesScore() {
        // Entity only free Sep–Oct; request is Aug–Oct → ~2/3 covered.
        DiscoveryEntity e = entity(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31), 100);
        double overlap = rerank.availabilityOverlap(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 10, 31), 60, e);
        assertThat(overlap).isBetween(0.5, 0.8);
    }

    @Test
    void pensumBelowRequestScalesDown() {
        DiscoveryEntity e = entity(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31), 40);
        double overlap = rerank.availabilityOverlap(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 10, 31), 80, e);
        assertThat(overlap).isCloseTo(0.5, within(0.01)); // full date × 40/80
    }

    @Test
    void openToRequestsBoostRanksOpenProfileHigher() {
        DiscoveryEntity open = entity(null, null, null);
        open.setOpenToRequests(true);
        DiscoveryEntity closed = entity(null, null, null);
        closed.setOpenToRequests(false);
        double[] factors = rerank.boostFactors(List.of(open, closed), null, null, null);
        assertThat(factors[0]).isGreaterThan(factors[1]);
    }

    @Test
    void zeroOverlapClosedProfileGetsNoBoostButNoPenalty() {
        // Availability entirely outside the requested window → overlap 0; the
        // factor bottoms out at exactly 1.0 (soft signals reorder, never punish).
        DiscoveryEntity e = entity(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), 10);
        e.setOpenToRequests(false);
        double[] factors = rerank.boostFactors(List.of(e),
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 10, 31), 80);
        assertThat(factors[0]).isEqualTo(1.0);
    }

    @Test
    void fullAvailabilityAndOpenYieldsMaximalBoost() {
        DiscoveryEntity e = entity(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31), 100);
        e.setOpenToRequests(true);
        double[] factors = rerank.boostFactors(List.of(e),
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 10, 31), 60);
        // 1 + W_avail * 1.0 + W_open * 1.0 with the weights from the constructor
        assertThat(factors[0]).isCloseTo(1.4, within(0.001));
    }
}
