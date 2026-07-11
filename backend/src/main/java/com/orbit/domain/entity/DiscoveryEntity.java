package com.orbit.domain.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.persistence.*;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

/**
 * The matchable thing — a person or organization. Maps table {@code entities}.
 * <p>
 * Every entity has one or more {@link Source sources}; each source is split into
 * {@link Chunk chunks} that carry the embedding and full-text vectors. This is
 * the three-tier hierarchy at the core of the Orbit Discovery Engine.
 */
@jakarta.persistence.Entity
@Table(name = "entities")
public class DiscoveryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "headline")
    private String headline;

    @Column(name = "org_name")
    private String orgName;

    /**
     * Rich, LLM-generated competence description (3-5 sentences). Re-generated
     * after each ingestion, aggregating ALL sources of the entity. Separate
     * from {@code headline} which is a short one-liner set at creation time.
     */
    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "owner_id")
    private String ownerId;

    @Column(name = "status", nullable = false)
    private String status = "active";

    /** 'supply' | 'demand' — drives mode-dependent profile rendering (spec §5.1). */
    @Column(name = "profile_mode", nullable = false)
    private String profileMode = "supply";

    // ── Supply-side profile (V6): structured availability + affiliation ──

    /** "Offen für Anfragen" toggle — nudges open profiles up in re-ranking (§4.2). */
    @Column(name = "open_to_requests", nullable = false)
    private boolean openToRequests = false;

    @Column(name = "available_from")
    private LocalDate availableFrom;

    @Column(name = "available_to")
    private LocalDate availableTo;

    /** Capacity 0–100 (%). */
    @Column(name = "pensum_pct")
    private Integer pensumPct;

    @Column(name = "availability_note")
    private String availabilityNote;

    /** Bachelorarbeit | Innosuisse | Mandat | Forschung | Praktikum | … */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "preferred_collaboration", columnDefinition = "text[]")
    private List<String> preferredCollaboration;

    /** DE | EN | FR working languages. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "languages", columnDefinition = "text[]")
    private List<String> languages;

    /** 'company' | 'self_employed' | 'private' — every person has a shown origin (§10). */
    @Column(name = "affiliation_kind")
    private String affiliationKind;

    @Column(name = "location_label")
    private String locationLabel;

    /** Region anchor (V7) — FK to regions(id), resolved via RegionRepository. */
    @Column(name = "home_region_id")
    private Long homeRegionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Generated(event = EventType.INSERT)
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected DiscoveryEntity() {
    }

    public DiscoveryEntity(String entityType, String displayName, String headline, String orgName) {
        this.entityType = entityType;
        this.displayName = displayName;
        this.headline = headline;
        this.orgName = orgName;
    }

    public Long getId() {
        return id;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getHeadline() {
        return headline;
    }

    public String getOrgName() {
        return orgName;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
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

    public String getStatus() {
        return status;
    }

    public String getProfileMode() {
        return profileMode;
    }

    public void setProfileMode(String profileMode) {
        this.profileMode = profileMode;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    // ── Supply-side profile accessors ──

    public boolean isOpenToRequests() {
        return openToRequests;
    }

    public void setOpenToRequests(boolean openToRequests) {
        this.openToRequests = openToRequests;
    }

    public LocalDate getAvailableFrom() {
        return availableFrom;
    }

    public void setAvailableFrom(LocalDate availableFrom) {
        this.availableFrom = availableFrom;
    }

    public LocalDate getAvailableTo() {
        return availableTo;
    }

    public void setAvailableTo(LocalDate availableTo) {
        this.availableTo = availableTo;
    }

    public Integer getPensumPct() {
        return pensumPct;
    }

    public void setPensumPct(Integer pensumPct) {
        this.pensumPct = pensumPct;
    }

    public String getAvailabilityNote() {
        return availabilityNote;
    }

    public void setAvailabilityNote(String availabilityNote) {
        this.availabilityNote = availabilityNote;
    }

    public List<String> getPreferredCollaboration() {
        return preferredCollaboration;
    }

    public void setPreferredCollaboration(List<String> preferredCollaboration) {
        this.preferredCollaboration = preferredCollaboration;
    }

    public List<String> getLanguages() {
        return languages;
    }

    public void setLanguages(List<String> languages) {
        this.languages = languages;
    }

    public String getAffiliationKind() {
        return affiliationKind;
    }

    public void setAffiliationKind(String affiliationKind) {
        this.affiliationKind = affiliationKind;
    }

    public String getLocationLabel() {
        return locationLabel;
    }

    public void setLocationLabel(String locationLabel) {
        this.locationLabel = locationLabel;
    }

    public Long getHomeRegionId() {
        return homeRegionId;
    }

    public void setHomeRegionId(Long homeRegionId) {
        this.homeRegionId = homeRegionId;
    }
}