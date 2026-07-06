package com.orbit.controllers;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import com.orbit.app.auth.CurrentUser;
import com.orbit.domain.entity.DiscoveryEntity;
import com.orbit.domain.entity.Source;
import com.orbit.domain.repository.DiscoveryEntityRepository;
import com.orbit.domain.repository.SourceRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

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
    private final CurrentUser currentUser;

    EntityController(DiscoveryEntityRepository entities, SourceRepository sources, CurrentUser currentUser) {
        this.entities = entities;
        this.sources = sources;
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

    private EntityDto toDto(DiscoveryEntity entity) {
        List<SourceDto> sourceDtos = sources.findByEntityIdOrderByCreatedAtAsc(entity.getId()).stream()
                .map(s -> new SourceDto(
                        s.getId(), s.getSourceType(), s.getLabel(),
                        s.getSourceUrl(), s.getFetchStatus(), s.getFetchedAt()))
                .toList();
        return new EntityDto(
                entity.getId(),
                entity.getEntityType(),
                entity.getDisplayName(),
                entity.getHeadline(),
                entity.getOrgName(),
                entity.getStatus(),
                parseMetadata(entity.getMetadata()),
                entity.getCreatedAt(),
                sourceDtos);
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

    record SourceDto(
            Long id,
            String sourceType,
            String label,
            String sourceUrl,
            String fetchStatus,
            Instant fetchedAt) {
    }

    record EntityDto(
            Long id,
            String entityType,
            String displayName,
            String headline,
            String orgName,
            String status,
            JsonNode metadata,
            Instant createdAt,
            List<SourceDto> sources) {
    }
}
