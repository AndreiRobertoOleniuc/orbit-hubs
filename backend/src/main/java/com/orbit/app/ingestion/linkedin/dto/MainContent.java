package com.orbit.app.ingestion.linkedin.dto;

/**
 * Main content sections parsed from a LinkedIn PDF — summary, experience, education.
 * Fields are mutable because they are populated incrementally during parsing.
 */
public class MainContent {
    public String summary = "";
    public String experienceText = "";
    public String educationText = "";
}