package com.orbit.app.controllers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.orbit.app.auth.CurrentUser;
import com.orbit.app.ingestion.IngestionOrchestrator;
import com.orbit.app.ingestion.dto.IngestionResult;
import com.orbit.app.util.HubDtos;
import com.orbit.app.util.SlugUtils;
import com.orbit.app.controllers.dto.AvailabilityDto;
import com.orbit.app.controllers.dto.EntityDto;
import com.orbit.app.controllers.dto.HubDto;
import com.orbit.app.controllers.dto.RegionDto;
import com.orbit.app.controllers.dto.ResyncResponse;
import com.orbit.app.controllers.dto.SourceDto;
import com.orbit.app.controllers.dto.SubscriptionDto;
import com.orbit.app.controllers.dto.TagDto;
import com.orbit.app.controllers.dto.UpdateAvailabilityRequest;
import com.orbit.app.controllers.dto.UpdateHubsRequest;
import com.orbit.app.controllers.dto.UpdateModeRequest;
import com.orbit.app.controllers.dto.UpdateProfileMetaRequest;
import com.orbit.app.controllers.dto.UpdateSubscriptionsRequest;
import com.orbit.app.controllers.dto.UpdateSummaryRequest;
import com.orbit.app.controllers.dto.UpdateTagsRequest;
import com.orbit.domain.entity.DiscoveryEntity;
import com.orbit.domain.entity.EntityTag;
import com.orbit.domain.entity.EntityTagId;
import com.orbit.domain.entity.Hub;
import com.orbit.domain.entity.HubMembership;
import com.orbit.domain.entity.Region;
import com.orbit.domain.entity.Tag;
import com.orbit.domain.entity.TagSubscription;
import com.orbit.domain.repository.DiscoveryEntityRepository;
import com.orbit.domain.repository.EntityTagRepository;
import com.orbit.domain.repository.HubMembershipRepository;
import com.orbit.domain.repository.HubRepository;
import com.orbit.domain.repository.RegionRepository;
import com.orbit.domain.repository.SourceRepository;
import com.orbit.domain.repository.TagRepository;
import com.orbit.domain.repository.TagSubscriptionRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Read-only profile endpoints over the ingested entity → source hierarchy.
 *
 * <p>
 * {@code GET /api/v1/entities/{id}} is the public profile view (whitelisted in
 * {@code ClerkSecurityConfig}); {@code GET /api/v1/me/entities} returns the
 * authenticated user's own entities, richest first — entity resolution is
 * per-external-id, so one user can own more than one entity until merge
 * handling lands in a later stage.
 */
@RestController
class EntityController {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final DiscoveryEntityRepository entities;
    private final SourceRepository sources;
    private final TagRepository tagRepo;
    private final EntityTagRepository entityTagRepo;
    private final RegionRepository regionRepo;
    private final HubRepository hubRepo;
    private final HubMembershipRepository hubMembershipRepo;
    private final TagSubscriptionRepository subscriptionRepo;
    private final IngestionOrchestrator orchestrator;
    private final CurrentUser currentUser;

