package com.orbit.app.controllers;

import java.util.List;

import com.orbit.app.controllers.dto.RequestSummaryDto;
import com.orbit.app.request.RequestService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public request board — lists all open requests. Public, no auth.
 * See reference doc §8.1.
 */
@RestController
class BoardController {

    private final RequestService requestService;

    BoardController(RequestService requestService) {
        this.requestService = requestService;
    }

    @GetMapping("/api/v1/board")
    List<RequestSummaryDto> list() {
        return requestService.listBoard();
    }
}