package com.orbit.app.ingestion.linkedin.dto;

import java.util.List;

/**
 * Sidebar data parsed from a LinkedIn PDF — contact info, top skills, languages.
 * Fields are mutable because they are populated incrementally during parsing.
 */
public class SidebarData {
    public String email, phone, linkedInUrl, personalWebsite;
    public List<String> topSkills = List.of();
    public List<String> languages = List.of();
}