package com.orbit.app.request;

import java.time.Instant;
import java.time.ZoneId;

import com.orbit.domain.entity.Request;
import com.orbit.domain.repository.RequestRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Urgency-driven escalation of distributed requests (reference §8.3) —
 * regelbasiert, no intelligent routing. Polls requests in status
 * {@code distributed}; since the first contact auto-transitions a request to
 * {@code contacted}, "still distributed" is exactly "no contact yet".
 * <ul>
 * <li><b>urgent</b> — after {@code urgentEscalateAfter} of silence since the
 * last wave, widen one level (Hub → Region → CH) and re-distribute.</li>
 * <li><b>soon</b> — one reminder wave after {@code soonRemindAfter}.</li>
 * <li><b>flexible</b> — no escalation; the request rests on the board.</li>
 * <li><b>deadline</b> — any urgency: a final wave {@code deadlineWaveBefore}
 * before the deadline; past the deadline nothing runs.</li>
 * </ul>
 * All windows come from {@link RequestsProperties} ({@code orbit.requests.*}).
 */
@Component
public class EscalationScheduler {

    private static final Logger log = LoggerFactory.getLogger(EscalationScheduler.class);

    /** What the scheduler should do with one distributed request right now. */
    enum WaveAction { NONE, ESCALATE, REMIND, DEADLINE_WAVE }

    private final RequestRepository requestRepository;
    private final DistributionService distributionService;
    private final RequestsProperties properties;

    public EscalationScheduler(RequestRepository requestRepository,
                               DistributionService distributionService,
                               RequestsProperties properties) {
        this.requestRepository = requestRepository;
        this.distributionService = distributionService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${orbit.requests.poll-interval:PT5M}")
    public void poll() {
        Instant now = Instant.now();
        for (Request request : requestRepository.findByStatusOrderByCreatedAtDesc("distributed")) {
            try {
                apply(request, decide(request, now, distributionService.canWiden(request)));
            } catch (RuntimeException ex) {
                log.warn("Escalation failed for request {}: {}", request.getId(), ex.getMessage(), ex);
            }
        }
    }

    private void apply(Request request, WaveAction action) {
        switch (action) {
            case ESCALATE -> {
                request.setEscalationLevel(request.getEscalationLevel() + 1);
                int notified = distributionService.distribute(request);
                log.info("Request {} escalated to level {} ({}) — {} newly notified",
                        request.getId(), request.getEscalationLevel(),
                        distributionService.effectiveScope(request).kind(), notified);
            }
            case REMIND -> {
                request.setReminderSent(true);
                int notified = distributionService.distribute(request);
                log.info("Request {} reminder wave — {} newly notified", request.getId(), notified);
            }
            case DEADLINE_WAVE -> {
                int notified = distributionService.distribute(request);
                log.info("Request {} deadline wave — {} newly notified", request.getId(), notified);
            }
            case NONE -> { }
        }
    }

    /**
     * Pure decision per request — package-private for unit testing. The
     * one-shot guards are structural: escalation stops once the scope can't
     * widen, the reminder flips {@code reminderSent}, and the deadline wave
     * only fires while {@code lastWaveAt} still lies before the wave window.
     */
    WaveAction decide(Request request, Instant now, boolean canWiden) {
        Instant lastWave = request.getLastWaveAt();
        if (lastWave == null) return WaveAction.NONE;

        Instant deadline = request.getDeadline() != null
                ? request.getDeadline().atStartOfDay(ZoneId.systemDefault()).toInstant()
                : null;
        if (deadline != null && !now.isBefore(deadline)) {
            return WaveAction.NONE; // Frist abgelaufen — nothing escalates anymore
        }

        if ("urgent".equals(request.getUrgency()) && canWiden
                && now.isAfter(lastWave.plus(properties.urgentEscalateAfter()))) {
            return WaveAction.ESCALATE;
        }

        if ("soon".equals(request.getUrgency()) && !request.isReminderSent()
                && request.getDistributedAt() != null
                && now.isAfter(request.getDistributedAt().plus(properties.soonRemindAfter()))) {
            return WaveAction.REMIND;
        }

        if (deadline != null) {
            Instant waveWindowStart = deadline.minus(properties.deadlineWaveBefore());
            if (!now.isBefore(waveWindowStart) && lastWave.isBefore(waveWindowStart)) {
                return WaveAction.DEADLINE_WAVE;
            }
        }

        return WaveAction.NONE;
    }
}
