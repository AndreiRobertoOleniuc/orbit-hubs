package com.orbit.app.inference;

import java.util.OptionalLong;
import java.util.function.Supplier;

/**
 * A per-thread deadline for hosted inference calls made on behalf of someone
 * who is waiting — a search request.
 * <p>
 * A hosted model that is overloaded or rate-limited is retried for up to
 * {@code orbit.openrouter.max-wait} (2 minutes). That suits a background job;
 * nobody waits that long for search results. Work wrapped in {@link #within}
 * gives up at the deadline instead, with an
 * {@link InferenceUnavailableException} (HTTP 503), and the client can retry
 * against a model that has warmed up meanwhile.
 * <p>
 * The deadline belongs to the calling thread: wrap the call again inside any
 * task handed to another thread.
 */
public final class InferenceDeadline {

    private static final ThreadLocal<Long> DEADLINE_NANOS = new ThreadLocal<>();

    private InferenceDeadline() {
    }

    /** Run {@code work} with inference calls on this thread bounded by {@code deadlineNanos} ({@link System#nanoTime}). */
    public static <T> T within(long deadlineNanos, Supplier<T> work) {
        Long previous = DEADLINE_NANOS.get();
        DEADLINE_NANOS.set(deadlineNanos);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                DEADLINE_NANOS.remove();
            } else {
                DEADLINE_NANOS.set(previous);
            }
        }
    }

    /** The deadline in force on this thread, if any. */
    public static OptionalLong current() {
        Long deadline = DEADLINE_NANOS.get();
        return deadline == null ? OptionalLong.empty() : OptionalLong.of(deadline);
    }
}