    EntityController(DiscoveryEntityRepository entities, SourceRepository sources,
                     TagRepository tagRepo, EntityTagRepository entityTagRepo,
                     RegionRepository regionRepo, HubRepository hubRepo,
                     HubMembershipRepository hubMembershipRepo,
                     TagSubscriptionRepository subscriptionRepo,
                     IngestionOrchestrator orchestrator,
                     CurrentUser currentUser) {
        this.entities = entities;
        this.sources = sources;
        this.tagRepo = tagRepo;
        this.entityTagRepo = entityTagRepo;
        this.regionRepo = regionRepo;
        this.hubRepo = hubRepo;
        this.hubMembershipRepo = hubMembershipRepo;
        this.subscriptionRepo = subscriptionRepo;
        this.orchestrator = orchestrator;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/v1/entities/{id}")
    ResponseEntity<EntityDto> entity(@PathVariable Long id) {
        return entities.findById(id)
                .map(entity -> ResponseEntity.ok(toDto(entity)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/api/v1/me/entities")
    List<EntityDto> myEntities() {
        String userId = currentUser.userId().orElse(null);
        if (userId == null) {
            return List.of();
        }
        return entities.findByOwnerIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toDto)
                .sorted(Comparator.comparingInt((EntityDto dto) -> dto.sources().size()).reversed())
                .toList();
    }

    // ─── Tag & summary editing (user edits in onboarding / profile) ───

    /**
     * Resync an entity: re-ingest all its sources and re-run enrichment.
     * This is the backend for the "Quellen neu auswerten" button — it
     * re-fetches all re-fetchable sources (GitHub, ORCID, Zefix, websites),
     * re-chunks, re-embeds, and regenerates the competence summary + tags.
     * LinkedIn PDF and manual text sources are skipped (their chunks stay).
     */
    @PostMapping("/api/v1/entities/{id}/resync")
    ResponseEntity<ResyncResponse> resync(@PathVariable Long id) {
        DiscoveryEntity entity = entities.findById(id).orElse(null);
        if (entity == null) return ResponseEntity.notFound().build();

        var results = orchestrator.resyncEntity(id);

        int ingested = (int) results.stream().filter(r -> r.status() == IngestionResult.Status.INGESTED).count();
        int skipped = (int) results.stream().filter(r -> r.status() == IngestionResult.Status.SKIPPED).count();
        int errors = (int) results.stream().filter(r -> r.status() == IngestionResult.Status.ERROR).count();

        // Return the updated entity DTO so the frontend gets fresh summary + tags
        DiscoveryEntity refreshed = entities.findById(id).orElse(entity);
        return ResponseEntity.ok(new ResyncResponse(toDto(refreshed), ingested, skipped, errors));
    }

    /**
     * Replace the full set of tags for an entity. All tags in the request body
     * become {@code origin='manual'} (user-confirmed). Any existing derived
     * tags not in the request are removed. Existing manual tags not in the
     * request are also removed (the user explicitly chose the final set).
     * <p>
     * This is the endpoint the onboarding wizard calls after the user confirms
     * or edits the AI-derived tags.
     */
    @PutMapping("/api/v1/entities/{id}/tags")
    ResponseEntity<EntityDto> updateTags(@PathVariable Long id,
                                                @RequestBody UpdateTagsRequest request) {
        DiscoveryEntity entity = entities.findById(id).orElse(null);
        if (entity == null) return ResponseEntity.notFound().build();

        // Remove ALL existing tags (both derived and manual) — the user's
        // confirmed set replaces everything.
        List<EntityTag> existing = entityTagRepo.findByEntityId(id);
        entityTagRepo.deleteAll(existing);
        entityTagRepo.flush();

        // Mint or find tags, insert as manual
        for (String label : request.tags()) {
            String slug = SlugUtils.slugify(label);
            if (slug.isEmpty()) continue;
            Tag tag = tagRepo.findBySlug(slug)
                    .orElseGet(() -> tagRepo.save(new Tag(slug, label)));
            EntityTagId etId = new EntityTagId(id, tag.getId());
            if (entityTagRepo.findById(etId).isEmpty()) {
                entityTagRepo.save(new EntityTag(id, tag.getId(), "manual"));
            }
        }

        return ResponseEntity.ok(toDto(entity));
    }

    /**
     * Update the entity's competence summary (manual override). The user can
     * edit the AI-generated summary in the onboarding wizard or profile.
     * Once manually set, it will be overwritten by the next ingestion's
     * LLM regeneration (derived summaries replace manual). This matches the
     * reference doc's "User edits set origin='manual' and are never overwritten"
     * principle for tags — but for the summary, re-ingestion always regenerates
     * since it aggregates all sources.
     */
    @PutMapping("/api/v1/entities/{id}/summary")
    ResponseEntity<EntityDto> updateSummary(@PathVariable Long id,
                                                  @RequestBody UpdateSummaryRequest request) {
        DiscoveryEntity entity = entities.findById(id).orElse(null);
        if (entity == null) return ResponseEntity.notFound().build();
        entity.setSummary(request.summary());
        entities.save(entity);
        return ResponseEntity.ok(toDto(entity));
    }

    // ─── Supply-side profile editing (availability, affiliation, hubs, subs) ───

    /**
     * Update structured availability — the "Offen für Anfragen" toggle plus the
     * window, Pensum %, note, and preferred collaboration types (reference §6).
     */
    @PutMapping("/api/v1/entities/{id}/availability")
    ResponseEntity<EntityDto> updateAvailability(@PathVariable Long id,
                                                 @RequestBody UpdateAvailabilityRequest request) {
        DiscoveryEntity entity = entities.findById(id).orElse(null);
        if (entity == null) return ResponseEntity.notFound().build();
        if (!isOwner(entity)) return ResponseEntity.status(403).build();

        entity.setOpenToRequests(request.openToRequests());
        entity.setAvailableFrom(request.availableFrom());
        entity.setAvailableTo(request.availableTo());
        entity.setPensumPct(request.pensumPct());
        entity.setAvailabilityNote(request.note());
        entity.setPreferredCollaboration(request.preferredCollaboration());
        entities.save(entity);
        return ResponseEntity.ok(toDto(entity));
    }

    /**
     * Update profile meta — affiliation kind (Firma / Selbständig / Privatperson),
     * working languages, and human-readable location (reference §5.2 / §10).
     */
    @PutMapping("/api/v1/entities/{id}/profile-meta")
    ResponseEntity<EntityDto> updateProfileMeta(@PathVariable Long id,
                                                @RequestBody UpdateProfileMetaRequest request) {
        DiscoveryEntity entity = entities.findById(id).orElse(null);
        if (entity == null) return ResponseEntity.notFound().build();
        if (!isOwner(entity)) return ResponseEntity.status(403).build();

        entity.setAffiliationKind(request.affiliationKind());
        entity.setLanguages(request.languages());
        entity.setLocationLabel(request.locationLabel());
        entities.save(entity);
        return ResponseEntity.ok(toDto(entity));
    }

    /**
     * Replace the entity's confirmed hub memberships. The provided hub ids become
     * {@code source='confirmed'}; any prior membership not in the set is removed.
     */
    @PutMapping("/api/v1/entities/{id}/hubs")
    ResponseEntity<EntityDto> updateHubs(@PathVariable Long id,
                                         @RequestBody UpdateHubsRequest request) {
        DiscoveryEntity entity = entities.findById(id).orElse(null);
        if (entity == null) return ResponseEntity.notFound().build();
        if (!isOwner(entity)) return ResponseEntity.status(403).build();

        hubMembershipRepo.deleteAll(hubMembershipRepo.findByEntityId(id));
        hubMembershipRepo.flush();
        List<Long> hubIds = request.hubIds() == null ? List.of() : request.hubIds();
        for (Long hubId : hubIds) {
            if (hubId == null || hubRepo.findById(hubId).isEmpty()) continue;
            hubMembershipRepo.save(new HubMembership(id, hubId, null, "confirmed"));
        }
        return ResponseEntity.ok(toDto(entity));
    }

    /**
     * Switch the profile mode ('supply' | 'demand'). Set from the onboarding
     * fork and switchable on the profile — drives mode-dependent rendering (§5.1).
     */
    @PutMapping("/api/v1/entities/{id}/mode")
    ResponseEntity<EntityDto> updateMode(@PathVariable Long id,
                                         @RequestBody UpdateModeRequest request) {
        DiscoveryEntity entity = entities.findById(id).orElse(null);
        if (entity == null) return ResponseEntity.notFound().build();
        if (!isOwner(entity)) return ResponseEntity.status(403).build();
        String mode = request.mode();
        if (!"supply".equals(mode) && !"demand".equals(mode)) {
            return ResponseEntity.badRequest().build();
        }
        entity.setProfileMode(mode);
        entities.save(entity);
        return ResponseEntity.ok(toDto(entity));
    }

    /** The entity's subscribed tags ("Abonnierte Themen"). Owner-only. */
    @GetMapping("/api/v1/entities/{id}/subscriptions")
    ResponseEntity<List<SubscriptionDto>> subscriptions(@PathVariable Long id) {
        DiscoveryEntity entity = entities.findById(id).orElse(null);
        if (entity == null) return ResponseEntity.notFound().build();
        if (!isOwner(entity)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(buildSubscriptionDtos(id));
    }

    /**
     * Replace the entity's tag subscriptions with the provided label set. Tags
     * are minted on demand (normalized to a slug) and joined to the same
     * controlled vocabulary as Kompetenzen and distribution.
     */
    @PutMapping("/api/v1/entities/{id}/subscriptions")
    ResponseEntity<List<SubscriptionDto>> updateSubscriptions(@PathVariable Long id,
                                                              @RequestBody UpdateSubscriptionsRequest request) {
        DiscoveryEntity entity = entities.findById(id).orElse(null);
        if (entity == null) return ResponseEntity.notFound().build();
        if (!isOwner(entity)) return ResponseEntity.status(403).build();

        subscriptionRepo.deleteAll(subscriptionRepo.findByEntityId(id));
        subscriptionRepo.flush();
        List<String> labels = request.tags() == null ? List.of() : request.tags();
        for (String label : labels) {
            String slug = SlugUtils.slugify(label);
            if (slug.isEmpty()) continue;
            Tag tag = tagRepo.findBySlug(slug)
                    .orElseGet(() -> tagRepo.save(new Tag(slug, label)));
            if (subscriptionRepo.findById(new com.orbit.domain.entity.TagSubscriptionId(id, tag.getId())).isEmpty()) {
                subscriptionRepo.save(new TagSubscription(id, tag.getId()));
            }
        }
        return ResponseEntity.ok(buildSubscriptionDtos(id));
    }

    // ─── helpers ───

    /** True when the current authenticated user owns this entity. */
    private boolean isOwner(DiscoveryEntity entity) {
        String userId = currentUser.userId().orElse(null);
        return userId != null && userId.equals(entity.getOwnerId());
    }

    private EntityDto toDto(DiscoveryEntity entity) {
        List<SourceDto> sourceDtos = sources.findByEntityIdOrderByCreatedAtAsc(entity.getId()).stream()
                .map(s -> new SourceDto(
                        s.getId(), s.getSourceType(), s.getLabel(),
                        s.getSourceUrl(), s.getFetchStatus(), s.getFetchedAt()))
                .toList();
        List<TagDto> tagDtos = buildTagDtos(entity.getId());
        return new EntityDto(
                entity.getId(),
                entity.getEntityType(),
                entity.getDisplayName(),
                entity.getHeadline(),
                entity.getOrgName(),
                entity.getSummary(),
                entity.getStatus(),
                entity.getProfileMode(),
                parseMetadata(entity.getMetadata()),
                entity.getCreatedAt(),
                sourceDtos,
                tagDtos,
                buildAvailability(entity),
                entity.getAffiliationKind(),
                entity.getLanguages(),
                entity.getLocationLabel(),
                buildHubDtos(entity.getId()),
                buildHomeRegion(entity));
    }

    private AvailabilityDto buildAvailability(DiscoveryEntity entity) {
        return new AvailabilityDto(
                entity.isOpenToRequests(),
                entity.getAvailableFrom(),
                entity.getAvailableTo(),
                entity.getPensumPct(),
                entity.getAvailabilityNote(),
                entity.getPreferredCollaboration());
    }

    /** The entity's confirmed hub memberships, resolved to hubs with region names. */
    private List<HubDto> buildHubDtos(Long entityId) {
        List<HubMembership> memberships = hubMembershipRepo.findByEntityId(entityId);
        if (memberships.isEmpty()) return List.of();
        List<Long> hubIds = memberships.stream().map(HubMembership::getHubId).toList();
        List<Hub> hubs = hubRepo.findAllById(hubIds);
        return HubDtos.from(hubs, regionRepo);
    }

    private RegionDto buildHomeRegion(DiscoveryEntity entity) {
        if (entity.getHomeRegionId() == null) return null;
        return regionRepo.findById(entity.getHomeRegionId())
                .map(r -> new RegionDto(r.getId(), r.getSlug(), r.getName(), r.getParentRegionId()))
                .orElse(null);
    }

    private List<SubscriptionDto> buildSubscriptionDtos(Long entityId) {
        List<TagSubscription> subs = subscriptionRepo.findByEntityId(entityId);
        if (subs.isEmpty()) return List.of();
        List<Long> tagIds = subs.stream().map(TagSubscription::getTagId).toList();
        return tagRepo.findAllById(tagIds).stream()
                .map(t -> new SubscriptionDto(t.getId(), t.getSlug(), t.getLabel()))
                .toList();
    }

    /**
     * Build the tag DTOs for an entity, resolving tag labels from the tags table.
     */
    private List<TagDto> buildTagDtos(Long entityId) {
        List<EntityTag> entityTags = entityTagRepo.findByEntityId(entityId);
        if (entityTags.isEmpty()) return List.of();
        List<Long> tagIds = entityTags.stream().map(EntityTag::getTagId).toList();
        List<Tag> tags = tagRepo.findAllById(tagIds);
        Map<Long, Tag> tagById = new LinkedHashMap<>();
        for (Tag t : tags) tagById.put(t.getId(), t);

        List<TagDto> result = new ArrayList<>();
        for (EntityTag et : entityTags) {
            Tag tag = tagById.get(et.getTagId());
            if (tag != null) {
                result.add(new TagDto(tag.getId(), tag.getSlug(), tag.getLabel(), et.getOrigin()));
            }
        }
        return result;
    }

    private static JsonNode parseMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return null;
        }
        try {
            return JSON.readTree(metadata);
        } catch (Exception e) {
            return null;
        }
    }


}
