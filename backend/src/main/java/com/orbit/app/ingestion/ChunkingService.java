package com.orbit.app.ingestion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

/**
 * Recursive text splitter that chunks text by token count with overlap.
 * <p>
 * Strategy: try splitting on the largest boundary first (section → paragraph →
 * sentence). If a segment exceeds {@code maxTokens}, recurse with the next
 * smaller boundary. If no smaller boundary exists, split by word boundaries
 * (preserving original whitespace). Overlap is applied between consecutive
 * chunks from the same source.
 * <p>
 * Section detection: when splitting by ## headings, each section includes the
 * heading + all content until the next heading. The heading text becomes the
 * chunk's {@code section} field. If no headings are found, falls back to
 * paragraph → sentence splitting with {@code section = null}.
 * <p>
 * CRITICAL: all splitting methods preserve the original text's whitespace by
 * working with substrings of the original text. The tokenize() method is used
 * ONLY for counting tokens, never for reconstructing text.
 */
@Service
public class ChunkingService {

    // Only split at ## (level 2) headings — ### sub-headings stay within their parent section
    private static final Pattern HEADING_PATTERN = Pattern.compile("^##[^#].+", Pattern.MULTILINE);
    private static final Pattern PARAGRAPH_BOUNDARY = Pattern.compile("\n\\s*\n");
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?])\\s+(?=[A-ZÄÖÜ0-9])");
    private static final Pattern WORD_BOUNDARY = Pattern.compile("\\s+");

    /**
     * Recursively split text into chunks of at most {@code maxTokens} tokens,
     * with {@code overlapTokens} overlap between consecutive chunks.
     */
    public List<ChunkSegment> recursiveSplit(String text, int maxTokens, int overlapTokens) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<ChunkSegment> raw = splitRecursive(text.trim(), maxTokens, 0);
        return applyOverlap(raw, overlapTokens, maxTokens);
    }

    private List<ChunkSegment> splitRecursive(String text, int maxTokens, int depth) {
        int tokenCount = countTokens(text);
        if (tokenCount <= maxTokens) {
            return List.of(new ChunkSegment(text, extractSection(text), tokenCount));
        }

        if (depth == 0) {
            List<ChunkSegment> sections = splitBySections(text, maxTokens);
            if (sections.size() > 1) {
                return sections;
            }
        }

        if (depth <= 1) {
            List<String> paragraphs = splitByPattern(text, PARAGRAPH_BOUNDARY);
            if (paragraphs.size() > 1) {
                return mergeAndRecurse(paragraphs, maxTokens, depth + 1);
            }
        }

        if (depth <= 2) {
            List<String> sentences = splitByPattern(text, SENTENCE_BOUNDARY);
            if (sentences.size() > 1) {
                return mergeAndRecurse(sentences, maxTokens, depth + 1);
            }
        }

        // Last resort: split by words, preserving original whitespace
        return splitByWords(text, maxTokens);
    }

    /**
     * Split text by markdown ## headings. Each section includes the heading
     * and all content until the next heading. The heading text becomes the
     * section label for all chunks derived from that section.
     */
    private List<ChunkSegment> splitBySections(String text, int maxTokens) {
        Matcher matcher = HEADING_PATTERN.matcher(text);
        List<int[]> headingPositions = new ArrayList<>();
        while (matcher.find()) {
            headingPositions.add(new int[]{matcher.start(), matcher.end()});
        }

        if (headingPositions.isEmpty()) {
            return List.of();
        }

        List<ChunkSegment> sections = new ArrayList<>();

        // Content before the first heading (if any)
        if (headingPositions.get(0)[0] > 0) {
            String preContent = text.substring(0, headingPositions.get(0)[0]).trim();
            if (!preContent.isEmpty()) {
                int tokens = countTokens(preContent);
                if (tokens <= maxTokens) {
                    sections.add(new ChunkSegment(preContent, "Overview", tokens));
                } else {
                    sections.addAll(splitRecursive(preContent, maxTokens, 1));
                }
            }
        }

        // Each section: heading + content until next heading
        for (int i = 0; i < headingPositions.size(); i++) {
            int headingStart = headingPositions.get(i)[0];
            int headingEnd = headingPositions.get(i)[1];
            int contentEnd = (i + 1 < headingPositions.size())
                    ? headingPositions.get(i + 1)[0]
                    : text.length();

            String headingLine = text.substring(headingStart, headingEnd).trim();
            String sectionContent = text.substring(headingEnd, contentEnd).trim();
            String sectionLabel = truncateSectionLabel(extractHeadingText(headingLine));

            String fullSection = headingLine + "\n" + sectionContent;
            int tokens = countTokens(fullSection);

            if (tokens <= maxTokens) {
                sections.add(new ChunkSegment(fullSection.trim(), sectionLabel, tokens));
            } else {
                List<ChunkSegment> subChunks = splitRecursive(fullSection, maxTokens, 1);
                for (ChunkSegment seg : subChunks) {
                    sections.add(new ChunkSegment(seg.text(), sectionLabel, seg.tokenCount()));
                }
            }
        }

        return sections;
    }

    private List<ChunkSegment> mergeAndRecurse(List<String> parts, int maxTokens, int depth) {
        List<ChunkSegment> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentTokens = 0;

        for (String part : parts) {
            int partTokens = countTokens(part);

            if (currentTokens + partTokens > maxTokens && !current.isEmpty()) {
                result.add(new ChunkSegment(current.toString().trim(), extractSection(current.toString()), currentTokens));
                current = new StringBuilder();
                currentTokens = 0;
            }

            if (partTokens > maxTokens) {
                if (!current.isEmpty()) {
                    result.add(new ChunkSegment(current.toString().trim(), extractSection(current.toString()), currentTokens));
                    current = new StringBuilder();
                    currentTokens = 0;
                }
                result.addAll(splitRecursive(part, maxTokens, depth));
            } else {
                current.append(part);
                currentTokens += partTokens;
            }
        }

        if (!current.isEmpty()) {
            result.add(new ChunkSegment(current.toString().trim(), extractSection(current.toString()), currentTokens));
        }

        return result;
    }

    /**
     * Split text by word boundaries, preserving original whitespace.
     * This is the last-resort splitter when no structural boundaries work.
     * It accumulates words until maxTokens is reached, then starts a new chunk.
     * The original text between word boundaries is preserved exactly.
     */
    private List<ChunkSegment> splitByWords(String text, int maxTokens) {
        // Find word positions: [start, end] for each word in the original text
        List<int[]> wordPositions = new ArrayList<>();
        Matcher matcher = WORD_BOUNDARY.matcher(text);
        int wordStart = 0;
        while (matcher.find()) {
            if (wordStart < matcher.start()) {
                wordPositions.add(new int[]{wordStart, matcher.start()});
            }
            wordStart = matcher.end();
        }
        if (wordStart < text.length()) {
            wordPositions.add(new int[]{wordStart, text.length()});
        }

        if (wordPositions.isEmpty()) {
            return List.of();
        }

        // Count tokens per word and accumulate into chunks
        List<ChunkSegment> result = new ArrayList<>();
        int chunkStartIdx = 0;
        int chunkTokens = 0;

        for (int i = 0; i < wordPositions.size(); i++) {
            String word = text.substring(wordPositions.get(i)[0], wordPositions.get(i)[1]);
            int wordTokens = countTokens(word);

            if (chunkTokens + wordTokens > maxTokens && chunkTokens > 0) {
                // Flush current chunk — use substring to preserve original text
                int start = wordPositions.get(chunkStartIdx)[0];
                int end = wordPositions.get(i - 1)[1];
                String chunkText = text.substring(start, end).trim();
                result.add(new ChunkSegment(chunkText, null, chunkTokens));
                chunkStartIdx = i;
                chunkTokens = 0;
            }

            chunkTokens += wordTokens;
        }

        // Last chunk
        if (chunkStartIdx < wordPositions.size()) {
            int start = wordPositions.get(chunkStartIdx)[0];
            int end = wordPositions.get(wordPositions.size() - 1)[1];
            String chunkText = text.substring(start, end).trim();
            result.add(new ChunkSegment(chunkText, null, chunkTokens));
        }

        return result;
    }

    /**
     * Apply overlap between consecutive chunks. The overlap is the last
     * ~overlapTokens tokens from the previous chunk, extracted as a substring
     * of the original text to preserve whitespace.
     */
    private List<ChunkSegment> applyOverlap(List<ChunkSegment> chunks, int overlapTokens, int maxTokens) {
        if (overlapTokens <= 0 || chunks.size() <= 1) {
            return chunks;
        }

        List<ChunkSegment> result = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            if (i == 0) {
                result.add(chunks.get(i));
            } else {
                String prevText = chunks.get(i - 1).text();
                String overlapText = extractLastWords(prevText, overlapTokens);

                // Trim overlap if it would exceed maxTokens
                int coreTokens = chunks.get(i).tokenCount();
                int affordableOverlap = Math.max(0, maxTokens - coreTokens);
                if (countTokens(overlapText) > affordableOverlap) {
                    overlapText = extractLastWords(prevText, affordableOverlap);
                }

                String merged = overlapText + " " + chunks.get(i).text();
                int mergedTokens = countTokens(merged);
                result.add(new ChunkSegment(merged.trim(), chunks.get(i).section(), mergedTokens));
            }
        }
        return result;
    }

    /**
     * Extract the last N tokens from text as a substring, preserving whitespace.
     * Works backwards from the end of the text, accumulating words until
     * tokenCount is reached.
     */
    private String extractLastWords(String text, int tokenCount) {
        if (tokenCount <= 0 || text == null || text.isBlank()) return "";

        // Split into words with their positions
        List<int[]> wordPositions = new ArrayList<>();
        Matcher matcher = WORD_BOUNDARY.matcher(text);
        int wordStart = 0;
        while (matcher.find()) {
            if (wordStart < matcher.start()) {
                wordPositions.add(new int[]{wordStart, matcher.start()});
            }
            wordStart = matcher.end();
        }
        if (wordStart < text.length()) {
            wordPositions.add(new int[]{wordStart, text.length()});
        }

        if (wordPositions.isEmpty()) return "";

        // Walk backwards accumulating tokens
        int accumulated = 0;
        int startIdx = wordPositions.size();
        for (int i = wordPositions.size() - 1; i >= 0 && accumulated < tokenCount; i--) {
            String word = text.substring(wordPositions.get(i)[0], wordPositions.get(i)[1]);
            accumulated += countTokens(word);
            startIdx = i;
        }

        if (startIdx >= wordPositions.size()) return "";
        return text.substring(wordPositions.get(startIdx)[0]);
    }

    private List<String> splitByPattern(String text, Pattern pattern) {
        List<String> parts = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        int lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                parts.add(text.substring(lastEnd, matcher.start()));
            }
            parts.add(text.substring(matcher.start(), matcher.end()));
            lastEnd = matcher.end();
        }
        if (lastEnd < text.length()) {
            parts.add(text.substring(lastEnd));
        }
        return parts;
    }

    private String extractSection(String text) {
        Matcher m = Pattern.compile("^#{1,6}\\s+(.+)").matcher(text.trim());
        if (m.find()) {
            return truncateSectionLabel(m.group(1).trim());
        }
        return null;
    }

    private String extractHeadingText(String headingLine) {
        return headingLine.replaceAll("^#{1,6}\\s+", "").trim();
    }

    /**
     * Truncate section labels to a reasonable length (max 80 chars).
     * Some READMEs have very long heading lines that include sub-headings.
     */
    private String truncateSectionLabel(String label) {
        if (label == null) return null;
        // Cut at first newline (sub-headings on next lines)
        int newline = label.indexOf('\n');
        if (newline > 0) label = label.substring(0, newline);
        // Cut at first ### sub-heading marker
        int sub = label.indexOf("###");
        if (sub > 0) label = label.substring(0, sub);
        // Trim to 80 chars at word boundary
        if (label.length() > 80) {
            int cut = label.lastIndexOf(' ', 80);
            label = label.substring(0, cut > 0 ? cut : 80) + "…";
        }
        return label.trim();
    }

    /**
     * Count tokens — approximates BERT WordPiece tokenization.
     * Used ONLY for counting, never for text reconstruction.
     */
    public int countTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        return tokenize(text).size();
    }

    /**
     * Tokenize text — approximates BERT WordPiece tokenization.
     * Returns individual word/punctuation tokens WITHOUT whitespace.
     * Used ONLY for counting tokens, never for reconstructing text.
     */
    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        String[] words = text.split("\\s+");
        for (String word : words) {
            if (word.isEmpty()) continue;
            StringBuilder current = new StringBuilder();
            for (int i = 0; i < word.length(); i++) {
                char c = word.charAt(i);
                if (isPunctuation(c)) {
                    if (!current.isEmpty()) {
                        tokens.add(current.toString());
                        current = new StringBuilder();
                    }
                    tokens.add(String.valueOf(c));
                } else {
                    current.append(c);
                }
            }
            if (!current.isEmpty()) {
                tokens.add(current.toString());
            }
        }
        return tokens;
    }

    private boolean isPunctuation(char c) {
        return ",.!?;:()[]{}\"'`/\\|<>=+-_*#@&$%^~".indexOf(c) >= 0;
    }
}