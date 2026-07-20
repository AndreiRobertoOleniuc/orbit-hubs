package com.orbit.app.ingestion;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SourceAdapterRegistryTest {

    @Test
    void resolvesRegisteredAdapterAndExposesItsType() {
        SourceAdapter adapter = mock(SourceAdapter.class);
        when(adapter.sourceType()).thenReturn("manual_text");
        SourceAdapterRegistry registry = new SourceAdapterRegistry(List.of(adapter));

        assertThat(registry.getAdapter("manual_text")).isSameAs(adapter);
        assertThat(registry.registeredTypes()).containsExactly("manual_text");
    }

    @Test
    void unknownTypeFailsClearly() {
        SourceAdapterRegistry registry = new SourceAdapterRegistry(List.of());

        assertThatThrownBy(() -> registry.getAdapter("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void duplicateTypesAreRejectedAtStartup() {
        SourceAdapter first = mock(SourceAdapter.class);
        SourceAdapter second = mock(SourceAdapter.class);
        when(first.sourceType()).thenReturn("duplicate");
        when(second.sourceType()).thenReturn("duplicate");

        assertThatThrownBy(() -> new SourceAdapterRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class);
    }
}
