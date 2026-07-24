package com.orbit.app.controllers;

import java.util.List;

import com.orbit.app.controllers.dto.EntityDto;
import com.orbit.app.controllers.dto.ResyncResponse;
import com.orbit.app.controllers.dto.SetSourceActiveRequest;
import com.orbit.app.controllers.dto.SubscriptionDto;
import com.orbit.app.controllers.dto.UpdateAvailabilityRequest;
import com.orbit.app.controllers.dto.UpdateHubsRequest;
import com.orbit.app.controllers.dto.UpdateModeRequest;
import com.orbit.app.controllers.dto.UpdateProfileMetaRequest;
import com.orbit.app.controllers.dto.UpdateSubscriptionsRequest;
import com.orbit.app.controllers.dto.UpdateSummaryRequest;
import com.orbit.app.controllers.dto.UpdateTagsRequest;
import com.orbit.app.controllers.dto.UpdateSourceSelectionRequest;
import com.orbit.app.profile.EntityService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Thin REST controller for entity profile endpoints. All business logic lives
 * in {@link EntityService} — this class only maps HTTP to service calls and
 * back to HTTP responses. Exception → HTTP status mapping is handled globally
 * by {@link GlobalExceptionHandler}.
 */
@RestController
class EntityController {

    private final EntityService entityService;

    EntityController(EntityService entityService) {
        this.entityService = entityService;
    }

    @GetMapping("/api/v1/entities/{id}")
    ResponseEntity<EntityDto> entity(@PathVariable Long id) {
        EntityDto dto = entityService.getEntity(id);
        return dto != null ? ResponseEntity.ok(dto) : ResponseEntity.notFound().build();
    }

    @GetMapping("/api/v1/me/entities")
    List<EntityDto> myEntities() {
        return entityService.getMyEntities();
    }

    @DeleteMapping("/api/v1/entities/{id}")
    ResponseEntity<Void> deleteEntity(@PathVariable Long id) {
        return entityService.deleteEntity(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @PostMapping("/api/v1/entities/{id}/resync")
    ResponseEntity<ResyncResponse> resync(@PathVariable Long id) {
        ResyncResponse response = entityService.resync(id);
        return response != null ? ResponseEntity.ok(response) : ResponseEntity.notFound().build();
    }

    @PutMapping("/api/v1/entities/{id}/tags")
    ResponseEntity<EntityDto> updateTags(@PathVariable Long id,
                                          @RequestBody UpdateTagsRequest request) {
        EntityDto dto = entityService.replaceTags(id, request.tags());
        return dto != null ? ResponseEntity.ok(dto) : ResponseEntity.notFound().build();
    }

    @PutMapping("/api/v1/entities/{id}/summary")
    ResponseEntity<EntityDto> updateSummary(@PathVariable Long id,
                                            @RequestBody UpdateSummaryRequest request) {
        EntityDto dto = entityService.updateSummary(id, request.summary());
        return dto != null ? ResponseEntity.ok(dto) : ResponseEntity.notFound().build();
    }

    @PutMapping("/api/v1/entities/{id}/availability")
    ResponseEntity<EntityDto> updateAvailability(@PathVariable Long id,
                                                 @RequestBody UpdateAvailabilityRequest request) {
        EntityDto dto = entityService.updateAvailability(
                id, request.openToRequests(), request.availableFrom(),
                request.availableTo(), request.pensumPct(),
                request.note(), request.preferredCollaboration());
        return resolveEntityResponse(dto);
    }

    @PutMapping("/api/v1/entities/{id}/profile-meta")
    ResponseEntity<EntityDto> updateProfileMeta(@PathVariable Long id,
                                                 @RequestBody UpdateProfileMetaRequest request) {
        EntityDto dto = entityService.updateProfileMeta(
                id, request.displayName(), request.headline(), request.orgName(),
                request.affiliationKind(), request.languages(), request.locationLabel());
        return resolveEntityResponse(dto);
    }

    @PutMapping("/api/v1/entities/{id}/hubs")
    ResponseEntity<EntityDto> updateHubs(@PathVariable Long id,
                                         @RequestBody UpdateHubsRequest request) {
        List<Long> hubIds = request.hubIds() == null ? List.of() : request.hubIds();
        EntityDto dto = entityService.replaceHubs(id, hubIds);
        return resolveEntityResponse(dto);
    }

    @PutMapping("/api/v1/entities/{id}/mode")
    ResponseEntity<EntityDto> updateMode(@PathVariable Long id,
                                         @RequestBody UpdateModeRequest request) {
        EntityDto dto = entityService.updateMode(id, request.mode());
        return resolveEntityResponse(dto);
    }

    @PatchMapping("/api/v1/entities/{entityId}/sources/{sourceId}")
    ResponseEntity<EntityDto> setSourceActive(@PathVariable Long entityId,
                                              @PathVariable Long sourceId,
                                              @RequestBody SetSourceActiveRequest request) {
        EntityDto dto = entityService.setSourceActive(entityId, sourceId, request.active());
        return resolveEntityResponse(dto);
    }

    @PutMapping("/api/v1/entities/{entityId}/sources/selection")
    ResponseEntity<EntityDto> replaceSourceSelection(
            @PathVariable Long entityId,
            @RequestBody UpdateSourceSelectionRequest request) {
        EntityDto dto = entityService.replaceSourceSelection(entityId, request.activeSourceIds());
        return resolveEntityResponse(dto);
    }

    @PostMapping("/api/v1/entities/{entityId}/publish")
    ResponseEntity<EntityDto> publish(@PathVariable Long entityId) {
        EntityDto dto = entityService.publish(entityId);
        return resolveEntityResponse(dto);
    }

    @GetMapping("/api/v1/entities/{id}/subscriptions")
    ResponseEntity<List<SubscriptionDto>> subscriptions(@PathVariable Long id) {
        List<SubscriptionDto> subs = entityService.getSubscriptions(id);
        return resolveListResponse(subs);
    }

    @PutMapping("/api/v1/entities/{id}/subscriptions")
    ResponseEntity<List<SubscriptionDto>> updateSubscriptions(
            @PathVariable Long id,
            @RequestBody UpdateSubscriptionsRequest request) {
        List<String> labels = request.tags() == null ? List.of() : request.tags();
        List<SubscriptionDto> subs = entityService.replaceSubscriptions(id, labels);
        return resolveListResponse(subs);
    }

    // ─── Response helpers ───

    /**
     * EntityService returns null when the entity is not found (→ 404).
     * Not-owner is thrown as {@link com.orbit.app.profile.EntityAccessDeniedException} (→ 403 via
     * the exception handler above). Invalid input is thrown as
     * {@link IllegalArgumentException} (→ 400).
     */
    private ResponseEntity<EntityDto> resolveEntityResponse(EntityDto dto) {
        return dto != null ? ResponseEntity.ok(dto) : ResponseEntity.notFound().build();
    }

    private ResponseEntity<List<SubscriptionDto>> resolveListResponse(List<SubscriptionDto> subs) {
        return subs != null ? ResponseEntity.ok(subs) : ResponseEntity.notFound().build();
    }
}
