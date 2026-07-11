package com.orbit.domain.entity;

import jakarta.persistence.*;

/**
 * An affiliation hub (an institution, incubator, or city scene) belonging to
 * exactly one {@link Region}. Maps table {@code hubs}. See reference doc §5.
 * <p>
 * Setters exist for the admin hub-management surface (create/edit); the
 * {@code regionId} FK is a plain {@code Long} resolved via {@code RegionRepository}.
 */
@Entity
@Table(name = "hubs")
public class Hub {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "hub_type")
    private String hubType;

    @Column(name = "region_id", nullable = false)
    private Long regionId;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    protected Hub() {
    }

    public Hub(String slug, String name, String hubType, Long regionId) {
        this.slug = slug;
        this.name = name;
        this.hubType = hubType;
        this.regionId = regionId;
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHubType() {
        return hubType;
    }

    public void setHubType(String hubType) {
        this.hubType = hubType;
    }

    public Long getRegionId() {
        return regionId;
    }

    public void setRegionId(Long regionId) {
        this.regionId = regionId;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }
}
