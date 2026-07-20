package com.orbit.app.inference;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

@FunctionalInterface
interface HfInferenceTransport {
    HfInferenceResponse post(URI uri, String token, String body, Duration timeout)
            throws IOException, InterruptedException;
}
