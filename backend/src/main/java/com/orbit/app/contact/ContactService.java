package com.orbit.app.contact;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import java.time.Instant;

import com.orbit.app.auth.CurrentUser;
import com.orbit.app.controllers.dto.ContactGatingDto;
import com.orbit.app.controllers.dto.ContactRequestDto;
import com.orbit.app.controllers.dto.CreateContactRequest;
import com.orbit.app.controllers.dto.RespondContactRequest;
import com.orbit.app.controllers.dto.SenderContextDto;
import com.orbit.app.profile.EntityAccessDeniedException;
import com.orbit.domain.entity.ContactRequest;
import com.orbit.domain.entity.DiscoveryEntity;
import com.orbit.domain.entity.Request;
import com.orbit.domain.entity.Source;
import com.orbit.domain.repository.ContactRequestRepository;
import com.orbit.domain.repository.DiscoveryEntityRepository;
import com.orbit.domain.repository.RequestRepository;
import com.orbit.domain.repository.SourceRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

/**
 * First-contact handoff (§11). The platform brokers exactly one message with a
 * frozen sender context; everything after runs off-platform via email shown in
 * the recipient's Postfach. No SMTP — the handoff is in-app only.
 * <p>
 * Gating (§11.1): a sender needs an active signed-in profile with a verified email and
 * at least one self-describing source (LinkedIn, website or own description).
 * NOT required: affiliation, availability, complete skill profile. The rule is
 * simply "anyone with a profile can contact anyone" — person or organization.
 */
@Service
public class ContactService {

    /** Source types that satisfy the "≥1 source" gating check (§11.1). */
    private static final Set<String> GATING_SOURCE_TYPES =
            Set.of("cv_pdf", "linkedin_pdf", "personal_site", "company_site", "manual_text");

    private static final int DESCRIPTION_MAX_CHARS = 300;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ContactRequestRepository contactRepository;
    private final DiscoveryEntityRepository entityRepository;
    private final SourceRepository sourceRepository;
    private final RequestRepository requestRepository;
    private final CurrentUser currentUser;

    public ContactService(ContactRequestRepository contactRepository,
                          DiscoveryEntityRepository entityRepository,
                          SourceRepository sourceRepository,
                          RequestRepository requestRepository,
                          CurrentUser currentUser) {
        this.contactRepository = contactRepository;
        this.entityRepository = entityRepository;
        this.sourceRepository = sourceRepository;
        this.requestRepository = requestRepository;
        this.currentUser = currentUser;
    }

    /** The gating checks for the current user — read before the dialog sends. */
    public ContactGatingDto gating() {
        List<String> missing = new ArrayList<>();

        if (currentUser.email().isEmpty()) {
            missing.add("email");
        }

        DiscoveryEntity sender = senderActiveEntity();
        if (sender == null) {
            missing.add("entity");
            missing.add("source");
            return new ContactGatingDto(false, missing);
        }
        boolean hasSource = sourceRepository.findByEntityIdOrderByCreatedAtAsc(sender.getId()).stream()
                .map(Source::getSourceType)
                .anyMatch(GATING_SOURCE_TYPES::contains);
        if (!hasSource) {
            missing.add("source");
        }
        return new ContactGatingDto(missing.isEmpty(), missing);
    }

    /**
     * Broker one contact. Returns {@code null} when the recipient entity
     * doesn't exist (→ 404); throws {@code IllegalArgumentException} on failed
     * gating, empty message, or an unclaimed recipient (→ 400). A linked
     * request in {@code open}/{@code distributed} transitions to
     * {@code contacted}.
     */
    @Transactional
    public ContactRequestDto create(CreateContactRequest req) {
        ContactGatingDto gate = gating();
        if (!gate.allowed()) {
            throw new IllegalArgumentException("Profile incomplete: " + String.join(", ", gate.missing()));
        }
        if (req.message() == null || req.message().isBlank()) {
            throw new IllegalArgumentException("message is required");
        }
        if (req.toEntityId() == null) {
            throw new IllegalArgumentException("toEntityId is required");
        }

        DiscoveryEntity recipient = entityRepository.findById(req.toEntityId()).orElse(null);
        if (recipient == null) return null;
        if (recipient.getOwnerId() == null) {
            throw new IllegalArgumentException("Recipient profile is not claimed");
        }

        DiscoveryEntity sender = senderActiveEntity();
        if (recipient.getId().equals(sender.getId())) {
            throw new IllegalArgumentException("Cannot contact your own profile");
        }

        Request request = loadAuthorizedRequest(req.requestId(), sender, recipient);
        String searchedText = req.searchedText() != null && !req.searchedText().isBlank()
                ? req.searchedText().trim()
                : (request != null ? request.getQueryText() : null);

        ContactRequest contact = contactRepository.save(new ContactRequest(
                sender.getId(), recipient.getId(),
                request != null ? request.getId() : null,
                req.message().trim(),
                senderContextJson(sender, searchedText)));

        // First contact for this request → kontaktiert (§8.1).
        if (request != null
                && ("open".equals(request.getStatus()) || "distributed".equals(request.getStatus()))) {
            request.setStatus("contacted");
            requestRepository.save(request);
        }

        return new ContactRequestDto(contact.getId(), contact.getFromEntityId(),
                contact.getToEntityId(), contact.getRequestId(), contact.getMessage(),
                contact.getCreatedAt());
    }

