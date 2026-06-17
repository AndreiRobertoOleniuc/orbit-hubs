package com.orbit.domain.entity;

import java.time.Instant;

import jakarta.persistence.*;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * One-time first-contact hand-off (no chat thread). Maps table {@code contact_handoffs}
 * ({@code bigserial} PK → {@link Long}). An append-only log; the platform's role ends here.
 */
@jakarta.persistence.Entity
@Table(name = "contact_handoffs")
public class ContactHandoff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nullable — a contact can originate from search, not only from a request. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id")
    private Request request;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_user_id")
    private User fromUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_entity_id")
    private Entity toEntity;

    private String message;

    @Column(name = "delivered_email")
    private String deliveredEmail;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected ContactHandoff() {
    }

    public ContactHandoff(Request request, User fromUser, Entity toEntity, String message, String deliveredEmail) {
        this.request = request;
        this.fromUser = fromUser;
        this.toEntity = toEntity;
        this.message = message;
        this.deliveredEmail = deliveredEmail;
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

    public User getFromUser() {
        return fromUser;
    }

    public void setFromUser(User fromUser) {
        this.fromUser = fromUser;
    }

    public Entity getToEntity() {
        return toEntity;
    }

    public void setToEntity(Entity toEntity) {
        this.toEntity = toEntity;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDeliveredEmail() {
        return deliveredEmail;
    }

    public void setDeliveredEmail(String deliveredEmail) {
        this.deliveredEmail = deliveredEmail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
