package com.orbit.app.llm;

import java.util.ArrayList;
import java.util.List;

import com.orbit.app.ingestion.dto.SourceContent;
import com.orbit.app.llm.dto.MatchRationale;
import com.orbit.app.llm.dto.RationaleClaim;
import com.orbit.app.llm.dto.RationaleEvidence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * LLM-powered summarization service for extracting high-signal text from
 * unstructured sources (GitHub READMEs, website content).
 * <p>
 * Instead of mechanically splitting READMEs by headings/paragraphs (which
 * produces noisy chunks with badges, CI configs, and sponsor lists), this
 * service asks the LLM to distill the README into a concise, focused summary
 * that captures what the project does and what expertise it demonstrates.
 * <p>
 * Includes retry logic (3 attempts with exponential backoff) to handle
 * intermittent timeouts and rate limiting from the Ollama cloud model.
 */
@Service
public class LlmSummaryService {

    private static final Logger log = LoggerFactory.getLogger(LlmSummaryService.class);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final int MAX_RETRIES = 3;
    private static final int README_MAX_CHARS = 6000;
    private static final int WEBSITE_MAX_CHARS = 8000;

    private final ChatModel chatModel;

    public LlmSummaryService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * Summarize a GitHub repository README for semantic search.
     * Returns 200-300 tokens of clean, focused text about what the project
     * does and what expertise it demonstrates.
     *
     * @return the summary, or null if all retries fail
     */
    public String summarizeGithubRepo(
            String repoName,
            String description,
            String language,
            List<String> topics,
            String readme) {

        String topicsStr = topics != null && !topics.isEmpty()
                ? String.join(", ", topics) : "none";

        String truncatedReadme = truncateReadme(readme, README_MAX_CHARS);

        String prompt = """
                You are a technical summarizer for a semantic search system that matches experts to projects.

                Summarize this GitHub repository in 200-300 words. Focus on:
                - What the project does (its purpose and use case)
                - What technologies, languages, and frameworks it uses
                - What expertise or skills it demonstrates
                - Notable features and capabilities

                Write in plain text (no markdown, no headers, no bullet points, no code blocks).
                Only include information from the README and description — do not add or invent details.
                Be concise and factual. Start directly with what the project is.

                Repository: %s
                Description: %s
                Primary Language: %s
                Topics: %s

                README:
                %s""".formatted(repoName, description, language, topicsStr, truncatedReadme);

        // Retry with exponential backoff
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                long start = System.currentTimeMillis();
                String response = chatModel.call(new Prompt(prompt)).getResult().getOutput().getText();
                long elapsed = System.currentTimeMillis() - start;

                if (response != null && !response.isBlank()) {
                    log.info("LLM summary for {} (attempt {}/{}, {}ms): {} chars",
                            repoName, attempt, MAX_RETRIES, elapsed, response.length());
                    return response.trim();
                }
                log.warn("LLM returned empty response for {} (attempt {}/{})", repoName, attempt, MAX_RETRIES);
            } catch (Exception e) {
                log.warn("LLM attempt {}/{} failed for {} ({}): {}",
                        attempt, MAX_RETRIES, repoName, e.getClass().getSimpleName(), e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        long backoff = 2000L * attempt; // 2s, 4s, 6s
                        log.debug("Retrying in {}ms...", backoff);
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.warn("All {} LLM attempts failed for {} — falling back to mechanical stripping", MAX_RETRIES, repoName);
        return null;
    }

    /**
     * Clean a Swiss commercial register purpose (Zweck) by extracting ONLY the
     * substantive business description and discarding all legal boilerplate.
     * <p>
     * Swiss company purposes always have the same structure:
     * <ol>
     *   <li>1-3 sentences describing what the company actually does (KEEP)</li>
     *   <li>Legal boilerplate: branches, real estate, financing, guarantees,
     *       patents, insurance, catch-all clauses (DISCARD)</li>
     * </ol>
     * The prompt uses a structural approach (keep part 1, discard part 2) with
     * an exhaustive concept list, a 1-2 sentence output constraint, and an
     * example. Output is in English.
     *
     * @param companyName the company name (for context)
     * @param rawZweck the raw purpose text from Zefix
     * @return cleaned purpose text, or the raw text if LLM fails
     */
    public String cleanZweck(String companyName, String rawZweck) {
        if (rawZweck == null || rawZweck.isBlank()) return "";
        if (rawZweck.length() < 50) return rawZweck.trim();

        String prompt = """
                You are extracting the business purpose from a Swiss commercial register entry (Zefix Zweck).

                A Zweck always has the same structure:
                1. FIRST: 1-3 sentences describing what the company actually does (the substantive purpose)
                2. THEN: Legal boilerplate — standard clauses that appear in almost every Swiss company registration

                Your job: extract ONLY part 1 — what the company actually does. Discard EVERYTHING else.

                DISCARD any sentence that mentions ANY of these concepts (in any language):
                - Establishing branches, subsidiaries, or affiliated companies
                - Acquiring, managing, or selling real estate / properties / Liegenschaften
                - Financing, guarantees, sureties, securities, collateral
                - Participating in or holding shares in other companies / investments
                - Acquiring, managing, or licensing patents, trademarks, or intellectual property
                - Providing loans or financial services incidental to the purpose
                - Insurance or risk management activities
                - "All business related to / connected with the purpose" catch-all clauses
                - Hiring or employing staff / personnel matters
                - Any activity described as "can", "may", "is entitled to", "kann", "darf"

                KEEP only sentences that describe:
                - What the company produces, sells, or services
                - Their industry, domain, or field of expertise
                - Specific products, technologies, or methods they use
                - Their target market or customer segment

                Rules:
                - Output 1-2 sentences MAXIMUM. If the substantive purpose is longer, condense it.
                - Write in plain text (no markdown, no headers). Write in English.
                - Do not add information not in the original text.
                - If the entire Zweck is boilerplate with no substantive purpose, output just the company name and industry.

                Example input: "Die Gesellschaft bezweckt die Entwicklung und den Vertrieb von Softwarelösungen für das Regulierungsmanagement. Sie kann Zweigniederlassungen errichten, sich an anderen Unternehmen beteiligen, Grundeigentum erwerben und veräussern sowie Finanzierungen gewähren und Sicherheiten bestellen. Alle Geschäfte, die mit dem Zweck im Zusammenhang stehen, sind zulässig."
                Example output: "Development and distribution of software solutions for regulatory management."

                Company: %s

                Zweck:
                %s""".formatted(companyName, rawZweck);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                long start = System.currentTimeMillis();
                String response = chatModel.call(new Prompt(prompt)).getResult().getOutput().getText();
                long elapsed = System.currentTimeMillis() - start;

                if (response != null && !response.isBlank()) {
                    log.info("Zweck cleanup for {} (attempt {}/{}, {}ms): {} chars → {} chars",
                            companyName, attempt, MAX_RETRIES, elapsed, rawZweck.length(), response.length());
                    return response.trim();
                }
            } catch (Exception e) {
                log.warn("Zweck cleanup attempt {}/{} failed for {} ({}): {}",
                        attempt, MAX_RETRIES, companyName, e.getClass().getSimpleName(), e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(2000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.warn("All {} LLM attempts failed for Zweck cleanup of {} — using raw text", MAX_RETRIES, companyName);
        return rawZweck.trim();
    }

    /**
     * Expand a short manual text entry into a richer description for better embedding quality.
     * <p>
     * When a user types something like "React Dev", the embedding has very little
     * semantic signal. This method uses Gemini to expand it into a descriptive
     * paragraph — but only elaborates on what the user wrote. It does NOT invent
     * specific skills, companies, years of experience, or credentials not in
     * the original text.
     *
     * @param shortText the user's short text (e.g. "React Dev")
     * @param name optional name of the person/org (for context, not added to output)
     * @return expanded descriptive text, or null if LLM fails
     */
    public String enrichManualText(String shortText, String name) {
        if (shortText == null || shortText.isBlank()) return null;

        String contextName = name != null && !name.isBlank() ? name : "the person";

        String prompt = """
                You are expanding a short self-description into a richer profile paragraph for a semantic search system.

                The user wrote a very brief description of themselves. Expand it into 2-4 sentences that:
                - Elaborate on what the user actually wrote (e.g. "React Dev" → "React developer experienced in building web applications using React, JavaScript/TypeScript, and modern frontend development practices.")
                - Stay faithful to the original — do NOT invent specific skills, companies, years of experience, projects, or credentials not mentioned in the original text
                - Add general context about the field/role only (e.g. "building web applications" for a React developer, "data analysis and visualization" for a data analyst)
                - Write in plain text (no markdown, no bullet points, no headers)
                - Write in English

                Write in third person ("He/She is..." or "The person is..." or just start with the role name).
                Be concise and factual. Do not add information beyond reasonable field-level context.

                Original text by %s:
                %s""".formatted(contextName, shortText);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                long start = System.currentTimeMillis();
                String response = chatModel.call(new Prompt(prompt)).getResult().getOutput().getText();
                long elapsed = System.currentTimeMillis() - start;

                if (response != null && !response.isBlank()) {
                    log.info("Manual text enrichment (attempt {}/{}, {}ms): {} chars → {} chars",
                            attempt, MAX_RETRIES, elapsed, shortText.length(), response.length());
                    return response.trim();
                }
            } catch (Exception e) {
                log.warn("Manual text enrichment attempt {}/{} failed ({}): {}",
                        attempt, MAX_RETRIES, e.getClass().getSimpleName(), e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(2000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.warn("All {} LLM attempts failed for manual text enrichment — using raw text", MAX_RETRIES);
        return null;
    }

    /**
     * Generate a description for a GitHub repo that has no README.
     * Uses just the repo name, description, language, and topics to produce
     * 2-3 sentences of natural language for better embedding quality.
     *
     * @return the description, or null if all retries fail
     */
    public String describeGithubRepoFromMetadata(
            String repoName,
            String description,
            String language,
            List<String> topics) {

        String topicsStr = topics != null && !topics.isEmpty()
                ? String.join(", ", topics) : "none";
        String descStr = description != null && !description.isBlank() ? description : "(no description provided)";
        String langStr = language != null && !language.isBlank() ? language : "unknown";

        String prompt = """
                You are a technical summarizer for a semantic search system that matches experts to projects.

                A GitHub repository has no README. Based on its name, description, language, and topics,
                write 2-3 sentences about what this project likely does and what expertise it demonstrates.

                Rules:
                - Write in plain text (no markdown, no headers, no bullet points)
                - Be concise and factual
                - Only use information provided — do not invent specific features, frameworks, or details
                - If the description is vague, keep your summary general (e.g. "utility tools for X")
                - Start directly with what the project is

                Repository: %s
                Description: %s
                Primary Language: %s
                Topics: %s""".formatted(repoName, descStr, langStr, topicsStr);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                long start = System.currentTimeMillis();
                String response = chatModel.call(new Prompt(prompt)).getResult().getOutput().getText();
                long elapsed = System.currentTimeMillis() - start;

                if (response != null && !response.isBlank()) {
                    log.info("LLM metadata description for {} (attempt {}/{}, {}ms): {} chars",
                            repoName, attempt, MAX_RETRIES, elapsed, response.length());
                    return response.trim();
                }
            } catch (Exception e) {
                log.warn("LLM metadata attempt {}/{} failed for {} ({}): {}",
                        attempt, MAX_RETRIES, repoName, e.getClass().getSimpleName(), e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(2000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.warn("All {} LLM attempts failed for {} metadata — falling back to mechanical", MAX_RETRIES, repoName);
        return null;
    }

    /**
     * Summarize website content (personal or company) into clean, searchable text
     * for embedding. Replaces the fragile CSS-selector boilerplate removal with
     * semantic understanding — Gemini strips navigation, footer, marketing fluff
     * and produces a focused description of what the person/company does.
     *
     * @param domain       website domain (e.g. "apiax.com")
     * @param siteName     page title or og:site_name (may be null)
     * @param entityType   "person" or "organization" — guides the summary focus
     * @param rawText      concatenated raw text from all crawled pages
     * @return clean summary text, or null if all retries fail
     */
    public String summarizeWebsiteContent(
            String domain,
            String siteName,
            String entityType,
            String rawText) {

        if (rawText == null || rawText.isBlank()) return null;
        String truncated = truncateText(rawText, WEBSITE_MAX_CHARS);
        String siteStr = siteName != null && !siteName.isBlank() ? siteName : domain;

        boolean isCompany = "organization".equalsIgnoreCase(entityType);

        String prompt = isCompany ? """
                You are a content summarizer for a semantic search system that matches companies to projects and experts.

                Below is the raw text extracted from the website of %s.
                Write a concise but comprehensive summary (200-500 words) of what this company does.

                Focus on:
                - What the company does (their core business, services, products)
                - Their industry and domain expertise
                - Key technologies, methods, or approaches they use
                - Team size, locations, and markets they serve
                - Any specific products or solutions mentioned

                Rules:
                - Write in plain text (no markdown, no headers, no bullet points)
                - Be factual — only use information from the text below
                - Skip navigation menus, cookie notices, contact forms, social media links
                - Skip marketing slogans and testimonials
                - Write in English
                - Start directly with what the company does

                Website: %s
                Raw text:
                %s""".formatted(siteStr, siteStr, truncated) : """
                You are a content summarizer for a semantic search system that matches experts to projects.

                Below is the raw text extracted from the personal website of %s.
                Write a concise but comprehensive summary (200-500 words) about this person.

                Focus on:
                - Who they are (name, role, profession)
                - Their skills, expertise, and areas of work
                - Notable projects, services, or offerings
                - Their experience and background
                - Location and availability if mentioned

                Rules:
                - Write in plain text (no markdown, no headers, no bullet points)
                - Be factual — only use information from the text below
                - Skip navigation menus, cookie notices, contact forms, social media links
                - Skip testimonials and reviews from others
                - Write in English
                - Start directly with who they are and what they do

                Website: %s
                Raw text:
                %s""".formatted(siteStr, siteStr, truncated);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                long start = System.currentTimeMillis();
                String response = chatModel.call(new Prompt(prompt)).getResult().getOutput().getText();
                long elapsed = System.currentTimeMillis() - start;

                if (response != null && !response.isBlank()) {
                    log.info("LLM website summary for {} (attempt {}/{}, {}ms): {} chars",
                            domain, attempt, MAX_RETRIES, elapsed, response.length());
                    return response.trim();
                }
                log.warn("LLM returned empty response for {} (attempt {}/{})", domain, attempt, MAX_RETRIES);
            } catch (Exception e) {
                log.warn("LLM website attempt {}/{} failed for {} ({}): {}",
                        attempt, MAX_RETRIES, domain, e.getClass().getSimpleName(), e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(2000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.warn("All {} LLM attempts failed for website {} — falling back to raw text", MAX_RETRIES, domain);
        return null;
    }

    // ─── Entity-level competence enrichment (post-ingestion) ───

    private static final int SUMMARY_MAX_SOURCE_CHARS = 1200;
    private static final int TAGS_MAX_SOURCE_CHARS = 1000;

    /**
     * Generate a rich competence description for an entity, aggregating ALL
     * its sources. Called after each ingestion so the summary gets richer
     * as more sources are added.
     * <p>
     * Produces 3-5 sentences in English that describe what the person or organization does, their expertise,
     * and notable evidence from their sources.
     *
     * @param entityName  display name of the entity
     * @param entityType   "person" or "organization"
     * @param headline     the existing short headline (may be null)
     * @param sourceContents  list of (sourceType, label, coreText) tuples — one per source
     * @return the generated summary, or null if all retries fail
     */
    public String generateCompetenceSummary(
            String entityName,
            String entityType,
            String headline,
            List<SourceContent> sourceContents) {

        if (sourceContents.isEmpty()) return null;

        boolean isOrg = "organization".equalsIgnoreCase(entityType);
        StringBuilder sourcesBlock = new StringBuilder();
        for (SourceContent sc : sourceContents) {
            String text = truncateText(sc.coreText(), SUMMARY_MAX_SOURCE_CHARS);
            sourcesBlock.append("--- Quelle: ").append(sc.sourceType())
                    .append(" (Label: ").append(sc.label() != null ? sc.label() : "-").append(") ---\n")
                    .append(text).append("\n\n");
        }

        String prompt;
        if (isOrg) {
            prompt = """
                    You are writing a competence profile for a Swiss matching platform that connects organizations with experts and projects.

                    Below are the extracted contents from ALL sources of the organization "%s".
                    Write a rich, factual competence description in 3-5 sentences that covers:
                    - What the organization does (core business, services, products)
                    - Their industry domain and technical expertise
                    - Key technologies, methods, or specializations
                    - Notable projects, references, or achievements mentioned in the sources

                    Rules:
                    - Write in English
                    - Write in plain text (no markdown, no headers, no bullet points)
                    - Be factual — only use information from the sources below
                    - Do NOT invent specific details not present in the sources
                    - Start directly with what the organization does — no introductory filler
                    - If sources are sparse, keep it concise but still informative

                    Entity: %s
                    Current headline: %s

                    Sources:
                    %s""".formatted(entityName, entityName, headline != null ? headline : "(none)", sourcesBlock);
        } else {
            prompt = """
                    You are writing a competence profile for a Swiss matching platform that connects experts with projects.

                    Below are the extracted contents from ALL sources of "%s".
                    Write a rich, factual competence description in 3-5 sentences that covers:
                    - Who they are (role, profession, current position)
                    - Their skills, expertise, and areas of work
                    - Notable projects, publications, or achievements
                    - Technologies and methods they work with

                    Rules:
                    - Write in English
                    - Write in plain text (no markdown, no headers, no bullet points)
                    - Be factual — only use information from the sources below
                    - Do NOT invent specific details not present in the sources
                    - Start directly with who they are and what they do — no introductory filler
                    - If sources are sparse, keep it concise but still informative

                    Entity: %s
                    Current headline: %s

                    Sources:
                    %s""".formatted(entityName, entityName, headline != null ? headline : "(none)", sourcesBlock);
        }

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                long start = System.currentTimeMillis();
                String response = chatModel.call(new Prompt(prompt)).getResult().getOutput().getText();
                long elapsed = System.currentTimeMillis() - start;

                if (response != null && !response.isBlank()) {
                    log.info("Competence summary for {} (attempt {}/{}, {}ms): {} chars",
                            entityName, attempt, MAX_RETRIES, elapsed, response.length());
                    return response.trim();
                }
            } catch (Exception e) {
                log.warn("Competence summary attempt {}/{} failed for {} ({}): {}",
                        attempt, MAX_RETRIES, entityName, e.getClass().getSimpleName(), e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(2000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.warn("All {} LLM attempts failed for competence summary of {} — keeping existing", MAX_RETRIES, entityName);
        return null;
    }

    /**
     * Derive competence tags (Kompetenzen) from an entity's sources. The LLM
     * analyses ALL source contents and returns a list of short, specific tags
         * that describe the entity's skills, expertise, and domain.
     * <p>
     * Tags are normalized to a slug for the controlled vocabulary. The caller
     * is responsible for upserting into the tags table and entity_tags with
     * origin='derived'.
     *
     * @param entityName  display name (for context)
     * @param entityType  "person" or "organization"
     * @param sourceContents  list of (sourceType, label, coreText) tuples
     * @param maxTags     maximum number of tags to derive (default 8)
     * @return list of tag labels (e.g. ["Embedded", "C/C++", "IoT"]), or empty on failure
     */
    public List<String> deriveTags(
            String entityName,
            String entityType,
            List<SourceContent> sourceContents,
            int maxTags) {

        if (sourceContents.isEmpty()) return List.of();

        boolean isOrg = "organization".equalsIgnoreCase(entityType);
        StringBuilder sourcesBlock = new StringBuilder();
        for (SourceContent sc : sourceContents) {
            String text = truncateText(sc.coreText(), TAGS_MAX_SOURCE_CHARS);
            sourcesBlock.append("--- Quelle: ").append(sc.sourceType())
                    .append(" (Label: ").append(sc.label() != null ? sc.label() : "-").append(") ---\n")
                    .append(text).append("\n\n");
        }

        String prompt = """
                You are a tagging assistant for a Swiss expert-matching platform.

                Below are the extracted contents from ALL sources of "%s" (%s).
                Derive up to %d competence tags (Kompetenzen) that describe this entity's skills, expertise, and domain.

                Rules:
                - Each tag is 1-3 words, specific, and commonly understood (e.g. "Embedded", "C/C++", "IoT", "Robotik", "Zephyr", "CI/CD")
                - Tags should reflect what is actually evidenced in the sources — do NOT invent skills not mentioned
                - For organizations, include industry/domain tags and technology tags
                - For persons, include skill, technology, and methodology tags
                - Tags should be in English
                - Do not include generic tags like "Software" or "IT" unless specifically evidenced
                - Return ONLY the tags, one per line, no numbering, no explanations

                Entity: %s
                Type: %s

                Sources:
                %s""".formatted(entityName, isOrg ? "Organisation" : "Person", maxTags, entityName, isOrg ? "Organisation" : "Person", sourcesBlock);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                long start = System.currentTimeMillis();
                String response = chatModel.call(new Prompt(prompt)).getResult().getOutput().getText();
                long elapsed = System.currentTimeMillis() - start;

                if (response != null && !response.isBlank()) {
                    List<String> tags = parseTagList(response, maxTags);
                    log.info("Derived {} tags for {} (attempt {}/{}, {}ms): {}",
                            tags.size(), entityName, attempt, MAX_RETRIES, elapsed, tags);
                    return tags;
                }
            } catch (Exception e) {
                log.warn("Tag derivation attempt {}/{} failed for {} ({}): {}",
                        attempt, MAX_RETRIES, entityName, e.getClass().getSimpleName(), e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(2000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.warn("All {} LLM attempts failed for tag derivation of {} — keeping existing tags", MAX_RETRIES, entityName);
        return List.of();
    }

    /**
     * Parse the LLM's tag response into a clean list. Handles newline-separated,
     * comma-separated, and bulleted responses.
     */
    private List<String> parseTagList(String response, int maxTags) {
        List<String> tags = new ArrayList<>();
        for (String line : response.split("\n")) {
            String cleaned = line.trim()
                    .replaceAll("^[-*•\\d+.\\s)]+", "")  // strip bullets/numbering
                    .trim();
            if (cleaned.isEmpty() || cleaned.length() > 50) continue;
            // Handle comma-separated on a single line
            for (String part : cleaned.split(",")) {
                String tag = part.trim();
                if (!tag.isEmpty() && tag.length() <= 50 && !tags.contains(tag)) {
                    tags.add(tag);
                    if (tags.size() >= maxTags) return tags;
                }
            }
        }
        return tags;
    }

    private String truncateText(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) return text;
        return text.substring(0, maxChars) + "\n[... content truncated ...]";
    }

    private String truncateReadme(String readme, int maxChars) {
        if (readme == null || readme.length() <= maxChars) return readme;
        return readme.substring(0, maxChars) + "\n[... README truncated ...]";
    }

    // ─── Grounded match rationale (search, §6.4) ───

    private static final int RATIONALE_EVIDENCE_MAX_CHARS = 1200;

    /**
     * Generate a grounded German rationale for why an entity matches a query,
     * citing only the passed evidence (reference §6.4). The model sees the query,
     * a thin entity profile, and the winning chunks — never the full entity — and
     * returns JSON with a 1–3 sentence rationale plus per-claim {@code source_ids}.
     * <p>
     * The caller MUST still validate the citations (§6.5): a returned claim may
     * cite an id that isn't in {@code evidence}, and those must be dropped before
     * anything reaches a card. Returns {@code null} when the LLM is unavailable or
     * the response can't be parsed — the caller degrades to an evidence-only card.
     *
     * @param queryText the natural-language query
     * @param name      entity display name
     * @param role      entity headline / role (may be null)
     * @param org       entity org name (may be null)
     * @param evidence  the winning chunks, each with a stable citation id (S1, S2, …)
     * @return the grounded rationale, or {@code null} on failure
     */
    public MatchRationale generateMatchRationale(
            String queryText, String name, String role, String org,
            List<RationaleEvidence> evidence) {

        if (queryText == null || queryText.isBlank() || evidence == null || evidence.isEmpty()) {
            return null;
        }

        StringBuilder evidenceBlock = new StringBuilder();
        for (RationaleEvidence ev : evidence) {
            String text = truncateText(ev.text(), RATIONALE_EVIDENCE_MAX_CHARS);
            evidenceBlock.append('[').append(ev.id()).append("] (")
                    .append(ev.type() != null ? ev.type() : "quelle").append(") ")
                    .append(ev.label() != null ? ev.label() : "").append("\n")
                    .append(text != null ? text : "").append("\n\n");
        }

        String profile = name
                + (role != null && !role.isBlank() ? " — " + role : "")
                + (org != null && !org.isBlank() ? " (" + org + ")" : "");

        String prompt = """
                Du bist ein Matching-Assistent für eine Schweizer Innovationsplattform.
                Nutze AUSSCHLIESSLICH die bereitgestellten Belege. Mache keine Aussage, die
                nicht durch die Belege gestützt ist. Zitiere je Aussage die passende(n)
                source_id(s). Schreibe auf Deutsch, sachlich, 1–3 Sätze.

3                Antworte NUR mit reinem JSON in genau diesem Format (keine Markdown-Codeblöcke,
                kein Text davor oder danach):
                {"rationale":"<1–3 Sätze auf Deutsch>","claims":[{"text":"<Teilaussage>","source_ids":["S1"]}]}

                Anfrage: %s
                Profil: %s

                Belege:
                %s""".formatted(queryText, profile, evidenceBlock);

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String response = chatModel.call(new Prompt(prompt)).getResult().getOutput().getText();
                MatchRationale parsed = parseRationale(response);
                if (parsed != null && parsed.rationale() != null && !parsed.rationale().isBlank()) {
                    log.info("Rationale for \"{}\" (attempt {}/2): {} claim(s)",
                            name, attempt, parsed.claims() != null ? parsed.claims().size() : 0);
                    return parsed;
                }
            } catch (Exception e) {
                log.warn("Rationale attempt {}/2 failed for {} ({}): {}",
                        attempt, name, e.getClass().getSimpleName(), e.getMessage());
                if (attempt < 2) {
                    try {
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.warn("Rationale generation failed for {} — caller falls back to evidence-only", name);
        return null;
    }

    /** Parse the generator's JSON (tolerating ```json fences) into a MatchRationale. */
    private MatchRationale parseRationale(String response) {
        if (response == null || response.isBlank()) return null;
        String json = stripCodeFences(response);
        try {
            JsonNode root = JSON.readTree(json);
            String rationale = root.path("rationale").asString("");
            if (rationale.isBlank()) return null;

            List<RationaleClaim> claims = new ArrayList<>();
            JsonNode claimsNode = root.path("claims");
            if (claimsNode.isArray()) {
                for (JsonNode c : claimsNode) {
                    String text = c.path("text").asString("");
                    List<String> ids = new ArrayList<>();
                    JsonNode idsNode = c.path("source_ids");
                    if (idsNode.isArray()) {
                        for (JsonNode id : idsNode) {
                            String s = id.asString("");
                            if (!s.isBlank()) ids.add(s.trim());
                        }
                    }
                    if (!text.isBlank()) {
                        claims.add(new RationaleClaim(text.trim(), ids));
                    }
                }
            }
            return new MatchRationale(rationale.trim(), claims);
        } catch (Exception e) {
            log.warn("Could not parse rationale JSON ({}): {}", e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    private static String stripCodeFences(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            if (firstNl >= 0) t = t.substring(firstNl + 1);
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        }
        return t.trim();
    }

    /**
     * Translate text to English for search normalization. Used at ingestion
     * time (LinkedIn chunks) and at query time (German queries). If the text
     * is already English, Gemini returns it unchanged. If Gemini fails, the
     * original text is returned so the semantic leg (multilingual E5) still
     * works.
     *
     * @param text the text to translate (any language)
     * @return English text, or the original if translation fails
     */
    public String translateToEnglish(String text) {
        if (text == null || text.isBlank()) return text;
        if (text.length() < 10) return text;

        String truncated = truncateText(text, WEBSITE_MAX_CHARS);

        String prompt = """
                Translate the following text to English. If it is already in English, return it unchanged.
                Preserve all technical terms, names, and proper nouns as-is.
                Keep the same structure (headings, paragraphs). Do not add or remove information.

                Text:
                %s""".formatted(truncated);

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                long start = System.currentTimeMillis();
                String response = chatModel.call(new Prompt(prompt)).getResult().getOutput().getText();
                long elapsed = System.currentTimeMillis() - start;

                if (response != null && !response.isBlank()) {
                    log.info("Translation to English (attempt {}/{}, {}ms): {} -> {} chars",
                            attempt, 2, elapsed, text.length(), response.length());
                    return response.trim();
                }
            } catch (Exception e) {
                log.warn("Translation attempt {}/{} failed ({}): {}",
                        attempt, 2, e.getClass().getSimpleName(), e.getMessage());
                if (attempt < 2) {
                    try {
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.warn("Translation failed - using original text (semantic leg still works)");
        return text;
    }
}