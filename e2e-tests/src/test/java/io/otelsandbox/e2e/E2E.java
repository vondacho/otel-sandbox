package io.otelsandbox.e2e;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.awaitility.core.ConditionFactory;

/** Endpoints of the running stack (overridable with -De2e.*.url) and shared helpers. */
final class E2E {

    static final String FRONTEND = url("frontend", "http://localhost:8000");
    static final String ORDER_SERVICE = url("order-service", "http://localhost:8080");
    static final String MICROCKS = url("microcks", "http://localhost:8585");
    static final String JAEGER = url("jaeger", "http://localhost:16686");
    static final String PROMETHEUS = url("prometheus", "http://localhost:9090");
    static final String GRAFANA = url("grafana", "http://localhost:3000");

    /** Telemetry is exported every 10 s and scraped every 10 s: allow a few cycles. */
    static final Duration TELEMETRY_TIMEOUT = Duration.ofSeconds(90);

    private E2E() {
    }

    private static String url(String name, String defaultValue) {
        return System.getProperty("e2e." + name + ".url", defaultValue);
    }

    /** The shop API, called the way the browser does: through the nginx frontend. */
    static RequestSpecification shop() {
        return given().baseUri(FRONTEND).contentType(ContentType.JSON).accept(ContentType.JSON);
    }

    static Response placeOrder(String sku, int quantity) {
        return placeOrder(sku, quantity, null);
    }

    static Response placeOrder(String sku, int quantity, String traceparent) {
        RequestSpecification request = shop().body("{\"sku\": \"%s\", \"quantity\": %d}".formatted(sku, quantity));
        if (traceparent != null) {
            request.header("traceparent", traceparent);
        }
        return request.post("/api/orders");
    }

    static ConditionFactory eventually() {
        return eventually(Duration.ofSeconds(60));
    }

    static ConditionFactory eventually(Duration timeout) {
        return await().atMost(timeout).pollInterval(Duration.ofSeconds(2)).ignoreExceptions();
    }

    // ------------------------------------------------------------------ W3C trace context

    static String newTraceId() {
        return randomHex(16);
    }

    /** A sampled W3C traceparent, so that the test knows the trace id before sending the request. */
    static String traceparent(String traceId) {
        return "00-" + traceId + "-" + randomHex(8) + "-01";
    }

    private static String randomHex(int bytes) {
        byte[] buffer = new byte[bytes];
        ThreadLocalRandom.current().nextBytes(buffer);
        return HexFormat.of().formatHex(buffer);
    }

    // ------------------------------------------------------------------ Prometheus

    /** Instant query; returns the sum of all resulting series, or empty when there is none. */
    static Optional<Double> prometheus(String promql) {
        List<Map<String, Object>> result = prometheusQuery(promql).jsonPath().getList("data.result");
        return result.stream()
                .map(series -> Double.parseDouble(String.valueOf(((List<?>) series.get("value")).get(1))))
                .reduce(Double::sum);
    }

    static Response prometheusQuery(String promql) {
        return given().baseUri(PROMETHEUS).queryParam("query", promql).get("/api/v1/query");
    }
}
