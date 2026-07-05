package com.orbit.app.ingestion.linkedin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.orbit.app.ingestion.linkedin.dto.ParsedLinkedInProfile;

/**
 * Parses raw text extracted from a LinkedIn profile PDF into a structured
 * {@link ParsedLinkedInProfile}.
 * <p>
 * LinkedIn PDFs have a consistent structure with a sidebar (Contact, Top Skills,
 * Languages, etc.) followed by a name block (Name, Headline, Location), then
 * main content sections (Summary, Experience, Education). Section headers can
 * be in English or German (or mixed within the same PDF).
 * <p>
 * The parser handles:
 * <ul>
 *   <li>Multi-language section headers (EN/DE, FR in future)</li>
 *   <li>Line wrapping (LinkedIn URLs and emails that break across lines)</li>
 *   <li>Page break markers ("Page X of Y")</li>
 *   <li>Variable name block lengths (headline may span 2 lines)</li>
 * </ul>
 */
public class LinkedInPdfParser {

    private static final Logger log = LoggerFactory.getLogger(LinkedInPdfParser.class);

    // ─── Section header dictionary (EN + DE) ───
    // Maps section header text → canonical section key
    private static final Map<String, String> SECTION_HEADERS = Map.ofEntries(
            // Sidebar sections (before name)
            Map.entry("Contact", "contact"),
            Map.entry("Kontakt", "contact"),
            Map.entry("Top Skills", "top_skills"),
            Map.entry("Top-Kenntnisse", "top_skills"),
            Map.entry("Languages", "languages"),
            Map.entry("Sprachen", "languages"),
            Map.entry("Certifications", "certifications"),
            Map.entry("Zertifikate", "certifications"),
            Map.entry("Honors-Awards", "honors_awards"),
            Map.entry("Honors & Awards", "honors_awards"),
            Map.entry("Auszeichnungen", "honors_awards"),
            Map.entry("Publications", "publications"),
            Map.entry("Veröffentlichungen", "publications"),

            // Main sections (after name)
            Map.entry("Summary", "summary"),
            Map.entry("Zusammenfassung", "summary"),
            Map.entry("Experience", "experience"),
            Map.entry("Berufserfahrung", "experience"),
            Map.entry("Education", "education"),
            Map.entry("Ausbildung", "education"),
            Map.entry("Volunteer", "volunteer"),
            Map.entry("Volunteer Experience", "volunteer"),
            Map.entry("Ehrenamtliche Arbeit", "volunteer"),
            Map.entry("Projects", "projects"),
            Map.entry("Projekte", "projects")
    );

    // Sidebar section keys (appear before the name block)
    private static final Set<String> SIDEBAR_SECTIONS = Set.of(
            "contact", "top_skills", "languages", "certifications", "honors_awards", "publications"
    );

    // Main section keys (appear after the name block)
    private static final Set<String> MAIN_SECTIONS = Set.of(
            "summary", "experience", "education", "volunteer", "projects"
    );

