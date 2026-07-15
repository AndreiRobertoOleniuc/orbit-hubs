package com.orbit.app.controllers;

import java.util.List;

import com.orbit.app.controllers.dto.CreateRequest;
import com.orbit.app.controllers.dto.RequestDto;
import com.orbit.app.controllers.dto.RequestSummaryDto;
import com.orbit.app.controllers.dto.UpdateStatusRequest;
import com.orbit.app.request.RequestService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin REST controller for request CRUD + lifecycle + "My Requests" list.
 * All logic lives in {@link RequestService}. Access-denied exceptions are
 * handled globally by {@code GlobalExceptionHandler} (→ 403).
 * <p>
 * Routes:
 * <ul>
 *   <li>GET  /api/v1/requests       — my requests (authenticated)</li>
 *   <li>POST /api/v1/requests       — create request (authenticated)</li>
 *   <li>GET  /api/v1/requests/{id}  — single request (public if open)</li>
 *   <li>PUT  /api/v1/requests/{id}/status — transition (owner only)</li>
 * </ul>
 */
@RestController
class RequestController {

    private final RequestService requestService;

    RequestController(RequestService requestService) {
        this.requestService = requestService;
    }

    @GetMapping("/api/v1/requests")
    List<RequestSummaryDto> listMyRequests() {
        return requestService.listMyRequests();
    }

    @PostMapping("/api/v1/requests")
    RequestDto create(@RequestBody CreateRequest req) {
        return requestService.createRequest(req);
    }

    @GetMapping("/api/v1/requests/{id}")
    ResponseEntity<RequestDto> get(@PathVariable Long id) {
        RequestDto dto = requestService.getRequest(id);
        return dto != null ? ResponseEntity.ok(dto) : ResponseEntity.notFound().build();
    }

    @PutMapping("/api/v1/requests/{id}/status")
    ResponseEntity<RequestDto> transition(@PathVariable Long id, @RequestBody UpdateStatusRequest req) {
        RequestDto dto = requestService.transitionStatus(id, req);
        return dto != null ? ResponseEntity.ok(dto) : ResponseEntity.notFound().build();
    }
}