    private Request loadAuthorizedRequest(Long requestId,
                                          DiscoveryEntity sender,
                                          DiscoveryEntity recipient) {
        if (requestId == null) return null;

        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        boolean senderOwnsRequest = request.getCreatedBy().equals(sender.getOwnerId());
        boolean recipientOwnsRequest = request.getCreatedBy().equals(recipient.getOwnerId());
        if (!senderOwnsRequest && !recipientOwnsRequest) {
            throw new EntityAccessDeniedException();
        }
        if ("closed".equals(request.getStatus())) {
            throw new IllegalArgumentException("Closed requests cannot be contacted");
        }
        return request;
    }

    /**
     * The recipient's one-time handshake response (§11). Returns {@code null}
     * when the contact doesn't exist (→ 404); throws
     * {@code EntityAccessDeniedException} when the caller doesn't own the
     * recipient entity (→ 403), or {@code IllegalArgumentException} on a
     * repeated response or an accept without a message (→ 400).
     * <p>
     * Reply gating is intentionally light — the recipient was already sought
     * out, so we don't demand a complete profile just to say yes/no.
     */
    @Transactional
    public ContactRequestDto respond(Long contactId, RespondContactRequest req) {
        ContactRequest contact = contactRepository.findById(contactId).orElse(null);
        if (contact == null) return null;

        DiscoveryEntity recipient = entityRepository.findById(contact.getToEntityId()).orElse(null);
        String userId = currentUser.userId()
                .orElseThrow(() -> new IllegalStateException("Not authenticated"));
        if (recipient == null || !userId.equals(recipient.getOwnerId())) {
            throw new EntityAccessDeniedException();
        }
        if (!"pending".equals(contact.getStatus())) {
            throw new IllegalArgumentException("Contact request already answered");
        }

        if (req.accept()) {
            if (req.message() == null || req.message().isBlank()) {
                throw new IllegalArgumentException("A reply message is required to accept");
            }
            contact.setStatus("accepted");
            contact.setReplyMessage(req.message().trim());
        } else {
            contact.setStatus("declined");
        }
        contact.setRespondedAt(Instant.now());
        contactRepository.save(contact);

        return new ContactRequestDto(contact.getId(), contact.getFromEntityId(),
                contact.getToEntityId(), contact.getRequestId(), contact.getMessage(),
                contact.getCreatedAt());
    }

    /**
     * Recipient entity ids the current user has already contacted — powers the
     * "✓ Kontaktiert" markers on search cards and profiles. Empty when the user
     * has no entity yet.
     */
    @Transactional(readOnly = true)
    public List<Long> contactedEntityIds() {
        List<Long> myEntityIds = currentUser.userId()
                .map(uid -> entityRepository.findByOwnerIdOrderByCreatedAtDesc(uid).stream()
                        .map(DiscoveryEntity::getId).toList())
                .orElse(List.of());
        if (myEntityIds.isEmpty()) return List.of();
        return contactRepository.findContactedEntityIds(myEntityIds);
    }

    // ─── Sender context snapshot (§11.2) ───

    private String senderContextJson(DiscoveryEntity sender, String searchedText) {
        SenderContextDto context = new SenderContextDto(
                sender.getDisplayName(),
                sender.getHeadline(),
                sender.getOrgName(),
                truncate(sender.getSummary()),
                searchedText);
        return JSON.writeValueAsString(context);
    }

    private static String truncate(String text) {
        if (text == null || text.length() <= DESCRIPTION_MAX_CHARS) return text;
        return text.substring(0, DESCRIPTION_MAX_CHARS).stripTrailing() + "…";
    }

    /** The sender's identity — the current user's newest active owned entity. */
    private DiscoveryEntity senderActiveEntity() {
        String userId = currentUser.userId()
                .orElseThrow(() -> new IllegalStateException("Not authenticated"));
        List<DiscoveryEntity> owned = entityRepository.findByOwnerIdOrderByCreatedAtDesc(userId);
        return owned.stream()
                .filter(entity -> "active".equals(entity.getStatus()))
                .findFirst()
                .orElse(null);
    }
}
