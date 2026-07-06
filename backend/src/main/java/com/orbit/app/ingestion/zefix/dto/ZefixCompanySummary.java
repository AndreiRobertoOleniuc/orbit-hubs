package com.orbit.app.ingestion.zefix.dto;

/**
 * One Zefix name-search hit (CompanyShort), used for onboarding autocomplete.
 * The UID is normalized to the canonical CHE-NNN.NNN.NNN form.
 */
public record ZefixCompanySummary(
        String name,
        String uid,
        String legalSeat,
        String canton,
        String legalForm,
        String status) {
}
