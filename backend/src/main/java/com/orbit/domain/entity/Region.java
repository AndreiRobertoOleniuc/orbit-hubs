package com.orbit.domain.entity;

import jakarta.persistence.*;

/**
 * A node in the region tree (CH → Aargau → Region Baden → …). Maps table
 * {@code regions}. The root (CH) has a null {@code parentRegionId}.
 * <p>
 * The parent FK is a plain {@code Long} resolved via {@code RegionRepository}
 * rather than a JPA self-association, matching the repository-driven style used
 * across the codebase (subtrees are walked in the app/SQL layer, not eager-fetched).
 * See reference doc §5.
 */
@Entity
@Table(name = "regions")
public class Region {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "parent_region_id")
    private Long parentRegionId;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    protected Region() {
    }

    public Region(String slug, String name, Long parentRegionId) {
        this.slug = slug;
        this.name = name;
        this.parentRegionId = parentRegionId;
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public Long getParentRegionId() {
        return parentRegionId;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }
}
