package com.orbit.app.ingestion.zefix;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.orbit.app.ingestion.IngestionException;
import com.orbit.app.ingestion.SourceAdapter;
import com.orbit.app.ingestion.dto.EntityProfile;
import com.orbit.app.ingestion.dto.ExternalId;
import com.orbit.app.ingestion.dto.RawContent;
import com.orbit.app.ingestion.dto.SourceRef;
import com.orbit.app.ingestion.zefix.dto.ZefixCompanySummary;
import com.orbit.app.llm.LlmSummaryService;

/**
 * Zefix adapter — ingests company data from the Swiss Central Business Name Index.
 * <p>
 * Flow:
 * <ol>
 *   <li>{@code fetch()} — search by company name (or direct UID lookup),
 *       retrieve full company details including the registered purpose (Zweck).</li>
 *   <li>{@code extractHighSignalCore()} — use Gemini to strip legal boilerplate
 *       from the Zweck, producing clean text for high-quality embeddings.</li>
 *   <li>{@code extractExternalIds()} — the UID (CHE-NNN.NNN.NNN) as the primary
 *       cross-source identifier.</li>
 * </ol>
 * <p>
 * The user provides a company name; the adapter searches Zefix and takes the
 * top match. The Zweck is the only free-text field — it describes what the
 * company does. All other fields (legal form, address, capital) are structured
 * metadata that goes into the entity, not the embedding.
 * <p>
 * Authentication: HTTP Basic Auth with ZEFIX_USER and ZEFIX_KEY env vars.
 *
 * @see <a href="https://www.zefix.admin.ch/ZefixPublicREST/swagger-ui/index.html">Zefix API Swagger</a>
 */
@Component
public class ZefixAdapter implements SourceAdapter {

    private static final Logger log = LoggerFactory.getLogger(ZefixAdapter.class);
    private static final String API_BASE = "https://www.zefix.admin.ch/ZefixPublicREST";

    private final RestClient restClient;
    private final LlmSummaryService llmSummaryService;

    @Value("${orbit.zefix.user:}")
    private String zefixUser;

    @Value("${orbit.zefix.key:}")
    private String zefixKey;

