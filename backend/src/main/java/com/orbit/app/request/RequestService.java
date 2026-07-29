package com.orbit.app.request;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.orbit.app.auth.CurrentUser;
import com.orbit.app.controllers.dto.CreateRequest;
import com.orbit.app.controllers.dto.DistributedProfileDto;
import com.orbit.app.controllers.dto.RequestDto;
import com.orbit.app.controllers.dto.RequestSummaryDto;
import com.orbit.app.controllers.dto.TagRefDto;
import com.orbit.app.controllers.dto.UpdateStatusRequest;
import com.orbit.app.profile.EntityAccessDeniedException;
import com.orbit.domain.entity.DiscoveryEntity;
import com.orbit.domain.entity.Hub;
import com.orbit.domain.entity.Region;
import com.orbit.domain.entity.Request;
import com.orbit.domain.entity.RequestTag;
import com.orbit.domain.entity.Tag;
import com.orbit.domain.repository.DiscoveryEntityRepository;
import com.orbit.domain.repository.HubRepository;
import com.orbit.domain.repository.RegionRepository;
import com.orbit.domain.repository.RequestDistributionRepository;
import com.orbit.domain.repository.RequestRepository;
import com.orbit.domain.repository.RequestTagRepository;
import com.orbit.domain.repository.TagRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core request CRUD + lifecycle management (reference §8.1).
 * <p>
 * A request is the demand-side entity: when search doesn't find a match
 * (sufficient=false), the seeker creates a request that appears on the public
 * board. Later phases add distribution (tag-based push) and contact handoff.
 * <p>
 * State machine: {@code open} → {@code distributed} → {@code contacted} →
 * {@code closed}. Phase 1 only supports manual transitions by the owner
 * (create → open, cancel → closed). Distribution and contact auto-transitions
 * arrive in Phases 2–3.
 */
@Service
public class RequestService {

    private final RequestRepository requestRepository;
    private final RequestTagRepository requestTagRepository;
    private final TagRepository tagRepository;
    private final HubRepository hubRepository;
    private final RegionRepository regionRepository;
    private final DiscoveryEntityRepository entityRepository;
    private final RequestDistributionRepository distributionRepository;
    private final DistributionService distributionService;
    private final CurrentUser currentUser;

    public RequestService(RequestRepository requestRepository,
                          RequestTagRepository requestTagRepository,
                          TagRepository tagRepository,
                          HubRepository hubRepository,
                          RegionRepository regionRepository,
                          DiscoveryEntityRepository entityRepository,
                          RequestDistributionRepository distributionRepository,
                          DistributionService distributionService,
                          CurrentUser currentUser) {
        this.requestRepository = requestRepository;
        this.requestTagRepository = requestTagRepository;
        this.tagRepository = tagRepository;
        this.hubRepository = hubRepository;
        this.regionRepository = regionRepository;
        this.entityRepository = entityRepository;
        this.distributionRepository = distributionRepository;
        this.distributionService = distributionService;
        this.currentUser = currentUser;
    }

    // ─── Create ───

