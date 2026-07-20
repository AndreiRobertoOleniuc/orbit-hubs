package com.orbit.app.search;

import java.util.List;

/** Provider transport for scoring query/document pairs in input order. */
interface CrossEncoderClient {

    List<Double> score(String query, List<String> documents);
}
