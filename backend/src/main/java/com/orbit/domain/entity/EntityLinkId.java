package com.orbit.domain.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for {@link EntityLink} — (person_entity_id, org_entity_id).
 */
public class EntityLinkId implements Serializable {

    private Long personEntityId;
    private Long orgEntityId;

    public EntityLinkId() {
    }

    public EntityLinkId(Long personEntityId, Long orgEntityId) {
        this.personEntityId = personEntityId;
        this.orgEntityId = orgEntityId;
    }

    public Long getPersonEntityId() {
        return personEntityId;
    }

    public void setPersonEntityId(Long personEntityId) {
        this.personEntityId = personEntityId;
    }

    public Long getOrgEntityId() {
        return orgEntityId;
    }

    public void setOrgEntityId(Long orgEntityId) {
        this.orgEntityId = orgEntityId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EntityLinkId that)) return false;
        return Objects.equals(personEntityId, that.personEntityId)
                && Objects.equals(orgEntityId, that.orgEntityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(personEntityId, orgEntityId);
    }
}