    public ZefixAdapter(LlmSummaryService llmSummaryService) {
        this.llmSummaryService = llmSummaryService;
        this.restClient = RestClient.builder()
                .baseUrl(API_BASE)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public String sourceType() {
        return "zefix";
    }

    // ─── fetch: search by name or UID, get full company details ───

    @Override
    public RawContent fetch(SourceRef ref) throws IngestionException {
        String uid = ref.context().get("zefix_uid");
        String companyName = ref.context().get("company_name");

        JsonNode company;

        if (uid != null && !uid.isBlank()) {
            // Direct UID lookup
            log.info("Zefix UID lookup: {}", uid);
            company = fetchCompanyByUid(uid);
        } else if (companyName != null && !companyName.isBlank()) {
            // Name search → take top result → fetch full details
            log.info("Zefix name search: {}", companyName);
            String foundUid = searchByName(companyName);
            if (foundUid == null) {
                throw new IngestionException("No company found for: " + companyName);
            }
            company = fetchCompanyByUid(foundUid);
        } else {
            throw new IngestionException("Either zefix_uid or company_name is required in context");
        }

        // Parse the company data
        String name = company.path("name").asText("");
        String rawUid = company.path("uid").asText("");
        String purpose = company.path("purpose").asText("");
        String legalSeat = company.path("legalSeat").asText("");
        String canton = company.path("canton").asText("");
        String status = company.path("status").asText("");

        JsonNode legalFormNode = company.path("legalForm");
        String legalFormShort = legalFormNode.path("shortName").path("de").asText("");
        String legalFormName = legalFormNode.path("name").path("de").asText("");

        JsonNode addrNode = company.path("address");
        String street = addrNode.path("street").asText("");
        String houseNumber = addrNode.path("houseNumber").asText("");
        String zipCode = addrNode.path("swissZipCode").asText("");
        String city = addrNode.path("city").asText("");

        String capitalNominal = company.path("capitalNominal").asText("");
        String capitalCurrency = company.path("capitalCurrency").asText("");

        log.info("Zefix company: {} (UID={}, status={}, purpose={} chars)",
                name, rawUid, status, purpose.length());

        // Build metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", name);
        metadata.put("uid", rawUid);
        metadata.put("ehraid", company.path("ehraid").asLong(0));
        metadata.put("legal_seat", legalSeat);
        metadata.put("canton", canton);
        metadata.put("status", status);
        metadata.put("legal_form_short", legalFormShort);
        metadata.put("legal_form_name", legalFormName);
        metadata.put("purpose_raw", purpose);
        metadata.put("street", street);
        metadata.put("house_number", houseNumber);
        metadata.put("zip_code", zipCode);
        metadata.put("city", city);
        metadata.put("capital_nominal", capitalNominal);
        metadata.put("capital_currency", capitalCurrency);
        metadata.put("cantonal_excerpt_url", company.path("cantonalExcerptWeb").asText(""));

        // The raw text stored in sources.raw_content is the purpose text
        return new RawContent(purpose, metadata, Instant.now());
    }

    // ─── extractHighSignalCore: Gemini cleanup of legal boilerplate ───

    @Override
    public String extractHighSignalCore(RawContent raw) {
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = raw.metadata();

        String name = (String) meta.getOrDefault("name", "Unknown");
        String rawPurpose = (String) meta.getOrDefault("purpose_raw", "");
        String legalFormShort = (String) meta.getOrDefault("legal_form_short", "");
        String legalSeat = (String) meta.getOrDefault("legal_seat", "");
        String canton = (String) meta.getOrDefault("canton", "");

        // Use Gemini to strip legal boilerplate from the Zweck
        String cleanPurpose = llmSummaryService.cleanZweck(name, rawPurpose);

        if (cleanPurpose.isBlank()) {
            // Fallback: build from legal form + seat
            StringBuilder fb = new StringBuilder();
            fb.append("## ").append(name).append("\n");
            if (!legalFormShort.isBlank() && !legalSeat.isBlank()) {
                fb.append(legalFormShort).append(" based in ").append(legalSeat);
                if (!canton.isBlank()) fb.append(", ").append(canton);
                fb.append(".\n");
            }
            return fb.toString().trim();
        }

        // Build the high-signal core: ## heading ensures ChunkingService
        // sets the company name as the chunk's section field
        StringBuilder sb = new StringBuilder();
        sb.append("## ").append(name).append("\n");
        if (!legalFormShort.isBlank() && !legalSeat.isBlank()) {
            sb.append(legalFormShort).append(" based in ").append(legalSeat);
            if (!canton.isBlank()) sb.append(", ").append(canton);
            sb.append(".\n");
        }
        sb.append("\n").append(cleanPurpose);

        return sb.toString().trim();
    }

    @Override
    public String buildLabel(RawContent raw) {
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = raw.metadata();
        return (String) meta.getOrDefault("name", "Unknown Company");
    }

    @Override
    public List<ExternalId> extractExternalIds(RawContent raw) {
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = raw.metadata();
        String uid = (String) meta.get("uid");
        if (uid != null && !uid.isBlank()) {
            // Normalize UID: CHE107721785 → CHE-107.721.785
            String normalized = normalizeUid(uid);
            return List.of(new ExternalId("zefix_uid", normalized));
        }
        return List.of();
    }

    @Override
    public EntityProfile deriveProfile(RawContent raw) {
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = raw.metadata();

        String name = (String) meta.getOrDefault("name", "Unknown Company");
        String legalFormShort = (String) meta.getOrDefault("legal_form_short", "");
        String legalSeat = (String) meta.getOrDefault("legal_seat", "");
        String canton = (String) meta.getOrDefault("canton", "");
        String status = (String) meta.getOrDefault("status", "");

        String headline = !legalFormShort.isBlank() && !legalSeat.isBlank()
                ? legalFormShort + " based in " + legalSeat
                : (status.isBlank() ? "Company" : status);

        Map<String, Object> profileMeta = new HashMap<>();
        profileMeta.put("zefix_uid", normalizeUid((String) meta.getOrDefault("uid", "")));
        profileMeta.put("zefix_ehraid", meta.getOrDefault("ehraid", 0));
        profileMeta.put("legal_form", meta.getOrDefault("legal_form_short", ""));
        profileMeta.put("legal_form_name", meta.getOrDefault("legal_form_name", ""));
        profileMeta.put("canton", canton);
        profileMeta.put("legal_seat", legalSeat);
        profileMeta.put("status", status);
        profileMeta.put("capital_nominal", meta.getOrDefault("capital_nominal", ""));
        profileMeta.put("capital_currency", meta.getOrDefault("capital_currency", ""));
        profileMeta.put("address", Map.of(
                "street", meta.getOrDefault("street", ""),
                "house_number", meta.getOrDefault("house_number", ""),
                "zip_code", meta.getOrDefault("zip_code", ""),
                "city", meta.getOrDefault("city", "")
        ));
        profileMeta.put("hr_verified", true);

        return new EntityProfile(
                "organization",
                name,
                headline,
                name,
                null,  // latitude — geocode from address in Stage 3
                null,  // longitude
                profileMeta
        );
    }

    // ─── Zefix API calls ───

    /**
     * Search companies by name for onboarding autocomplete. Returns up to
     * {@code limit} active companies with normalized UIDs. Missing credentials
     * or an API failure degrade to an empty list so the onboarding UI can fall
     * back to manual capture.
     */
    public List<ZefixCompanySummary> searchCompanies(String name, int limit) {
        if (zefixUser == null || zefixUser.isBlank() || zefixKey == null || zefixKey.isBlank()) {
            log.warn("Zefix search skipped: credentials not configured (ZEFIX_USER/ZEFIX_KEY)");
            return List.of();
        }
        try {
            String requestBody = "{\"name\":\"" + escapeJson(name) + "\",\"activeOnly\":true}";
            JsonNode response = restClient.post()
                    .uri("/api/v1/company/search")
                    .headers(h -> h.setBasicAuth(zefixUser, zefixKey))
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || !response.isArray() || response.isEmpty()) {
                return List.of();
            }

            return java.util.stream.StreamSupport.stream(response.spliterator(), false)
                    .limit(limit)
                    .map(node -> new ZefixCompanySummary(
                            node.path("name").asText(""),
                            normalizeUid(node.path("uid").asText("")),
                            node.path("legalSeat").asText(""),
                            node.path("canton").asText(""),
                            node.path("legalForm").path("shortName").path("de").asText(""),
                            node.path("status").asText("")))
                    .toList();
        } catch (Exception e) {
            log.warn("Zefix search failed for '{}': {}", name, e.getMessage());
            return List.of();
        }
    }

