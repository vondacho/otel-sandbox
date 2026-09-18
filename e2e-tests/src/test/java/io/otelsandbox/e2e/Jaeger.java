package io.otelsandbox.e2e;

import static io.otelsandbox.e2e.E2E.JAEGER;
import static io.restassured.RestAssured.given;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.restassured.response.Response;

/** Minimal client of the Jaeger query API v3 (OTLP JSON). */
final class Jaeger {

    static final int INTERNAL = 1;
    static final int SERVER = 2;
    static final int CLIENT = 3;
    static final int CONSUMER = 5;

    record Span(String service, String traceId, String spanId, String parentSpanId, String name, int kind,
            Map<String, Object> attributes) {

        Object attribute(String key) {
            return attributes.get(key);
        }
    }

    private Jaeger() {
    }

    static List<Span> trace(String traceId) {
        Response response = given().baseUri(JAEGER).get("/api/v3/traces/{traceId}", traceId);
        response.then().statusCode(200);
        return spans(response);
    }

    static List<Span> search(String service, String operation, Instant since) {
        Response response = given().baseUri(JAEGER)
                .queryParam("query.service_name", service)
                .queryParam("query.operation_name", operation)
                .queryParam("query.start_time_min", since.toString())
                .queryParam("query.start_time_max", Instant.now().toString())
                .queryParam("query.search_depth", 20)
                .get("/api/v3/traces");
        response.then().statusCode(200);
        return spans(response);
    }

    @SuppressWarnings("unchecked")
    private static List<Span> spans(Response response) {
        List<Span> spans = new ArrayList<>();
        List<Map<String, Object>> resourceSpans = response.jsonPath().getList("result.resourceSpans");
        for (Map<String, Object> resourceSpan : resourceSpans) {
            Map<String, Object> resource = (Map<String, Object>) resourceSpan.get("resource");
            String service = String.valueOf(attributes((List<Map<String, Object>>) resource.get("attributes"))
                    .get("service.name"));
            for (Map<String, Object> scopeSpan : (List<Map<String, Object>>) resourceSpan.get("scopeSpans")) {
                for (Map<String, Object> span : (List<Map<String, Object>>) scopeSpan.get("spans")) {
                    spans.add(new Span(service,
                            (String) span.get("traceId"),
                            (String) span.get("spanId"),
                            (String) span.get("parentSpanId"),
                            (String) span.get("name"),
                            ((Number) span.get("kind")).intValue(),
                            attributes((List<Map<String, Object>>) span.get("attributes"))));
                }
            }
        }
        return spans;
    }

    /** OTLP JSON attributes: [{key, value: {stringValue|intValue|boolValue|doubleValue: ...}}]. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> attributes(List<Map<String, Object>> attributes) {
        Map<String, Object> map = new HashMap<>();
        Optional.ofNullable(attributes).orElse(List.of()).forEach(attribute -> {
            Map<String, Object> value = (Map<String, Object>) attribute.get("value");
            map.put((String) attribute.get("key"), value.values().stream().findFirst().orElse(null));
        });
        return map;
    }
}
