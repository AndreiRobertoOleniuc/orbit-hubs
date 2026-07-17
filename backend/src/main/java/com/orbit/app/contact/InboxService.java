package com.orbit.app.contact;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.orbit.app.auth.CurrentUser;
import com.orbit.app.controllers.dto.InboxContactDto;
import com.orbit.app.controllers.dto.InboxDistributionDto;
import com.orbit.app.controllers.dto.InboxDto;
import com.orbit.app.controllers.dto.InboxSentContactDto;
import com.orbit.app.controllers.dto.SenderContextDto;
import com.orbit.domain.entity.ContactRequest;
import com.orbit.domain.entity.DiscoveryEntity;
import com.orbit.domain.entity.Request;
import com.orbit.domain.entity.RequestDistribution;
import com.orbit.domain.entity.User;
import com.orbit.domain.repository.ContactRequestRepository;
import com.orbit.domain.repository.DiscoveryEntityRepository;
import com.orbit.domain.repository.RequestDistributionRepository;
import com.orbit.domain.repository.RequestRepository;
import com.orbit.domain.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

/**
 * The Postfach — served to both demand and supply. Three sections: contact
 * requests the user received, contact requests the user sent (each with its
 * handshake outcome), and requests distributed to the user's entities with
 * the explainable "Verteilt, weil …" reason (§8.2, §11).
 */
@Service
public class InboxService {

    private static final Logger log = LoggerFactory.getLogger(InboxService.class);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ContactRequestRepository contactRepository;
    private final RequestDistributionRepository distributionRepository;
    private final DiscoveryEntityRepository entityRepository;
    private final RequestRepository requestRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;

    public InboxService(ContactRequestRepository contactRepository,
                        RequestDistributionRepository distributionRepository,
                        DiscoveryEntityRepository entityRepository,
                        RequestRepository requestRepository,
                        UserRepository userRepository,
                        CurrentUser currentUser) {
        this.contactRepository = contactRepository;
        this.distributionRepository = distributionRepository;
        this.entityRepository = entityRepository;
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public InboxDto getInbox() {
        String userId = currentUser.userId()
                .orElseThrow(() -> new IllegalStateException("Not authenticated"));

        Set<Long> myEntityIds = entityRepository.findByOwnerIdOrderByCreatedAtDesc(userId).stream()
                .map(DiscoveryEntity::getId)
                .collect(Collectors.toSet());
        if (myEntityIds.isEmpty()) {
            return new InboxDto(List.of(), List.of(), List.of());
        }

        List<InboxContactDto> received = contactRepository
                .findByToEntityIdInOrderByCreatedAtDesc(myEntityIds).stream()
                .map(this::toReceivedDto)
                .toList();

        List<InboxSentContactDto> sent = contactRepository
                .findByFromEntityIdInOrderByCreatedAtDesc(myEntityIds).stream()
                .map(this::toSentDto)
                .toList();

        List<InboxDistributionDto> distributions = distributionRepository
                .findByEntityIdInOrderByCreatedAtDesc(myEntityIds).stream()
                .map(this::toDistributionDto)
                .filter(dto -> dto != null)
                .toList();

        return new InboxDto(received, sent, distributions);
    }

    // ─── DTO assembly ───

    private InboxContactDto toReceivedDto(ContactRequest contact) {
        DiscoveryEntity from = entityRepository.findById(contact.getFromEntityId()).orElse(null);
        // Sender email revealed to the recipient only once accepted (mutual opt-in).
        String fromEmail = "accepted".equals(contact.getStatus()) ? resolveEmail(from) : null;
        return new InboxContactDto(
                contact.getId(),
                contact.getFromEntityId(),
                from != null ? from.getDisplayName() : null,
                from != null ? from.getOrgName() : null,
                fromEmail,
                parseSenderContext(contact.getSenderContext()),
                contact.getMessage(),
                contact.getRequestId(),
                requestTitle(contact.getRequestId()),
                contact.getStatus(),
                contact.getReplyMessage(),
                contact.getRespondedAt(),
                contact.getCreatedAt());
    }

    private InboxSentContactDto toSentDto(ContactRequest contact) {
        DiscoveryEntity to = entityRepository.findById(contact.getToEntityId()).orElse(null);
        // Recipient email + reply revealed to the sender only once accepted.
        boolean accepted = "accepted".equals(contact.getStatus());
        return new InboxSentContactDto(
                contact.getId(),
                contact.getToEntityId(),
                to != null ? to.getDisplayName() : null,
                to != null ? to.getOrgName() : null,
                accepted ? resolveEmail(to) : null,
                contact.getMessage(),
                contact.getStatus(),
                accepted ? contact.getReplyMessage() : null,
                contact.getRequestId(),
                requestTitle(contact.getRequestId()),
                contact.getRespondedAt(),
                contact.getCreatedAt());
    }

    /** owner_id → users.email, or null when the entity is unclaimed/missing. */
    private String resolveEmail(DiscoveryEntity entity) {
        if (entity == null || entity.getOwnerId() == null) return null;
        return userRepository.findById(entity.getOwnerId()).map(User::getEmail).orElse(null);
    }

    private String requestTitle(Long requestId) {
        if (requestId == null) return null;
        return requestRepository.findById(requestId).map(Request::getTitle).orElse(null);
    }

    private InboxDistributionDto toDistributionDto(RequestDistribution distribution) {
        Request request = requestRepository.findById(distribution.getRequestId()).orElse(null);
        if (request == null) return null;
        DiscoveryEntity author = authorEntity(request.getCreatedBy());
        return new InboxDistributionDto(
                request.getId(),
                request.getTitle(),
                request.getQueryText(),
                author != null ? author.getId() : null,
                author != null ? author.getDisplayName() : null,
                author != null ? author.getOrgName() : null,
                distribution.getReason(),
                distribution.getMatchedTags(),
                distribution.getWideningLevel(),
                request.getStatus(),
                distribution.getNotifiedAt());
    }

    private SenderContextDto parseSenderContext(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return JSON.readValue(json, SenderContextDto.class);
        } catch (RuntimeException ex) {
            log.warn("Unparseable sender_context: {}", ex.getMessage());
            return null;
        }
    }

    /** Same author-identity resolution as the board: newest owned entity. */
    private DiscoveryEntity authorEntity(String userId) {
        if (userId == null) return null;
        List<DiscoveryEntity> owned = entityRepository.findByOwnerIdOrderByCreatedAtDesc(userId);
        return owned.isEmpty() ? null : owned.get(0);
    }
}
