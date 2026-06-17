package com.orbit.domain.entity;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.*;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

/**
 * "Offen für Anfragen" sub-record: a 1:1, shared-PK child of {@link Entity}. Maps table
 * {@code availability} (PK {@code entity_id} = the owning entity's id, via {@link MapsId}).
 */
@jakarta.persistence.Entity
@Table(name = "availability")
public class Availability {

    @Id
    @Column(name = "entity_id")
    private String entityId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    private Entity entity;

    @Column(name = "is_open", nullable = false)
    private boolean open;

    @Column(name = "available_from")
    private LocalDate availableFrom;

    @Column(name = "capacity_note")
    private String capacityNote;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "preferred_modes", columnDefinition = "text[]")
    private String[] preferredModes;

    @Generated(event = EventType.INSERT)
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Availability() {
    }

    public String getEntityId() {
        return entityId;
    }

    public Entity getEntity() {
        return entity;
    }

    public void setEntity(Entity entity) {
        this.entity = entity;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public LocalDate getAvailableFrom() {
        return availableFrom;
    }

    public void setAvailableFrom(LocalDate availableFrom) {
        this.availableFrom = availableFrom;
    }

    public String getCapacityNote() {
        return capacityNote;
    }

    public void setCapacityNote(String capacityNote) {
        this.capacityNote = capacityNote;
    }

    public String[] getPreferredModes() {
        return preferredModes;
    }

    public void setPreferredModes(String[] preferredModes) {
        this.preferredModes = preferredModes;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
