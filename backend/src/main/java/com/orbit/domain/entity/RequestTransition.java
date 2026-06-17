package com.orbit.domain.entity;

import java.time.Instant;

import jakarta.persistence.*;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Lifecycle audit row for a {@link Request}. Maps table {@code request_transitions}
 * ({@code bigserial} PK → {@link Long}). A child of its request; no independent identity.
 */
@jakarta.persistence.Entity
@Table(name = "request_transitions")
public class RequestTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private Request request;

    @Column(name = "from_status")
    private String fromStatus;

    @Column(name = "to_status")
    private String toStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actorUser;

    private String note;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected RequestTransition() {
    }

    public RequestTransition(Request request, String fromStatus, String toStatus, User actorUser, String note) {
        this.request = request;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorUser = actorUser;
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public Request getRequest() {
        return request;
    }

    public void setRequest(Request request) {
        this.request = request;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public User getActorUser() {
        return actorUser;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
