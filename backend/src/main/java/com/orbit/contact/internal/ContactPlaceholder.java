package com.orbit.contact.internal;

import com.orbit.notification.NotificationApi;
import com.orbit.request.RequestQuery;

/**
 * Skeleton placeholder that wires the allowed outgoing dependencies of the
 * contact module: it reads requests and notifies interested parties, but only
 * via published interfaces.
 */
class ContactPlaceholder {

    private final RequestQuery requests;
    private final NotificationApi notifications;

    ContactPlaceholder(RequestQuery requests, NotificationApi notifications) {
        this.requests = requests;
        this.notifications = notifications;
    }

    void todo() {
        throw new UnsupportedOperationException("not implemented");
    }

}
