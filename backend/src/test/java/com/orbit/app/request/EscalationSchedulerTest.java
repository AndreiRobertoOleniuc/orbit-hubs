package com.orbit.app.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import com.orbit.domain.entity.Request;
import com.orbit.domain.repository.RequestRepository;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the per-request escalation decision (§8.3) — the pure
 * {@code decide()} method, no Spring context, scheduler or DB.
 */
class EscalationSchedulerTest {

    private final RequestsProperties properties = new RequestsProperties(
            Duration.ofMinutes(5),   // pollInterval (unused by decide)
            Duration.ofHours(48),    // urgentEscalateAfter
            Duration.ofHours(120),   // soonRemindAfter
            Duration.ofHours(24));   // deadlineWaveBefore

    private final EscalationScheduler scheduler = new EscalationScheduler(
            mock(RequestRepository.class), mock(DistributionService.class), properties);

    private final Instant now = Instant.parse("2026-07-16T12:00:00Z");

    private Request request(String urgency, LocalDate deadline, Duration sinceLastWave) {
        Request r = new Request("user_a", "t", null, "q", "hub", 10L, null,
                urgency, deadline, null, null, null);
        Instant wave = now.minus(sinceLastWave);
        r.setLastWaveAt(wave);
        r.setDistributedAt(wave);
        return r;
    }

    // ─── urgent ───

    @Test
    void urgentEscalatesAfterSilenceWindow() {
        Request r = request("urgent", null, Duration.ofHours(49));
        assertThat(scheduler.decide(r, now, true)).isEqualTo(WaveAction.ESCALATE);
    }

    @Test
    void urgentWaitsWithinSilenceWindow() {
        Request r = request("urgent", null, Duration.ofHours(47));
        assertThat(scheduler.decide(r, now, true)).isEqualTo(WaveAction.NONE);
    }

    @Test
    void urgentStopsWhenScopeCannotWiden() {
        Request r = request("urgent", null, Duration.ofHours(49));
        assertThat(scheduler.decide(r, now, false)).isEqualTo(WaveAction.NONE);
    }

    // ─── soon ───

    @Test
    void soonRemindsOnceAfterWindow() {
        Request r = request("soon", null, Duration.ofHours(121));
        assertThat(scheduler.decide(r, now, true)).isEqualTo(WaveAction.REMIND);
    }

    @Test
    void soonReminderIsOneShot() {
        Request r = request("soon", null, Duration.ofHours(121));
        r.setReminderSent(true);
        assertThat(scheduler.decide(r, now, true)).isEqualTo(WaveAction.NONE);
    }

    // ─── flexible ───

    @Test
    void flexibleNeverEscalates() {
        Request r = request("flexible", null, Duration.ofDays(30));
        assertThat(scheduler.decide(r, now, true)).isEqualTo(WaveAction.NONE);
    }

    // ─── deadline ───

    @Test
    void deadlineWaveFiresInsideFinalWindow() {
        // Deadline tomorrow (< 24h away), last wave long before the window.
        LocalDate deadline = LocalDate.ofInstant(now, ZoneId.systemDefault()).plusDays(1);
        Request r = request("flexible", deadline, Duration.ofDays(10));
        assertThat(scheduler.decide(r, now, true)).isEqualTo(WaveAction.DEADLINE_WAVE);
    }

    @Test
    void deadlineWaveIsOneShot() {
        LocalDate deadline = LocalDate.ofInstant(now, ZoneId.systemDefault()).plusDays(1);
        Request r = request("flexible", deadline, Duration.ofHours(1)); // wave already inside window
        assertThat(scheduler.decide(r, now, true)).isEqualTo(WaveAction.NONE);
    }

    @Test
    void nothingRunsPastDeadline() {
        LocalDate deadline = LocalDate.ofInstant(now, ZoneId.systemDefault()).minusDays(1);
        Request r = request("urgent", deadline, Duration.ofDays(10));
        assertThat(scheduler.decide(r, now, true)).isEqualTo(WaveAction.NONE);
    }

    @Test
    void neverDistributedRequestIsIgnored() {
        Request r = new Request("user_a", "t", null, "q", "all", null, null,
                "urgent", null, null, null, null);
        assertThat(scheduler.decide(r, now, true)).isEqualTo(WaveAction.NONE);
    }
}
