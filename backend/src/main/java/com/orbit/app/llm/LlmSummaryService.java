package com.orbit.app.llm;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

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

    private static final int MAX_RETRIES = 3;
    private static final int README_MAX_CHARS = 6000;

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
     * Clean a Swiss commercial register purpose (Zweck) by removing legal boilerplate.
     * <p>
     * Swiss company purposes follow a standard pattern:
     * <ol>
     *   <li>One substantive sentence describing what the company actually does</li>
     *   <li>Several sentences of legal boilerplate about branches, subsidiaries,
     *       real estate, financing, guarantees — identical across most companies</li>
     * </ol>
     * <p>
     * We use Gemini to extract only the substantive business description, producing
     * clean text for high-quality semantic embeddings.
     *
     * @param companyName the company name (for context)
     * @param rawZweck the raw purpose text from Zefix
     * @return cleaned purpose text, or the raw text if LLM fails
     */
    public String cleanZweck(String companyName, String rawZweck) {
        if (rawZweck == null || rawZweck.isBlank()) return "";
        if (rawZweck.length() < 50) return rawZweck.trim();

        String prompt = """
                You are extracting the substantive business purpose from a Swiss commercial register entry.

                The following text is the registered purpose (Zweck) of a Swiss company. It contains:
                1. A substantive description of what the company actually does (usually the first sentence)
                2. Legal boilerplate about branches, subsidiaries, real estate, financing, and guarantees — REMOVE ALL OF THIS

                Extract ONLY the substantive business description — what the company actually does.
                Remove all standard legal clauses about:
                - Establishing branches or subsidiaries ("Zweigniederlassungen", "Tochtergesellschaften")
                - Acquiring/selling real estate ("Grundeigentum erwerben")
                - Financing and guarantees ("Finanzierungen", "Garantien", "Bürgschaften")
                - Participating in other companies ("sich an anderen Unternehmen beteiligen")
                - Generic "all business related to the purpose" clauses

                Write in plain text (no markdown, no headers). Keep the original language (German/French/Italian).
                Be concise — typically 1-3 sentences. Do not add any information not in the original text.

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

    private String truncateReadme(String readme, int maxChars) {
        if (readme == null || readme.length() <= maxChars) return readme;
        return readme.substring(0, maxChars) + "\n[... README truncated ...]";
    }
}