package com.orbit.app.inference;

import java.util.List;
import java.util.Map;
import java.util.Optional;

record InferenceResponse(
        int statusCode,
        Map<String, List<String>> headers,
        String body
) {
    Optional<String> firstHeader(String name) {
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .flatMap(entry -> entry.getValue().stream())
                .findFirst();
    }
}
