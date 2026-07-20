package com.orbit.app.util;

import java.nio.charset.StandardCharsets;

import com.orbit.app.ingestion.website.WebsiteContentExtractor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UtilityFunctionsTest {

    @Test
    void hashingIsStableAcrossTextAndBytes() {
        String expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

        assertThat(HashUtils.sha256Hex("abc")).isEqualTo(expected);
        assertThat(HashUtils.sha256Hex("abc".getBytes(StandardCharsets.UTF_8))).isEqualTo(expected);
    }

    @Test
    void slugificationHandlesSymbolsWhitespaceAndNull() {
        assertThat(SlugUtils.slugify(" C/C++ & Embedded Systems ")).isEqualTo("ccplus-embedded-systems");
        assertThat(SlugUtils.slugify("multiple---spaces")).isEqualTo("multiple-spaces");
        assertThat(SlugUtils.slugify(null)).isEmpty();
    }

    @Test
    void jsonEscapingCoversControlCharactersAndQuotes() {
        assertThat(JsonUtils.escapeJson("a\\b\n\"c\"\t"))
                .isEqualTo("a\\\\b\\n\\\"c\\\"\\t");
    }

    @Test
    void websiteDomainNormalizationRemovesProtocolPathAndWww() {
        assertThat(WebsiteContentExtractor.normalizeDomain("https://www.example.ch/about?q=1"))
                .isEqualTo("example.ch");
        assertThat(WebsiteContentExtractor.normalizeDomain("not a url")).isEqualTo("not a url");
    }
}
