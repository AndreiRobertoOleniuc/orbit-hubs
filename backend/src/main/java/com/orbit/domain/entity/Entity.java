package com.orbit.domain.entity;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.*;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * The thing you match — a person or company. Maps table {@code entities}.
 *
 * <p>Owns its small, fixed-cardinality children: {@link Availability} (1:1 shared PK) and the
 * {@code entity_tags} join to the shared {@link Tag} vocabulary. Documents are deliberately
 * <em>not</em> a child collection here — they are high-cardinality and stand on their own
 * (see {@link Document}).
 */
@jakarta.persistence.Entity
@Table(name = "entities")
public class Entity {

    @Id
    private String id;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String name;

    private String headline;

    private String summary;

    private String location;

    @Column(name = "location_lat")
    private Double locationLat;

    @Column(name = "location_lng")
    private Double locationLng;

    @Column(nullable = false)
    private String scope;

    @Column(name = "presence_mode")
    private String presenceMode;

    @Column(nullable = false)
    private boolean verified;

    @Column(name = "handelsregister_uid")
    private String handelsregisterUid;

    /** Owning account; nullable until claimed. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private User owner;

    @OneToOne(mappedBy = "entity", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Availability availability;

    @ManyToMany
    @JoinTable(name = "entity_tags",
            joinColumns = @JoinColumn(name = "entity_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_slug"))
    private Set<Tag> tags = new HashSet<>();

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Generated(event = EventType.INSERT)
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Entity() {
    }

    public Entity(String id, String type, String name) {
        this.id = id;
        this.type = type;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHeadline() {
        return headline;
    }

    public void setHeadline(String headline) {
        this.headline = headline;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Double getLocationLat() {
        return locationLat;
    }

    public void setLocationLat(Double locationLat) {
        this.locationLat = locationLat;
    }

    public Double getLocationLng() {
        return locationLng;
    }

    public void setLocationLng(Double locationLng) {
        this.locationLng = locationLng;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getPresenceMode() {
        return presenceMode;
    }

    public void setPresenceMode(String presenceMode) {
        this.presenceMode = presenceMode;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public String getHandelsregisterUid() {
        return handelsregisterUid;
    }

    public void setHandelsregisterUid(String handelsregisterUid) {
        this.handelsregisterUid = handelsregisterUid;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public Availability getAvailability() {
        return availability;
    }

    public void setAvailability(Availability availability) {
        if (availability != null) {
            availability.setEntity(this);
        }
        this.availability = availability;
    }

    public Set<Tag> getTags() {
        return tags;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
