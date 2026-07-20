package com.orbit.app.request;

/** Action selected by the request escalation scheduler for its next wave. */
enum WaveAction {
    NONE,
    ESCALATE,
    REMIND,
    DEADLINE_WAVE
}