    /**
     * Search companies by name. Returns the UID of the top match.
     * The Zefix search returns CompanyShort[] — we take the first result.
     */
    private String searchByName(String name) throws IngestionException {
        if (zefixUser == null || zefixUser.isBlank() || zefixKey == null || zefixKey.isBlank()) {
            throw new IngestionException("Zefix credentials not configured (ZEFIX_USER/ZEFIX_KEY)");
        }

        try {
            String requestBody = "{\"name\":\"" + escapeJson(name) + "\",\"activeOnly\":true}";
            JsonNode response = restClient.post()
                    .uri("/api/v1/company/search")
                    .headers(h -> h.setBasicAuth(zefixUser, zefixKey))
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || !response.isArray() || response.isEmpty()) {
                log.warn("Zefix search for '{}' returned no results", name);
                return null;
            }

            // Take the first (top) result
            JsonNode first = response.get(0);
            String foundName = first.path("name").asText("");
            String uid = first.path("uid").asText("");
            String legalSeat = first.path("legalSeat").asText("");

            log.info("Zefix search for '{}' → top match: {} (UID={}, seat={})", name, foundName, uid, legalSeat);
            return uid;

        } catch (Exception e) {
            throw new IngestionException("Zefix search failed for: " + name, e);
        }
    }

    /**
     * Fetch full company details by UID.
     * The API returns CompanyFull[] (array), but for a UID lookup there's
     * typically exactly one element.
     */
    private JsonNode fetchCompanyByUid(String uid) throws IngestionException {
        if (zefixUser == null || zefixUser.isBlank() || zefixKey == null || zefixKey.isBlank()) {
            throw new IngestionException("Zefix credentials not configured (ZEFIX_USER/ZEFIX_KEY)");
        }

        try {
            JsonNode response = restClient.get()
                    .uri("/api/v1/company/uid/" + uid)
                    .headers(h -> h.setBasicAuth(zefixUser, zefixKey))
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || !response.isArray() || response.isEmpty()) {
                throw new IngestionException("Company not found for UID: " + uid);
            }

            return response.get(0);

        } catch (IngestionException e) {
            throw e;
        } catch (Exception e) {
            throw new IngestionException("Zefix UID lookup failed for: " + uid, e);
        }
    }

    // ─── Helpers ───

    /**
     * Normalize UID from API format (CHE107721785) to canonical format (CHE-107.721.785).
     */
    static String normalizeUid(String uid) {
        if (uid == null || uid.isBlank()) return "";
        // Already normalized?
        if (uid.contains("-")) return uid;
        // CHE + 9 digits → CHE-NNN.NNN.NNN
        if (uid.startsWith("CHE") && uid.length() == 12) {
            return "CHE-" + uid.substring(3, 6) + "." + uid.substring(6, 9) + "." + uid.substring(9, 12);
        }
        return uid;
    }

    /** Escape a string for embedding in a JSON string value. */
    private static String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}