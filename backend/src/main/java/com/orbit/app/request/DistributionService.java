package com.orbit.app.request;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.orbit.app.search.ScopeResolver;
import com.orbit.app.search.dto.ResolvedScope;
import com.orbit.app.search.dto.ScopeSelection;
import com.orbit.domain.entity.DiscoveryEntity;
import com.orbit.domain.entity.Hub;
import com.orbit.domain.entity.Region;
import com.orbit.domain.entity.Request;
import com.orbit.domain.entity.RequestDistribution;
import com.orbit.domain.entity.RequestTag;
import com.orbit.domain.entity.Tag;
import com.orbit.domain.repository.DiscoveryEntityRepository;
import com.orbit.domain.repository.EntityTagRepository;
import com.orbit.domain.repository.HubMembershipRepository;
import com.orbit.domain.repository.HubRepository;
import com.orbit.domain.repository.RegionRepository;
import com.orbit.domain.repository.RequestDistributionRepository;
import com.orbit.domain.repository.RequestRepository;
import com.orbit.domain.repository.RequestTagRepository;
import com.orbit.domain.repository.TagRepository;
import com.orbit.domain.repository.TagSubscriptionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tag-based request distribution (reference §8.2) — the push path.
 * <p>
 * Search is semantic; distribution is <b>rule-based on tags + scope</b> so the
 * "warum wurde ich benachrichtigt" reason stays exact and explainable. The
 * cohort is the union of entities whose {@code entity_tags} overlap the
 * request's tags ({@code profile_match}) and entities subscribed to any of
 * them ({@code subscription}), hard-filtered to the request's effective scope
 * — the same Hub → Region → CH walk as search, widened
 * {@code escalation_level} times (§8.3).
 * <p>
 * Waves are idempotent: {@code UNIQUE(request_id, entity_id)} means an
 * escalation wave only adds entities that became in-scope by the widening.
 */
@Service
public class DistributionService {

    private static final ScopeSelection SCOPE_ALL = new ScopeSelection("all", null, null);

    private final RequestRepository requestRepository;
    private final RequestTagRepository requestTagRepository;
    private final RequestDistributionRepository distributionRepository;
    private final EntityTagRepository entityTagRepository;
    private final TagSubscriptionRepository tagSubscriptionRepository;
    private final HubMembershipRepository hubMembershipRepository;
    private final DiscoveryEntityRepository entityRepository;
    private final TagRepository tagRepository;
    private final HubRepository hubRepository;
    private final RegionRepository regionRepository;
    private final ScopeResolver scopeResolver;

    public DistributionService(RequestRepository requestRepository,
                               RequestTagRepository requestTagRepository,
                               RequestDistributionRepository distributionRepository,
                               EntityTagRepository entityTagRepository,
                               TagSubscriptionRepository tagSubscriptionRepository,
                               HubMembershipRepository hubMembershipRepository,
                               DiscoveryEntityRepository entityRepository,
                               TagRepository tagRepository,
                               HubRepository hubRepository,
                               RegionRepository regionRepository,
                               ScopeResolver scopeResolver) {
        this.requestRepository = requestRepository;
        this.requestTagRepository = requestTagRepository;
        this.distributionRepository = distributionRepository;
        this.entityTagRepository = entityTagRepository;
        this.tagSubscriptionRepository = tagSubscriptionRepository;
        this.hubMembershipRepository = hubMembershipRepository;
        this.entityRepository = entityRepository;
        this.tagRepository = tagRepository;
        this.hubRepository = hubRepository;
        this.regionRepository = regionRepository;
        this.scopeResolver = scopeResolver;
    }

    /**
     * Runs one distribution wave for the request at its current escalation
     * level and stamps the wave. On the first wave the request transitions
     * {@code open → distributed}. Returns how many entities were newly
     * notified (0 when the request has no tags or nothing new is in scope).
     */
    @Transactional
    public int distribute(Request request) {
        List<Long> tagIds = requestTagRepository.findByRequestId(request.getId()).stream()
                .map(RequestTag::getTagId)
                .toList();

        Instant now = Instant.now();
        int newlyNotified = tagIds.isEmpty() ? 0 : runWave(request, tagIds, now);

        request.setLastWaveAt(now);
        if (request.getDistributedAt() == null) {
            request.setDistributedAt(now);
            request.setStatus("distributed");
        }
        requestRepository.save(request);
        return newlyNotified;
    }

