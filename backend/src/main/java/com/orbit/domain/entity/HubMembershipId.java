package com.orbit.domain.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for {@link HubMembership} — (entity_id, hub_id).
 */
public class HubMembershipId implements Serializable {

    private Long entityId;
    private Long hubId;

    public HubMembershipId() {
    }

    public HubMembershipId(Long entityId, Long hubId) {
        this.entityId = entityId;
        this.hubId = hubId;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public Long getHubId() {
        return hubId;
    }

    public void setHubId(Long hubId) {
        this.hubId = hubId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HubMembershipId that)) return false;
        return Objects.equals(entityId, that.entityId) && Objects.equals(hubId, that.hubId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityId, hubId);
    }
}
