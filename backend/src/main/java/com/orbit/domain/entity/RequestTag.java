package com.orbit.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Request↔Tag join — the tags that steer distribution (§8.2).
 * Distribution matches entities whose {@code entity_tags} or
 * {@code tag_subscriptions} overlap with these tags.
 * Follows the composite-key pattern (see {@link HubMembership}).
 */
@Entity
@Table(name = "request_tags")
@IdClass(RequestTagId.class)
public class RequestTag {

    @Id
    @Column(name = "request_id")
    private Long requestId;

    @Id
    @Column(name = "tag_id")
    private Long tagId;

    protected RequestTag() {
    }

    public RequestTag(Long requestId, Long tagId) {
        this.requestId = requestId;
        this.tagId = tagId;
    }

    public Long getRequestId() { return requestId; }
    public Long getTagId() { return tagId; }
}