    @Transactional
    public RequestDto createRequest(CreateRequest req) {
        String userId = currentUser.userId()
                .orElseThrow(() -> new IllegalStateException("Not authenticated"));
        boolean hasActiveProfile = entityRepository.findByOwnerIdOrderByCreatedAtDesc(userId).stream()
                .anyMatch(entity -> "active".equals(entity.getStatus()));
        if (!hasActiveProfile) {
            throw new IllegalArgumentException("An active Orbit profile is required to create requests");
        }

        if (req.queryText() == null || req.queryText().isBlank()) {
            throw new IllegalArgumentException("queryText is required");
        }

        String scopeKind = req.scope() != null && req.scope().kind() != null
                ? req.scope().kind() : "all";
        Long scopeHubId = req.scope() != null ? req.scope().hubId() : null;
        Long scopeRegionId = req.scope() != null ? req.scope().regionId() : null;

        String urgency = req.urgency() != null ? req.urgency() : "flexible";

        Request request = new Request(
                userId,
                req.title(),
                req.description(),
                req.queryText().trim(),
                scopeKind, scopeHubId, scopeRegionId,
                urgency, req.deadline(),
                req.wantFrom(), req.wantTo(), req.wantPensumPct());
        request = requestRepository.save(request);

        // Resolve + attach tags (create new ones if needed).
        List<Tag> resolvedTags = resolveTags(req.tags());
        for (Tag tag : resolvedTags) {
            requestTagRepository.save(new RequestTag(request.getId(), tag.getId()));
        }

        // Distribute immediately (§8.2) — the response already carries
        // status='distributed' and the notified-profile count.
        distributionService.distribute(request);

        return toDto(request, resolvedTags, true);
    }

    // ─── Read ───

    /**
     * Single live request, publicly readable. Closed requests remain visible
     * only to their creator. Returns {@code null} for missing or inaccessible
     * requests (→ 404), avoiding disclosure through id enumeration.
     */
    @Transactional(readOnly = true)
    public RequestDto getRequest(Long id) {
        Request r = requestRepository.findById(id).orElse(null);
        if (r == null) return null;
        boolean mine = currentUser.userId().map(uid -> uid.equals(r.getCreatedBy())).orElse(false);
        if ("closed".equals(r.getStatus()) && !mine) return null;
        return toDto(r, loadTags(id), mine);
    }

    /** Public board — every live request (all but closed), newest first (§8.1). */
    @Transactional(readOnly = true)
    public List<RequestSummaryDto> listBoard() {
        return requestRepository.findByStatusNotOrderByCreatedAtDesc("closed").stream()
                .map(r -> toSummary(r, loadTags(r.getId())))
                .toList();
    }

    /** "My Requests" — all requests by the current user. */
    @Transactional(readOnly = true)
    public List<RequestSummaryDto> listMyRequests() {
        String userId = currentUser.userId()
                .orElseThrow(() -> new IllegalStateException("Not authenticated"));
        return requestRepository.findByCreatedByOrderByCreatedAtDesc(userId).stream()
                .map(r -> toSummary(r, loadTags(r.getId())))
                .toList();
    }

    // ─── Status transitions ───

    @Transactional
    public RequestDto transitionStatus(Long id, UpdateStatusRequest req) {
        String userId = currentUser.userId()
                .orElseThrow(() -> new IllegalStateException("Not authenticated"));
        Request r = requestRepository.findById(id).orElse(null);
        if (r == null) return null;

        if (!r.getCreatedBy().equals(userId)) {
            throw new EntityAccessDeniedException();
        }

        String newStatus = req.status();
        validateTransition(r.getStatus(), newStatus);
        r.setStatus(newStatus);
        requestRepository.save(r);
        return toDto(r, loadTags(id), true);
    }

    // ─── State machine ───

    private void validateTransition(String current, String target) {
        Set<String> validTargets = switch (current) {
            case "open" -> Set.of("distributed", "closed");
            case "distributed" -> Set.of("contacted", "closed");
            case "contacted" -> Set.of("closed");
            case "closed" -> Set.of();
            default -> Set.of();
        };
        if (!validTargets.contains(target)) {
            throw new IllegalArgumentException(
                    "Invalid transition: " + current + " → " + target);
        }
    }

    // ─── Tag resolution ───

    private List<Tag> resolveTags(List<String> labels) {
        if (labels == null || labels.isEmpty()) return List.of();
        Set<String> seen = new HashSet<>();
        List<Tag> result = new ArrayList<>();
        for (String label : labels) {
            if (label == null || label.isBlank()) continue;
            String slug = slugify(label);
            if (seen.contains(slug)) continue;
            seen.add(slug);
            Tag tag = tagRepository.findBySlug(slug).orElseGet(() -> {
                Tag newTag = new Tag(slug, label.trim());
                return tagRepository.save(newTag);
            });
            result.add(tag);
        }
        return result;
    }

