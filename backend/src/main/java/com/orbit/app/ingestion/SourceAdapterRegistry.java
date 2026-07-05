package com.orbit.app.ingestion;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * Registry of all {@link SourceAdapter} beans, keyed by {@code sourceType()}.
 * Auto-discovers implementations via Spring component scanning.
 * <p>
 * Adding a new source = implement {@link SourceAdapter} + annotate as {@code @Component}.
 * The registry picks it up automatically. Zero changes to the orchestrator.
 */
@Component
public class SourceAdapterRegistry {

    private final Map<String, SourceAdapter> adapters;

    public SourceAdapterRegistry(List<SourceAdapter> adapterBeans) {
        this.adapters = adapterBeans.stream()
                .collect(Collectors.toMap(SourceAdapter::sourceType, Function.identity()));
    }

    public SourceAdapter getAdapter(String sourceType) {
        SourceAdapter adapter = adapters.get(sourceType);
        if (adapter == null) {
            throw new IllegalArgumentException("No adapter registered for source_type: " + sourceType);
        }
        return adapter;
    }

    public Set<String> registeredTypes() {
        return adapters.keySet();
    }
}