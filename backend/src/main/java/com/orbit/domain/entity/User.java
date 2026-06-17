package com.orbit.domain.entity;

import java.time.Instant;

import jakarta.persistence.*;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Authenticated account. Maps table {@code users}. Mutual FK with {@link Entity}: a user may
 * claim one profile ({@code entity_id}), and an entity may be owned by one user
 * ({@code entities.owner_user_id}) — two independent columns, not inverse sides.
 */
@jakarta.persistence.Entity
@Table(name = "users")
public class User {

    @Id
    private String id;

    @Column(nullable = false)
    private String email;

    @Column(name = "display_name")
    private String displayName;

    /** The profile this user claims; nullable until claimed. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id")
    private Entity entity;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected User() {
    }

    public User(String id, String email, String displayName) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Entity getEntity() {
        return entity;
    }

    public void setEntity(Entity entity) {
        this.entity = entity;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
