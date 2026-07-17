package com.orbit.app.controllers;

import java.util.List;

import com.orbit.app.contact.ContactService;
import com.orbit.app.controllers.dto.ContactGatingDto;
import com.orbit.app.controllers.dto.ContactRequestDto;
import com.orbit.app.controllers.dto.CreateContactRequest;
import com.orbit.app.controllers.dto.RespondContactRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin REST controller for the contact handshake (§11). All logic lives in
 * {@link ContactService}; exceptions map globally (400 on gating/validation,
 * 403 on a non-recipient response).
 * <p>
 * Routes (all authenticated):
 * <ul>
 *   <li>GET  /api/v1/contacts/gating               — pre-send gating checks</li>
 *   <li>POST /api/v1/contacts                       — broker one contact</li>
 *   <li>POST /api/v1/contacts/{id}/respond          — recipient's response</li>
 *   <li>GET  /api/v1/contacts/contacted-entity-ids  — who I've already contacted</li>
 * </ul>
 */
@RestController
class ContactController {

    private final ContactService contactService;

    ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @GetMapping("/api/v1/contacts/gating")
    ContactGatingDto gating() {
        return contactService.gating();
    }

    @PostMapping("/api/v1/contacts")
    ResponseEntity<ContactRequestDto> create(@RequestBody CreateContactRequest req) {
        ContactRequestDto dto = contactService.create(req);
        return dto != null ? ResponseEntity.ok(dto) : ResponseEntity.notFound().build();
    }

    @PostMapping("/api/v1/contacts/{id}/respond")
    ResponseEntity<ContactRequestDto> respond(@PathVariable Long id,
                                              @RequestBody RespondContactRequest req) {
        ContactRequestDto dto = contactService.respond(id, req);
        return dto != null ? ResponseEntity.ok(dto) : ResponseEntity.notFound().build();
    }

    @GetMapping("/api/v1/contacts/contacted-entity-ids")
    List<Long> contactedEntityIds() {
        return contactService.contactedEntityIds();
    }
}
