package com.orbit.notification.internal;

import com.orbit.request.RequestQuery;

/**
 * Skeleton placeholder that wires the single allowed outgoing dependency of
 * the notification module: it may read request state via the published
 * {@link RequestQuery} interface.
 */
class NotificationPlaceholder {

    private final RequestQuery requests;

    NotificationPlaceholder(RequestQuery requests) {
        this.requests = requests;
    }

    void todo() {
        throw new UnsupportedOperationException("not implemented");
    }

}
