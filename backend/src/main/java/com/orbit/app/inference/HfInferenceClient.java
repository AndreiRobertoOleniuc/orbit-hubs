package com.orbit.app.inference;

import java.net.URI;
import java.util.function.LongSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Hugging Face serverless inference: one model per URL, waiting out a loading model. */
@Component
public class HfInferenceClient {

    private final HfInferenceProperties props;
    private final RetryingInferenceCall call;

    @Autowired
    public HfInferenceClient(HfInferenceProperties props, JsonMapper objectMapper) {
        this(props, objectMapper, RetryingInferenceCall.javaTransport(props.connectTimeout()),
                Thread::sleep, System::nanoTime);
    }

    HfInferenceClient(HfInferenceProperties props, JsonMapper objectMapper,
            InferenceTransport transport, InferenceSleeper sleeper, LongSupplier nanoTime) {
        this.props = props;
        this.call = new RetryingInferenceCall("Hugging Face", props.requestTimeout(), HfInferenceClient::isRetryable,
                objectMapper, transport, sleeper, nanoTime);
    }

    public JsonNode post(String model, JsonNode requestBody) {
        URI uri = URI.create(RetryingInferenceCall.stripTrailingSlash(props.baseUrl()) + "/" + model);
        return call.post(uri, props.requiredToken(), model, requestBody, props.maxWait());
    }

    private static boolean isRetryable(int status) {
        return status == 429 || status == 502 || status == 503 || status == 504;
    }
}
