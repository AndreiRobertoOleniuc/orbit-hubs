package com.orbit.app.controllers;

import com.orbit.app.contact.InboxService;
import com.orbit.app.controllers.dto.InboxDto;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin REST controller for the Postfach. Authenticated;
 * all logic lives in {@link InboxService}.
 */
@RestController
class InboxController {

    private final InboxService inboxService;

    InboxController(InboxService inboxService) {
        this.inboxService = inboxService;
    }

    @GetMapping("/api/v1/inbox")
    InboxDto getInbox() {
        return inboxService.getInbox();
    }
}
