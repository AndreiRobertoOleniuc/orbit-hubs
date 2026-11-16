package com.orbit.app.profile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.orbit.app.auth.CurrentUser;
import com.orbit.app.ingestion.EntityLinkResolver;
import com.orbit.app.ingestion.EntityEnrichmentService;
import com.orbit.app.ingestion.job.IngestionJobService;
import com.orbit.app.search.ProfileDocumentService;
import com.orbit.app.util.HubDtos;
import com.orbit.app.tag.TagVocabularyService;
import com.orbit.app.controllers.dto.AvailabilityDto;
import com.orbit.app.controllers.dto.EntityDto;
import com.orbit.app.controllers.dto.HubDto;
import com.orbit.app.controllers.dto.OrgLinkDto;
import com.orbit.app.controllers.dto.RegionDto;
import com.orbit.app.controllers.dto.IngestionJobDto;
import com.orbit.app.controllers.dto.SourceDto;
import com.orbit.app.controllers.dto.SubscriptionDto;
import com.orbit.app.controllers.dto.TagDto;
import com.orbit.domain.entity.DiscoveryEntity;
import com.orbit.domain.entity.EntityTag;
import com.orbit.domain.entity.EntityTagId;
import com.orbit.domain.entity.Hub;
import com.orbit.domain.entity.HubMembership;
import com.orbit.domain.entity.Source;
import com.orbit.domain.entity.Tag;
import com.orbit.domain.entity.TagSubscription;
import com.orbit.domain.entity.TagSubscriptionId;
import com.orbit.domain.repository.DiscoveryEntityRepository;
import com.orbit.domain.repository.EntityLinkRepository;
import com.orbit.domain.repository.EntityTagRepository;
import com.orbit.domain.repository.HubMembershipRepository;
import com.orbit.domain.repository.HubRepository;
import com.orbit.domain.repository.RegionRepository;
import com.orbit.domain.repository.SourceRepository;
import com.orbit.domain.repository.TagRepository;
import com.orbit.domain.repository.TagSubscriptionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Application service for the entity profile — the single home for all business
 * logic that was previously inline in {@code EntityController}. Owns entity
 * reads, tag/summary/availability/hub/subscription/mode mutations, resync
 * orchestration, and DTO assembly.
 */
@Service
public class EntityService {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final DiscoveryEntityRepository entities;
    private final SourceRepository sources;
    private final TagRepository tagRepo;
    private final EntityTagRepository entityTagRepo;
    private final RegionRepository regionRepo;
    private final HubRepository hubRepo;
    private final HubMembershipRepository hubMembershipRepo;
    private final TagSubscriptionRepository subscriptionRepo;
    private final EntityLinkRepository entityLinkRepo;
    private final EntityLinkResolver linkResolver;
    private final EntityEnrichmentService enrichmentService;
    private final IngestionJobService ingestionJobs;
    private final CurrentUser currentUser;
    private final ProfileDocumentService profileDocuments;
    private final TagVocabularyService tagVocabulary;

    public EntityService(DiscoveryEntityRepository entities, SourceRepository sources,
                         TagRepository tagRepo, EntityTagRepository entityTagRepo,
                         RegionRepository regionRepo, HubRepository hubRepo,
                         HubMembershipRepository hubMembershipRepo,
                         TagSubscriptionRepository subscriptionRepo,
                         EntityLinkRepository entityLinkRepo,
                         EntityLinkResolver linkResolver,
                         EntityEnrichmentService enrichmentService,
                         IngestionJobService ingestionJobs,
                         CurrentUser currentUser,
                         ProfileDocumentService profileDocuments,
                         TagVocabularyService tagVocabulary) {
        this.entities = entities;
        this.sources = sources;
        this.tagRepo = tagRepo;
        this.entityTagRepo = entityTagRepo;
        this.regionRepo = regionRepo;
        this.hubRepo = hubRepo;
        this.hubMembershipRepo = hubMembershipRepo;
        this.subscriptionRepo = subscriptionRepo;
        this.entityLinkRepo = entityLinkRepo;
        this.linkResolver = linkResolver;
        this.enrichmentService = enrichmentService;
        this.ingestionJobs = ingestionJobs;
        this.currentUser = currentUser;
        this.profileDocuments = profileDocuments;
        this.tagVocabulary = tagVocabulary;
    }

    // ─── Reads ───

    public EntityDto getEntity(Long id) {
        return entities.findById(id)
                .filter(entity -> "active".equals(entity.getStatus()) || isOwner(entity))
                .map(this::toDto)
                .orElse(null);
    }

