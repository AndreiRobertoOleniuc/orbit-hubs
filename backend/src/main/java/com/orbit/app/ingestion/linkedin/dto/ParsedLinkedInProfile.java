package com.orbit.app.ingestion.linkedin.dto;

import java.util.List;

/**
 * Structured representation of a parsed LinkedIn profile PDF.
 * <p>
 * Extracted by {@code LinkedInPdfParser} from the raw PDF text. Contains
 * all fields needed for entity creation, embedding, and metadata.
 *
 * @param name             the person's full name (e.g. "Marvin von Rappard")
 * @param headline         the profile headline/tagline
 * @param location         the person's location (e.g. "Zürich Metropolitan Area")
 * @param email            email address (may be null)
 * @param phone            phone number (may be null)
 * @param linkedInUrl      LinkedIn profile URL (e.g. "linkedin.com/in/marvinvr")
 * @param personalWebsite  personal website URL (may be null)
 * @param topSkills        top skills listed in the sidebar
 * @param languages        languages with proficiency (e.g. "German (Native or Bilingual)")
 * @param summary          the Summary/Zusammenfassung section text
 * @param experienceText   the full Experience/Berufserfahrung section text
 * @param educationText    the full Education/Ausbildung section text
 * @param currentCompany   the most recent company name (first line of Experience)
 */
public record ParsedLinkedInProfile(
        String name,
        String headline,
        String location,
        String email,
        String phone,
        String linkedInUrl,
        String personalWebsite,
        List<String> topSkills,
        List<String> languages,
        String summary,
        String experienceText,
        String educationText,
        String currentCompany
) {
}