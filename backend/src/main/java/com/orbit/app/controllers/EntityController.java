package com.orbit.app.controllers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.orbit.app.auth.CurrentUser;
import com.orbit.app.ingestion.IngestionOrchestrator;
import com.orbit.app.ingestion.dto.IngestionResult;
import com.orbit.app.util.SlugUtils;
import com.orbit.app.controllers.dto.EntityDto;
import com.orbit.app.controllers.dto.ResyncResponse;
import com.orbit.app.controllers.dto.SourceDto;
import com.orbit.app.controllers.dto.TagDto;
import com.orbit.app.controllers.dto.UpdateSummaryRequest;
import com.orbit.app.controllers.dto.UpdateTagsRequest;
import com.orbit.domain.entity.DiscoveryEntity;
import com.orbit.domain.entity.EntityTag;
import com.orbit.domain.entity.EntityTagId;
import com.orbit.domain.entity.Tag;
import com.orbit.domain.repository.DiscoveryEntityRepository;
import com.orbit.domain.repository.EntityTagRepository;
import com.orbit.domain.repository.SourceRepository;
import com.orbit.domain.repository.TagRepository;

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
    private final IngestionOrchestrator orchestrator;
    private final CurrentUser currentUser;

    EntityController(DiscoveryEntityRepository entities, SourceRepository sources,
                     TagRepository tagRepo, EntityTagRepository entityTagRepo,
                     IngestionOrchestrator orchestrator,
                     CurrentUser currentUser) {
        this.entities = entities;
        this.sources = sources;
        this.tagRepo = tagRepo;
        this.entityTagRepo = entityTagRepo;
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

    // ─── helpers ───



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
                parseMetadata(entity.getMetadata()),
                entity.getCreatedAt(),
                sourceDtos,
                tagDtos);
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