    // Patterns for identifying line types
    private static final Pattern PAGE_MARKER = Pattern.compile("^Page \\d+ of \\d+$");
    private static final Pattern LINKEDIN_URL = Pattern.compile("www\\.linkedin\\.com/in/");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.+-]+@.*\\.(com|ch|org|net|edu|de|fr|it)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL_PARTIAL = Pattern.compile("^[\\w.+-]+@.*\\.$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?\\d[\\d\\s\\(\\)-]+$");
    private static final Pattern DATE_RANGE = Pattern.compile(
            "(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)" +
            "|(?:Januar|Februar|März|April|Mai|Juni|Juli|August|September|Oktober|November|Dezember)" +
            "|\\b\\d{4}\\b.*-.*\\b(?:\\d{4}|Present|Heute)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TENURE_PATTERN = Pattern.compile(
            "^\\d+\\s+(?:years?|Jahre?|months?|Monate?|year|month)" +
            "(?:\\s+\\d+\\s+(?:years?|Jahre?|months?|Monate?))?$",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Parse raw LinkedIn PDF text into a structured profile.
     */
    public ParsedLinkedInProfile parse(String rawText) {
        // 1. Pre-process: clean lines, join wrapped text
        List<String> lines = preprocess(rawText);

        // 2. Identify section header positions
        List<SectionHeader> headers = findSectionHeaders(lines);

        // 3. Extract the name block (between last sidebar section and first main section)
        NameBlock nameBlock = extractNameBlock(lines, headers);

        // 4. Parse sidebar sections
        SidebarData sidebar = parseSidebarSections(lines, headers, nameBlock);

        // 5. Parse main sections
        MainContent main = parseMainSections(lines, headers, nameBlock);

        // 6. Extract current company from experience section
        String currentCompany = extractCurrentCompany(main.experienceText);

        log.debug("Parsed LinkedIn profile: name={}, headline={}, linkedInUrl={}, skills={}, companies={}",
                nameBlock.name, nameBlock.headline, sidebar.linkedInUrl,
                sidebar.topSkills.size(), currentCompany);

        return new ParsedLinkedInProfile(
                nameBlock.name,
                nameBlock.headline,
                nameBlock.location,
                sidebar.email,
                sidebar.phone,
                sidebar.linkedInUrl,
                sidebar.personalWebsite,
                sidebar.topSkills,
                sidebar.languages,
                main.summary,
                main.experienceText,
                main.educationText,
                currentCompany
        );
    }

    // ─── Pre-processing ───

    private List<String> preprocess(String rawText) {
        // PDFBox uses form feed (\f) to separate pages — replace with newlines
        String normalized = rawText.replace("\f", "\n");
        String[] rawLines = normalized.split("\n");
        List<String> cleaned = new ArrayList<>();

        for (String line : rawLines) {
            // Use strip() not trim() — handles non-breaking spaces (U+00A0)
            // that PDFBox embeds around page markers like "Page 1 of 2"
            String stripped = line.replace('\u00A0', ' ').strip();
            if (stripped.isEmpty() || PAGE_MARKER.matcher(stripped).matches()) {
                continue;
            }
            cleaned.add(stripped);
        }

        // Join wrapped lines (URLs ending with -, emails ending with .)
        List<String> joined = joinWrappedLines(cleaned);

        return joined;
    }

    /**
     * Join lines that were split by the PDF renderer:
     * - LinkedIn URLs ending with "-" → join with next line
     * - Emails ending with "." → join with next line (if it looks like a TLD)
     * - "(LinkedIn)" on a separate line → join with previous line
     */
    private List<String> joinWrappedLines(List<String> lines) {
        List<String> result = new ArrayList<>();
        int i = 0;
        while (i < lines.size()) {
            String current = lines.get(i);

            // Check if the next line is just "(LinkedIn)" — append to current
            if (i + 1 < lines.size() && lines.get(i + 1).equals("(LinkedIn)")) {
                current = current + " (LinkedIn)";
                i += 2;
                result.add(current);
                continue;
            }

            // LinkedIn URL wrapping: ends with "-" → join
            if (LINKEDIN_URL.matcher(current).find() && current.endsWith("-") && i + 1 < lines.size()) {
                String next = lines.get(i + 1);
                // Remove "(LinkedIn)" suffix from next if present
                next = next.replace(" (LinkedIn)", "").replace("(LinkedIn)", "").trim();
                current = current + next;
                // If there's a "(LinkedIn)" tag on the line after, skip it
                if (i + 2 < lines.size() && lines.get(i + 2).equals("(LinkedIn)")) {
                    i += 3;
                } else {
                    i += 2;
                }
                result.add(current);
                continue;
            }

            // Email wrapping: ends with "." → join with next line (TLD)
            if (EMAIL_PARTIAL.matcher(current).matches() && i + 1 < lines.size()) {
                String next = lines.get(i + 1).trim();
                // Only join if next line looks like a TLD (short, no spaces)
                if (next.length() <= 5 && !next.contains(" ")) {
                    current = current + next;
                    i += 2;
                    result.add(current);
                    continue;
                }
            }

            result.add(current);
            i++;
        }
        return result;
    }

    // ─── Section header detection ───

    private List<SectionHeader> findSectionHeaders(List<String> lines) {
        List<SectionHeader> headers = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String sectionKey = SECTION_HEADERS.get(line);
            if (sectionKey != null) {
                headers.add(new SectionHeader(line, sectionKey, i));
            }
        }
        return headers;
    }

    // ─── Name block extraction ───

    private NameBlock extractNameBlock(List<String> lines, List<SectionHeader> headers) {
        // Find the first main section header
        int firstMainHeaderIdx = -1;
        for (SectionHeader h : headers) {
            if (MAIN_SECTIONS.contains(h.sectionKey)) {
                firstMainHeaderIdx = h.lineIndex;
                break;
            }
        }

        if (firstMainHeaderIdx == -1) {
            log.warn("No main section header found — cannot extract name block");
            return new NameBlock("", "", "");
        }

        // The location is the last line before the first main section header
        int locIdx = firstMainHeaderIdx - 1;
        String location = lines.get(locIdx);

        // Walk backwards from locIdx to find the name:
        // Skip headline lines (1-2 lines) until we find a line that looks like a personal name.
        // The name is the first line (going backwards) that:
        //   - has 2-5 words (first name + last name, maybe middle/von/de)
        //   - does NOT look like sidebar content (email, URL, phone, language entry)
        int nameIdx = locIdx - 1;
        while (nameIdx > 0) {
            String line = lines.get(nameIdx);
            if (looksLikeName(line)) {
                break;
            }
            nameIdx--;
        }

        if (nameIdx <= 0) {
            // Fallback: use the line right after the last sidebar header
            int lastSidebarEnd = 0;
            for (SectionHeader h : headers) {
                if (SIDEBAR_SECTIONS.contains(h.sectionKey)) {
                    lastSidebarEnd = h.lineIndex;
                }
            }
            nameIdx = lastSidebarEnd + 1;
        }

        String name = lines.get(nameIdx);

        // Headline = everything between name and location, joined with spaces
        StringBuilder hb = new StringBuilder();
        for (int i = nameIdx + 1; i < locIdx; i++) {
            if (!hb.isEmpty()) hb.append(" ");
            hb.append(lines.get(i));
        }
        String headline = hb.toString();

        return new NameBlock(name, headline, location);
    }

    // ─── Sidebar section parsing ───

    private SidebarData parseSidebarSections(List<String> lines, List<SectionHeader> headers, NameBlock nameBlock) {
        SidebarData sidebar = new SidebarData();

        // Find the name line index to know where sidebar content ends
        // We find it by locating the name in the lines list
        int nameIdx = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).equals(nameBlock.name)) {
                nameIdx = i;
                break;
            }
        }

        // Parse each sidebar section
        for (int i = 0; i < headers.size(); i++) {
            SectionHeader h = headers.get(i);
            if (!SIDEBAR_SECTIONS.contains(h.sectionKey)) continue;

            // Find the end of this section's content
            int sectionEnd;
            if (i + 1 < headers.size()) {
                // Next header is also a sidebar header → content ends at next header
                // But if the next header is a main header, content ends at the name line
                SectionHeader next = headers.get(i + 1);
                if (SIDEBAR_SECTIONS.contains(next.sectionKey)) {
                    sectionEnd = next.lineIndex;
                } else {
                    // Next is a main header → this is the last sidebar section
                    // Content ends at the name line
                    sectionEnd = nameIdx >  0 ? nameIdx : next.lineIndex;
                }
            } else {
                sectionEnd = nameIdx > 0 ? nameIdx : lines.size();
            }

            // Extract content lines for this section
            int start = Math.min(h.lineIndex + 1, sectionEnd);
            int end = Math.min(sectionEnd, lines.size());
            if (start >= end) continue;
            List<String> sectionLines = lines.subList(start, end);

            switch (h.sectionKey) {
                case "contact" -> parseContactSection(sectionLines, sidebar);
                case "top_skills" -> sidebar.topSkills = extractListItems(sectionLines);
                case "languages" -> sidebar.languages = extractListItems(sectionLines);
                // certifications, honors, publications — not needed for Stage 1
            }
        }

        return sidebar;
    }
    private boolean looksLikeName(String line) {
        String[] words = line.split("\\s+");
        if (words.length < 2 || words.length > 5) return false;
        if (line.contains("@") || line.contains("www.")) return false;
        if (line.contains("(LinkedIn)") || line.contains("(Personal)")) return false;
        if (line.contains("(Mobile)") || line.contains("(Home)")) return false;
        // Exclude language entries like "German (Native or Bilingual)"
        if (line.endsWith(")")) return false;
        // Exclude phone numbers
        if (line.matches("^\\+?\\d[\\d\\s\\-]+.*")) return false;
        // Exclude headlines: lines with job-prepositions or punctuation
        // Names don't contain " at ", " bei ", " with ", " for ", " | "
        // Names don't have exclamation marks or pipe separators
        String lower = line.toLowerCase();
        if (lower.contains(" at ") || lower.contains(" bei ") ||
            lower.contains(" with ") || lower.contains(" for ") ||
            lower.contains(" und ") || lower.contains(" and ") ||
            lower.contains(" | ")) return false;
        if (line.contains("!") || line.contains("|")) return false;
        return true;
    }

    private void parseContactSection(List<String> lines, SidebarData sidebar) {
        for (String line : lines) {
            // LinkedIn URL
            if (LINKEDIN_URL.matcher(line).find()) {
                sidebar.linkedInUrl = line
                        .replace("(LinkedIn)", "")
                        .replace("(Personal)", "")
                        .trim();
            }
            // Personal website
            else if (line.contains("(Personal)")) {
                sidebar.personalWebsite = line.replace("(Personal)", "").trim();
            }
            // Email
            else if (line.contains("@") && line.contains(".")) {
                sidebar.email = line;
            }
            // Phone (starts with + or is all digits/space/dash)
            else if (PHONE_PATTERN.matcher(line).matches() || line.contains("(Mobile)") || line.contains("(Home)")) {
                sidebar.phone = line;
            }
        }
    }

    private List<String> extractListItems(List<String> lines) {
        List<String> items = new ArrayList<>();
        for (String line : lines) {
            // Skip parenthetical continuations like "(LLMOps)" that are part of previous skill
            if (line.startsWith("(") && line.endsWith(")") && !items.isEmpty()) {
                // Append to previous skill
                items.set(items.size() - 1, items.get(items.size() - 1) + " " + line);
            } else {
                items.add(line);
            }
        }
        return items;
    }

    // ─── Main section parsing ───

    private MainContent parseMainSections(List<String> lines, List<SectionHeader> headers, NameBlock nameBlock) {
        MainContent main = new MainContent();

        for (int i = 0; i < headers.size(); i++) {
            SectionHeader h = headers.get(i);
            if (!MAIN_SECTIONS.contains(h.sectionKey)) continue;

            // Find the end of this section
            int sectionEnd;
            if (i + 1 < headers.size()) {
                sectionEnd = headers.get(i + 1).lineIndex;
            } else {
                sectionEnd = lines.size();
            }

            // Extract content lines
            List<String> sectionLines = lines.subList(h.lineIndex + 1, sectionEnd);
            String sectionText = String.join("\n", sectionLines).trim();

            switch (h.sectionKey) {
                case "summary" -> main.summary = sectionText;
                case "experience" -> main.experienceText = sectionText;
                case "education" -> main.educationText = sectionText;
            }
        }

        return main;
    }

    /**
     * Extract the current (most recent) company name from the experience section text.
     * It's the first line of the experience text.
     */
    private String extractCurrentCompany(String experienceText) {
        if (experienceText == null || experienceText.isBlank()) return null;
        String firstLine = experienceText.split("\n")[0].trim();
        // Skip tenure-only lines (e.g. "9 years", "7 Jahre")
        if (TENURE_PATTERN.matcher(firstLine).matches()) {
            String[] lines = experienceText.split("\n");
            if (lines.length > 1) firstLine = lines[1].trim();
        }
        return firstLine;
    }

    // ─── Internal types ───

    private record SectionHeader(String headerText, String sectionKey, int lineIndex) {}

    private static class NameBlock {
        final String name, headline, location;
        NameBlock(String name, String headline, String location) {
            this.name = name; this.headline = headline; this.location = location;
        }
    }

    private static class SidebarData {
        String email, phone, linkedInUrl, personalWebsite;
        List<String> topSkills = List.of();
        List<String> languages = List.of();
    }

    private static class MainContent {
        String summary = "";
        String experienceText = "";
        String educationText = "";
    }
}