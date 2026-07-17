package com.orbit.domain.entity;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * A demand-side request — created when search doesn't find a match.
 * Maps table {@code requests}. See reference doc §8.
 * <p>
 * Lifecycle: {@code open} → {@code distributed} → {@code contacted} →
 * {@code closed}. The creator (seeker) is identified by their Clerk
 * {@code sub} via {@code created_by} → {@code users.id}.
 */
@Entity
@Table(name = "requests")
public class Request {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "title")
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "query_text", nullable = false)
    private String queryText;

    @Column(name = "scope_kind", nullable = false)
    private String scopeKind = "all";

    @Column(name = "scope_hub_id")
    private Long scopeHubId;

    @Column(name = "scope_region_id")
    private Long scopeRegionId;

    @Column(name = "urgency", nullable = false)
    private String urgency = "flexible";

    @Column(name = "deadline")
    private LocalDate deadline;

    @Column(name = "status", nullable = false)
    private String status = "open";

    @Column(name = "distributed_at")
    private Instant distributedAt;

    @Column(name = "want_from")
    private LocalDate wantFrom;

    @Column(name = "want_to")
    private LocalDate wantTo;

    @Column(name = "want_pensum_pct")
    private Integer wantPensumPct;

    /** Widening steps applied so far (§8.3) — effective scope = original scope widened this many times. */
    @Column(name = "escalation_level", nullable = false)
    private int escalationLevel = 0;

    /** When the last distribution wave ran (initial or escalation/reminder/deadline wave). */
    @Column(name = "last_wave_at")
    private Instant lastWaveAt;

    /** One-shot guard for the 'soon' reminder wave. */
    @Column(name = "reminder_sent", nullable = false)
    private boolean reminderSent = false;

    @Column(name = "created_at", updatable = false, insertable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false)
    @Generated(event = EventType.UPDATE)
    private Instant updatedAt;

    protected Request() {
    }

    public Request(String createdBy, String title, String description, String queryText,
                   String scopeKind, Long scopeHubId, Long scopeRegionId,
                   String urgency, LocalDate deadline,
                   LocalDate wantFrom, LocalDate wantTo, Integer wantPensumPct) {
        this.createdBy = createdBy;
        this.title = title;
        this.description = description;
        this.queryText = queryText;
        this.scopeKind = scopeKind;
        this.scopeHubId = scopeHubId;
        this.scopeRegionId = scopeRegionId;
        this.urgency = urgency;
        this.deadline = deadline;
        this.wantFrom = wantFrom;
        this.wantTo = wantTo;
        this.wantPensumPct = wantPensumPct;
    }

    public Long getId() { return id; }
    public String getCreatedBy() { return createdBy; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getQueryText() { return queryText; }
    public String getScopeKind() { return scopeKind; }
    public Long getScopeHubId() { return scopeHubId; }
    public Long getScopeRegionId() { return scopeRegionId; }
    public String getUrgency() { return urgency; }
    public LocalDate getDeadline() { return deadline; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getDistributedAt() { return distributedAt; }
    public void setDistributedAt(Instant distributedAt) { this.distributedAt = distributedAt; }
    public LocalDate getWantFrom() { return wantFrom; }
    public LocalDate getWantTo() { return wantTo; }
    public Integer getWantPensumPct() { return wantPensumPct; }
    public int getEscalationLevel() { return escalationLevel; }
    public void setEscalationLevel(int escalationLevel) { this.escalationLevel = escalationLevel; }
    public Instant getLastWaveAt() { return lastWaveAt; }
    public void setLastWaveAt(Instant lastWaveAt) { this.lastWaveAt = lastWaveAt; }
    public boolean isReminderSent() { return reminderSent; }
    public void setReminderSent(boolean reminderSent) { this.reminderSent = reminderSent; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}