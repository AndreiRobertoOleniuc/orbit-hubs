package com.orbit.domain.entity;

import jakarta.persistence.*;

/**
 * Global tag vocabulary (lookup, not owned by any aggregate). Maps table {@code tags}.
 */
@jakarta.persistence.Entity
@Table(name = "tags")
public class Tag {

    @Id
    private String slug;

    private String label;

    protected Tag() {
    }

    public Tag(String slug, String label) {
        this.slug = slug;
        this.label = label;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