    private int runWave(Request request, List<Long> tagIds, Instant now) {
        // Cohort: tag overlap (profile_match) ∪ subscriptions (subscription).
        // profile_match wins when an entity qualifies both ways.
        Map<Long, Set<Long>> profileMatches = new HashMap<>();
        for (var entityTag : entityTagRepository.findByTagIdIn(tagIds)) {
            profileMatches.computeIfAbsent(entityTag.getEntityId(), k -> new TreeSet<>())
                    .add(entityTag.getTagId());
        }
        Map<Long, Set<Long>> subscriptionMatches = new HashMap<>();
        for (var subscription : tagSubscriptionRepository.findByTagIdIn(tagIds)) {
            subscriptionMatches.computeIfAbsent(subscription.getEntityId(), k -> new TreeSet<>())
                    .add(subscription.getTagId());
        }

        Set<Long> candidates = new HashSet<>();
        candidates.addAll(profileMatches.keySet());
        candidates.addAll(subscriptionMatches.keySet());

        // Never notify the author about their own request.
        entityRepository.findByOwnerIdOrderByCreatedAtDesc(request.getCreatedBy())
                .forEach(e -> candidates.remove(e.getId()));

        // Skip entities already notified in an earlier wave.
        distributionRepository.findByRequestId(request.getId())
                .forEach(d -> candidates.remove(d.getEntityId()));
        if (candidates.isEmpty()) return 0;

        ScopeSelection effective = effectiveScope(request);
        ResolvedScope scope = scopeResolver.resolve(effective);
        Map<Long, Set<Long>> hubsByEntity = membershipsOf(candidates);

        Map<Long, String> tagLabels = tagRepository.findAllById(unionOf(profileMatches, subscriptionMatches))
                .stream().collect(Collectors.toMap(Tag::getId, Tag::getLabel));

        int notified = 0;
        for (DiscoveryEntity entity : entityRepository.findAllById(candidates)) {
            if (!"active".equals(entity.getStatus())) continue;
            if (!inScope(entity, hubsByEntity.getOrDefault(entity.getId(), Set.of()), scope)) continue;

            boolean profileMatch = profileMatches.containsKey(entity.getId());
            Set<Long> matchedIds = profileMatch
                    ? profileMatches.get(entity.getId())
                    : subscriptionMatches.get(entity.getId());
            List<String> matchedLabels = matchedIds.stream()
                    .map(tagLabels::get)
                    .filter(label -> label != null)
                    .toList();

            distributionRepository.save(new RequestDistribution(
                    request.getId(), entity.getId(),
                    profileMatch ? "profile_match" : "subscription",
                    matchedLabels, effective.kind(), now));
            notified++;
        }
        return notified;
    }

    // ─── Scope ───

    /** The request's original scope widened {@code escalation_level} times (§8.3). */
    public ScopeSelection effectiveScope(Request request) {
        ScopeSelection scope = new ScopeSelection(
                request.getScopeKind(), request.getScopeHubId(), request.getScopeRegionId());
        for (int i = 0; i < request.getEscalationLevel(); i++) {
            scope = widenOnce(scope);
        }
        return scope;
    }

    /** Whether another widening step would actually broaden the scope. */
    public boolean canWiden(Request request) {
        return !"all".equals(effectiveScope(request).kind());
    }

    /**
     * One step up the widening ladder: hub → its region, region → parent
     * region, root region / all → all. Mirrors the {@link ScopeResolver}
     * semantics so "widen one level" means the same thing as in search (§5.4).
     */
    private ScopeSelection widenOnce(ScopeSelection scope) {
        if (scope.kind() == null) return SCOPE_ALL;
        return switch (scope.kind()) {
            case "hub" -> scope.hubId() == null ? SCOPE_ALL
                    : hubRepository.findById(scope.hubId())
                            .map(Hub::getRegionId)
                            .map(regionId -> new ScopeSelection("region", null, regionId))
                            .orElse(SCOPE_ALL);
            case "region" -> scope.regionId() == null ? SCOPE_ALL
                    : regionRepository.findById(scope.regionId())
                            .map(Region::getParentRegionId)
                            .map(parentId -> new ScopeSelection("region", null, parentId))
                            .orElse(SCOPE_ALL);
            default -> SCOPE_ALL;
        };
    }

    /** Same predicate as the scoped hybrid_search: hub membership OR home region in subtree. */
    private boolean inScope(DiscoveryEntity entity, Set<Long> entityHubIds, ResolvedScope scope) {
        if (scope.isUnscoped()) return true;
        for (long regionId : scope.regionIds()) {
            if (entity.getHomeRegionId() != null && entity.getHomeRegionId() == regionId) return true;
        }
        for (long hubId : scope.hubIds()) {
            if (entityHubIds.contains(hubId)) return true;
        }
        return false;
    }

    private Map<Long, Set<Long>> membershipsOf(Set<Long> entityIds) {
        Map<Long, Set<Long>> result = new HashMap<>();
        for (var membership : hubMembershipRepository.findByEntityIdIn(entityIds)) {
            result.computeIfAbsent(membership.getEntityId(), k -> new HashSet<>())
                    .add(membership.getHubId());
        }
        return result;
    }

    private static Set<Long> unionOf(Map<Long, Set<Long>> a, Map<Long, Set<Long>> b) {
        Set<Long> all = new HashSet<>();
        a.values().forEach(all::addAll);
        b.values().forEach(all::addAll);
        return all;
    }
}
