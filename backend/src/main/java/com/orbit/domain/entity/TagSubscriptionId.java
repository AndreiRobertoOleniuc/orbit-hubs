package com.orbit.domain.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for {@link TagSubscription} — (entity_id, tag_id).
 */
public class TagSubscriptionId implements Serializable {

    private Long entityId;
    private Long tagId;

    public TagSubscriptionId() {
    }

    public TagSubscriptionId(Long entityId, Long tagId) {
        this.entityId = entityId;
        this.tagId = tagId;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public Long getTagId() {
        return tagId;
    }

    public void setTagId(Long tagId) {
        this.tagId = tagId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TagSubscriptionId that)) return false;
        return Objects.equals(entityId, that.entityId) && Objects.equals(tagId, that.tagId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityId, tagId);
    }
}
