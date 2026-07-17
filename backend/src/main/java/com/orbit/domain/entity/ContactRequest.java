package com.orbit.domain.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

/**
 * A first-contact handshake (§11) — one entity reaching out to another, with a
 * personal message and a frozen sender-context snapshot. Maps table
 * {@code contact_requests}. Creating one moves the linked request (if any) to
 * {@code contacted}.
 * <p>
 * The recipient responds exactly once: {@code accepted} (with a
 * {@code replyMessage}) or {@code declined}. Emails are revealed to both sides
 * only on acceptance. The platform's role ends at that single response; the
 * conversation continues off-platform.
 */
@Entity
@Table(name = "contact_requests")
public class ContactRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_entity_id", nullable = false)
    private Long fromEntityId;

    @Column(name = "to_entity_id", nullable = false)
    private Long toEntityId;

    /** Set when the contact was initiated from a board request. */
    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "message", nullable = false)
    private String message;

    /** Frozen at send time: {name, role, org, description, searched_text} (§11.2). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sender_context", columnDefinition = "jsonb")
    private String senderContext;

    /** 'pending' | 'accepted' | 'declined' — the recipient's one-time response. */
    @Column(name = "status", nullable = false)
    private String status = "pending";

    /** The recipient's reply — set only when {@code status='accepted'}. */
    @Column(name = "reply_message")
    private String replyMessage;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "created_at", updatable = false, insertable = false)
    @Generated(event = EventType.INSERT)
    private Instant createdAt;

    protected ContactRequest() {
    }

    public ContactRequest(Long fromEntityId, Long toEntityId, Long requestId,
                          String message, String senderContext) {
        this.fromEntityId = fromEntityId;
        this.toEntityId = toEntityId;
        this.requestId = requestId;
        this.message = message;
        this.senderContext = senderContext;
    }

    public Long getId() { return id; }
    public Long getFromEntityId() { return fromEntityId; }
    public Long getToEntityId() { return toEntityId; }
    public Long getRequestId() { return requestId; }
    public String getMessage() { return message; }
    public String getSenderContext() { return senderContext; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReplyMessage() { return replyMessage; }
    public void setReplyMessage(String replyMessage) { this.replyMessage = replyMessage; }
    public Instant getRespondedAt() { return respondedAt; }
    public void setRespondedAt(Instant respondedAt) { this.respondedAt = respondedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