    private static String slugify(String label) {
        return label.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private List<Tag> loadTags(Long requestId) {
        return requestTagRepository.findByRequestId(requestId).stream()
                .map(rt -> tagRepository.findById(rt.getTagId()).orElse(null))
                .filter(t -> t != null)
                .toList();
    }

    // ─── DTO assembly ───

    private RequestDto toDto(Request r, List<Tag> tags, boolean mine) {
        DiscoveryEntity author = authorEntity(r.getCreatedBy());
        return new RequestDto(
                r.getId(), r.getTitle(), r.getDescription(), r.getQueryText(),
                r.getScopeKind(), r.getScopeHubId(), resolveHubName(r.getScopeHubId()),
                r.getScopeRegionId(), resolveRegionName(r.getScopeRegionId()),
                r.getUrgency(), r.getDeadline(), r.getStatus(),
                r.getWantFrom(), r.getWantTo(), r.getWantPensumPct(),
                tags.stream().map(t -> new TagRefDto(t.getId(), t.getSlug(), t.getLabel())).toList(),
                author != null ? author.getId() : null,
                author != null ? author.getDisplayName() : null,
                author != null ? author.getOrgName() : null,
                author != null ? author.getEntityType() : null,
                mine,
                mine ? (int) distributionRepository.countByRequestId(r.getId()) : null,
                mine ? distributionService.effectiveScope(r).kind() : null,
                mine ? loadDistributedProfiles(r.getId()) : null,
                r.getCreatedAt());
    }

    /** The notified profiles for a request — owner-only (§8.2). */
    private List<DistributedProfileDto> loadDistributedProfiles(Long requestId) {
        return distributionRepository.findByRequestId(requestId).stream()
                .map(d -> {
                    DiscoveryEntity e = entityRepository.findById(d.getEntityId()).orElse(null);
                    if (e == null) return null;
                    return new DistributedProfileDto(
                            e.getId(), e.getDisplayName(), e.getOrgName(), e.getEntityType(),
                            d.getReason(), d.getMatchedTags(), d.getNotifiedAt());
                })
                .filter(dto -> dto != null)
                .toList();
    }

    private RequestSummaryDto toSummary(Request r, List<Tag> tags) {
        DiscoveryEntity author = authorEntity(r.getCreatedBy());
        return new RequestSummaryDto(
                r.getId(), r.getTitle(), r.getDescription(), r.getQueryText(),
                r.getScopeKind(), resolveHubName(r.getScopeHubId()),
                resolveRegionName(r.getScopeRegionId()),
                r.getUrgency(), r.getDeadline(), r.getStatus(),
                r.getWantFrom(), r.getWantTo(), r.getWantPensumPct(),
                tags.stream().map(t -> new TagRefDto(t.getId(), t.getSlug(), t.getLabel())).toList(),
                author != null ? author.getId() : null,
                author != null ? author.getDisplayName() : null,
                author != null ? author.getOrgName() : null,
                author != null ? author.getEntityType() : null,
                r.getCreatedAt());
    }

    /** The creator's entity profile — the "who asks" identity on board cards. */
    private DiscoveryEntity authorEntity(String userId) {
        if (userId == null) return null;
        List<DiscoveryEntity> owned = entityRepository.findByOwnerIdOrderByCreatedAtDesc(userId);
        return owned.isEmpty() ? null : owned.get(0);
    }

    private String resolveHubName(Long hubId) {
        if (hubId == null) return null;
        return hubRepository.findById(hubId).map(Hub::getName).orElse(null);
    }

    private String resolveRegionName(Long regionId) {
        if (regionId == null) return null;
        return regionRepository.findById(regionId).map(Region::getName).orElse(null);
    }
}
