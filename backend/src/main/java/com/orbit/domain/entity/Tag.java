package com.orbit.domain.entity;

import jakarta.persistence.*;

/**
 * A tag in the controlled vocabulary. Tags are minted on demand during
 * ingestion (AI-derived) or when a user adds a custom tag. Normalized to a
 * slug so subscriptions and distribution join cleanly.
 * <p>
 * Maps table {@code tags}. See reference doc §9.
 */
@jakarta.persistence.Entity
@Table(name = "tags")
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @Column(name = "label", nullable = false)
    private String label;

    // created_at is DEFAULT now() in the DB — no @Generated needed.
    // @Generated caused PSQLException issues with certain Hibernate 7.x
    // configurations. The field stays null in Java after save; the DB sets it.
    @Column(name = "created_at", updatable = false, insertable = false)
    private java.time.Instant createdAt;

    protected Tag() {
    }

    public Tag(String slug, String label) {
        this.slug = slug;
        this.label = label;
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getLabel() {
        return label;
    }

    public java.time.Instant getCreatedAt() {
        return createdAt;
    }
}