    public List<EntityDto> getMyEntities() {
        String userId = currentUser.userId().orElse(null);
        if (userId == null) return List.of();
        return entities.findByOwnerIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toDto)
                .sorted(Comparator.comparingInt((EntityDto dto) -> dto.sources().size()).reversed())
                .toList();
    }

    // ─── Owner deletion ───

    /**
     * Permanently delete one owned entity. Database cascades remove all data
     * belonging to the entity; the Clerk user and user-owned requests remain.
     */
    @Transactional
    public boolean deleteEntity(Long id) {
        DiscoveryEntity entity = loadOwnedEntity(id);
        if (entity == null) return false;
        entities.delete(entity);
        return true;
    }

    // ─── Resync ───

    /**
     * Queue a re-ingestion of every source of an owned entity. Returns the
     * queued job; the caller polls it (re-fetching all sources takes minutes).
     */
    public IngestionJobDto resync(Long id) {
        DiscoveryEntity entity = loadOwnedEntity(id);
        if (entity == null) return null;
        return ingestionJobs.enqueueResync(entity.getOwnerId(), id);
    }

    // ─── Tags ───

    @Transactional
    public EntityDto replaceTags(Long id, List<String> labels) {
        DiscoveryEntity entity = loadOwnedEntity(id);
        if (entity == null) return null;

        entityTagRepo.deleteAll(entityTagRepo.findByEntityId(id));
        entityTagRepo.flush();

        for (String label : labels) {
            Tag tag = tagVocabulary.findOrMint(label).orElse(null);
            if (tag == null) continue;
            EntityTagId etId = new EntityTagId(id, tag.getId());
            if (entityTagRepo.findById(etId).isEmpty()) {
                entityTagRepo.save(new EntityTag(id, tag.getId(), "manual"));
            }
        }
        profileDocuments.refreshAfterCommit(id);
        tagVocabulary.embedMissingAsync();
        return toDto(entity);
    }

    // ─── Summary ───

    @Transactional
    public EntityDto updateSummary(Long id, String summary) {
        DiscoveryEntity entity = loadOwnedEntity(id);
        if (entity == null) return null;
        entity.setSummary(summary);
        entities.save(entity);
        profileDocuments.refreshAfterCommit(id);
        return toDto(entity);
    }

    // ─── Availability ───

    @Transactional
    public EntityDto updateAvailability(Long id,
                                        boolean openToRequests,
                                        java.time.LocalDate availableFrom,
                                        java.time.LocalDate availableTo,
                                        Integer pensumPct,
                                        String note,
                                        List<String> preferredCollaboration) {
        DiscoveryEntity entity = loadOwnedEntity(id);
        if (entity == null) return null;

        entity.setOpenToRequests(openToRequests);
        entity.setAvailableFrom(availableFrom);
        entity.setAvailableTo(availableTo);
        entity.setPensumPct(pensumPct);
        entity.setAvailabilityNote(note);
        entity.setPreferredCollaboration(preferredCollaboration);
        entities.save(entity);
        return toDto(entity);
    }

    // ─── Profile meta ───

    @Transactional
    public EntityDto updateProfileMeta(Long id, String displayName, String headline, String orgName,
                                      String affiliationKind, List<String> languages,
                                      String locationLabel) {
        DiscoveryEntity entity = loadOwnedEntity(id);
        if (entity == null) return null;
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Display name is required");
        }

        entity.setDisplayName(displayName.trim());
        entity.setHeadline(blankToNull(headline));
        entity.setOrgName(blankToNull(orgName));
        entity.setAffiliationKind(affiliationKind);
        entity.setLanguages(languages);
        entity.setLocationLabel(locationLabel);
        entities.save(entity);
        // Re-resolve the Person↔Org link against the (possibly Zefix-picked) org
        // name: links to a matching org entity, or clears a stale link. Never
        // creates an org entity.
        linkResolver.resolveForPerson(entity);
        profileDocuments.refreshAfterCommit(id);
        return toDto(entity);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // ─── Sources (curation) ───

    /**
     * Switch a single source (a GitHub repo / OpenAlex work) on or off for the
     * owning entity. An inactive source keeps its chunks but is excluded from
     * search (hybrid_search + evidence). Owner-gated.
     */
    @Transactional
    public EntityDto setSourceActive(Long entityId, Long sourceId, boolean active) {
        DiscoveryEntity entity = loadOwnedEntity(entityId);
        if (entity == null) return null;

        Source source = sources.findById(sourceId).orElse(null);
        if (source == null || !entityId.equals(source.getEntityId())) {
            return null;   // unknown source, or not part of this entity → 404
        }
        source.setActive(active);
        sources.save(source);
        return toDto(entity);
    }

    /**
     * Replace the selected GitHub repositories and ORCID works in one action,
     * then regenerate the derived profile from the resulting active evidence.
     * <p>
     * Not one transaction on purpose: the selection is written atomically by
     * {@code saveAll}, then enrichment makes its LLM calls without holding a
     * database connection (it writes its own results in a short transaction).
     */
    public EntityDto replaceSourceSelection(Long entityId, List<Long> activeSourceIds) {
        DiscoveryEntity entity = loadOwnedEntity(entityId);
        if (entity == null) return null;

        List<Source> entitySources = sources.findByEntityIdOrderByCreatedAtAsc(entityId);
        Set<Long> requested = new HashSet<>(activeSourceIds == null ? List.of() : activeSourceIds);
        Set<Long> curatableIds = entitySources.stream()
                .filter(source -> isCuratable(source.getSourceType()))
                .map(Source::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (!curatableIds.containsAll(requested)) {
            throw new IllegalArgumentException("Source selection contains an unknown or non-curatable source");
        }

        for (Source source : entitySources) {
            if (isCuratable(source.getSourceType())) {
                source.setActive(requested.contains(source.getId()));
            }
        }
        if (entitySources.stream().noneMatch(Source::isActive)) {
            throw new IllegalArgumentException("At least one active evidence source is required");
        }

        sources.saveAll(entitySources);
        sources.flush();
        enrichmentService.enrich(entityId);
        return toDto(entities.findById(entityId).orElse(entity));
    }

    /** Validate the onboarding essentials and make a draft profile public. */
    @Transactional
    public EntityDto publish(Long entityId) {
        DiscoveryEntity entity = loadOwnedEntity(entityId);
        if (entity == null) return null;
        if ("active".equals(entity.getStatus())) return toDto(entity);
        if (!"draft".equals(entity.getStatus())) {
            throw new IllegalArgumentException("Only draft profiles can be published");
        }

        validatePublishable(entity);
        entity.setStatus("active");
        entities.save(entity);
        return toDto(entity);
    }

    // ─── Hubs ───

    @Transactional
    public EntityDto replaceHubs(Long id, List<Long> hubIds) {
        DiscoveryEntity entity = loadOwnedEntity(id);
        if (entity == null) return null;

        hubMembershipRepo.deleteAll(hubMembershipRepo.findByEntityId(id));
        hubMembershipRepo.flush();
        for (Long hubId : hubIds) {
            if (hubId == null || hubRepo.findById(hubId).isEmpty()) continue;
            hubMembershipRepo.save(new HubMembership(id, hubId, null, "confirmed"));
        }
        return toDto(entity);
    }

    // ─── Mode ───

    @Transactional
    public EntityDto updateMode(Long id, String mode) {
        if (!"supply".equals(mode) && !"demand".equals(mode)) {
            throw new IllegalArgumentException("Invalid mode: " + mode);
        }
        DiscoveryEntity entity = loadOwnedEntity(id);
        if (entity == null) return null;
        entity.setProfileMode(mode);
        entities.save(entity);
        return toDto(entity);
    }

    // ─── Subscriptions ───

    public List<SubscriptionDto> getSubscriptions(Long id) {
        DiscoveryEntity entity = loadOwnedEntity(id);
        if (entity == null) return null;
        return buildSubscriptionDtos(id);
    }

    @Transactional
    public List<SubscriptionDto> replaceSubscriptions(Long id, List<String> labels) {
        DiscoveryEntity entity = loadOwnedEntity(id);
        if (entity == null) return null;

        subscriptionRepo.deleteAll(subscriptionRepo.findByEntityId(id));
        subscriptionRepo.flush();
        for (String label : labels) {
            Tag tag = tagVocabulary.findOrMint(label).orElse(null);
            if (tag == null) continue;
            if (subscriptionRepo.findById(new TagSubscriptionId(id, tag.getId())).isEmpty()) {
                subscriptionRepo.save(new TagSubscription(id, tag.getId()));
            }
        }
        return buildSubscriptionDtos(id);
    }

    // ─── Owner check ───

    /**
     * Loads an entity and throws {@link EntityAccessDeniedException} if it exists
     * but is not owned by the current user. Returns null only if the entity
     * does not exist at all (→ 404).
     */
    private DiscoveryEntity loadOwnedEntity(Long id) {
        DiscoveryEntity entity = entities.findById(id).orElse(null);
        if (entity == null) return null;
        if (!isOwner(entity)) throw new EntityAccessDeniedException();
        return entity;
    }

    private boolean isOwner(DiscoveryEntity entity) {
        String userId = currentUser.userId().orElse(null);
        return userId != null && userId.equals(entity.getOwnerId());
    }

    private void validatePublishable(DiscoveryEntity entity) {
        if (entity.getDisplayName() == null || entity.getDisplayName().isBlank()) {
            throw new IllegalArgumentException("Display name is required");
        }
        if (entity.getHeadline() == null || entity.getHeadline().isBlank()) {
            throw new IllegalArgumentException("A role or short description is required");
        }
        if ("person".equals(entity.getEntityType())) {
            if (!Set.of("company", "self_employed", "private").contains(entity.getAffiliationKind())) {
                throw new IllegalArgumentException("An affiliation type is required");
            }
            if ("company".equals(entity.getAffiliationKind())
                    && (entity.getOrgName() == null || entity.getOrgName().isBlank())) {
                throw new IllegalArgumentException("A company is required for employed profiles");
            }
        }
        List<Source> activeSources = sources.findByEntityIdAndActiveTrueOrderByCreatedAtAsc(entity.getId());
        if (activeSources.isEmpty()) {
            throw new IllegalArgumentException("At least one active evidence source is required");
        }
        if ("demand".equals(entity.getProfileMode())) {
            Set<String> qualifyingTypes = "organization".equals(entity.getEntityType())
                    ? Set.of("zefix", "company_site", "manual_text")
                    : Set.of("cv_pdf", "linkedin_pdf", "manual_text");
            boolean hasDemandIdentitySource = activeSources.stream()
                    .map(Source::getSourceType)
                    .anyMatch(qualifyingTypes::contains);
            if (!hasDemandIdentitySource) {
                throw new IllegalArgumentException("A qualifying identity source is required for demand profiles");
            }
            // Summary and tags remain best-effort AI-generated fields. Demand
            // publication must not fail solely because enrichment is offline.
            return;
        }
        if (entity.getSummary() == null || entity.getSummary().isBlank()) {
            throw new IllegalArgumentException("A profile summary is required");
        }
        if (entityTagRepo.findByEntityId(entity.getId()).isEmpty()) {
            throw new IllegalArgumentException("At least one competence tag is required");
        }
    }

    private static boolean isCuratable(String sourceType) {
        return "github".equals(sourceType) || "orcid_pub".equals(sourceType);
    }

    // ─── DTO assembly ───

    public EntityDto toDto(DiscoveryEntity entity) {
        List<SourceDto> sourceDtos = sources.findByEntityIdOrderByCreatedAtAsc(entity.getId()).stream()
                .map(s -> new SourceDto(
                        s.getId(), s.getSourceType(), s.getLabel(),
                        s.getSourceUrl(), s.getFetchStatus(), s.getFetchedAt(), s.isActive()))
                .toList();
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
                buildTagDtos(entity.getId()),
                buildAvailability(entity),
                entity.getAffiliationKind(),
                entity.getLanguages(),
                entity.getLocationLabel(),
                buildHubDtos(entity.getId()),
                buildHomeRegion(entity),
                buildOrgLink(entity),
                entity.getOwnerId() != null);
    }

    /**
     * The person's linked organization (§10), or null. Only present when an
     * entity_links row exists — never fabricated from the plain org_name string.
     */
    private OrgLinkDto buildOrgLink(DiscoveryEntity entity) {
        if (!"person".equalsIgnoreCase(entity.getEntityType())) return null;
        return entityLinkRepo.findFirstByPersonEntityId(entity.getId())
                .map(link -> entities.findById(link.getOrgEntityId()).orElse(null))
                .map(org -> new OrgLinkDto(org.getId(), org.getDisplayName(), isHrVerified(org.getId())))
                .orElse(null);
    }

    /** True when the org entity has a Handelsregister (Zefix) source. */
    private boolean isHrVerified(Long orgEntityId) {
        return sources.findByEntityIdOrderByCreatedAtAsc(orgEntityId).stream()
                .anyMatch(s -> "zefix".equals(s.getSourceType()));
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
        if (metadata == null || metadata.isBlank()) return null;
        try {
            return JSON.readTree(metadata);
        } catch (Exception e) {
            return null;
        }
    }
}
