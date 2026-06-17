package com.orbit.domain.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.*;

import org.hibernate.annotations.Array;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

/**
 * The Anfrage — demand-side, first-class, searchable. Maps table {@code requests}.
 *
 * <p>Aggregate root over its {@code request_transitions} lifecycle audit log (children, since
 * status must stay consistent with the latest transition). Request text is embedded whole (not
 * chunked) directly on this row.
 */
@jakarta.persistence.Entity
@Table(name = "requests")
public class Request {

    @Id
    private String id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_entity_id")
    private Entity owner;

    @Column(name = "originator_type")
    private String originatorType;

    @Column(nullable = false)
    private String scope;

    @Column(name = "presence_mode")
    private String presenceMode;

    @Column(nullable = false)
    private String status;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 1024)
    private float[] embedding;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC, id ASC")
    private List<RequestTransition> transitions = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "request_tags",
            joinColumns = @JoinColumn(name = "request_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_slug"))
    private Set<Tag> tags = new HashSet<>();

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Generated(event = EventType.INSERT)
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Request() {
    }

    public Request(String id, String title, String description, String scope, String status) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.scope = scope;
        this.status = status;
    }

    /** Append a transition row and move the request into its new status. */
    public RequestTransition addTransition(String fromStatus, String toStatus, User actor, String note) {
        RequestTransition transition = new RequestTransition(this, fromStatus, toStatus, actor, note);
        transitions.add(transition);
        this.status = toStatus;
        return transition;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Entity getOwner() {
        return owner;
    }

    public void setOwner(Entity owner) {
        this.owner = owner;
    }

    public String getOriginatorType() {
        return originatorType;
    }

    public void setOriginatorType(String originatorType) {
        this.originatorType = originatorType;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(LocalDate validUntil) {
        this.validUntil = validUntil;
    }

    public List<RequestTransition> getTransitions() {
        return transitions;
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
