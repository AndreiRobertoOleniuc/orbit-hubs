package com.orbit.app.request;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Timing knobs for the urgency-driven escalation of distributed requests
 * (reference §8.3). All windows are config-driven so a demo can run the
 * 48-hour ladder in minutes ({@code ORBIT_REQUESTS_URGENT_ESCALATE_AFTER=PT2M}).
 *
 * @param pollInterval        how often the scheduler scans distributed requests
 * @param urgentEscalateAfter 'dringend': widen one level after this much silence
 * @param soonRemindAfter     'bald': one reminder wave after this much silence
 * @param deadlineWaveBefore  final wave this long before an explicit deadline
 */
@ConfigurationProperties(prefix = "orbit.requests")
public record RequestsProperties(
        @DefaultValue("PT5M") Duration pollInterval,
        @DefaultValue("PT48H") Duration urgentEscalateAfter,
        @DefaultValue("PT120H") Duration soonRemindAfter,
        @DefaultValue("PT24H") Duration deadlineWaveBefore) {
}
