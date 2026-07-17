package com.orbit.app.controllers.dto;

import java.util.List;

/**
 * The Postfach payload — incoming ({@code received}) and outgoing
 * ({@code sent}) contact requests plus requests distributed to the current
 * user's entities, in one call.
 */
public record InboxDto(
        List<InboxContactDto> received,
        List<InboxSentContactDto> sent,
        List<InboxDistributionDto> distributions
) {}
