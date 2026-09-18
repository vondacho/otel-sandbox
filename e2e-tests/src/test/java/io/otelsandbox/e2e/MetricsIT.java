package io.otelsandbox.e2e;

import static io.otelsandbox.e2e.E2E.PROMETHEUS;
import static io.otelsandbox.e2e.E2E.TELEMETRY_TIMEOUT;
import static io.otelsandbox.e2e.E2E.eventually;
import static io.otelsandbox.e2e.E2E.placeOrder;
import static io.otelsandbox.e2e.E2E.prometheus;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Metrics pushed over OTLP to the collector, then scraped by Prometheus. */
@ExtendWith(StackReadiness.class)
@DisplayName("Metrics (Prometheus)")
class MetricsIT {

    @Test
    void acceptedOrdersAreCountedAndTheirAmountRecorded() {
        String accepted = "sum(orders_submitted_total{outcome=\"accepted\", sku=\"SKU-001\"})";
        String amount = "sum(order_amount_sum{sku=\"SKU-001\"})";
        double acceptedBefore = prometheus(accepted).orElse(0.0);
        double amountBefore = prometheus(amount).orElse(0.0);

        placeOrder("SKU-001", 2).then().statusCode(201);

        eventually(TELEMETRY_TIMEOUT).untilAsserted(() -> {
            assertThat(prometheus(accepted)).hasValueSatisfying(v -> assertThat(v).isGreaterThan(acceptedBefore));
            assertThat(prometheus(amount)).hasValueSatisfying(v -> assertThat(v).isGreaterThan(amountBefore));
        });
    }

    @Test
    void rejectionsAreCountedByReason() {
        placeOrder("SKU-002", 1).then().statusCode(201);
        placeOrder("SKU-999", 1).then().statusCode(422);

        eventually(TELEMETRY_TIMEOUT).untilAsserted(() -> {
            assertThat(prometheus("sum(orders_submitted_total{outcome=\"rejected\", reason=\"out_of_stock\"})"))
                    .hasValueSatisfying(v -> assertThat(v).isPositive());
            assertThat(prometheus("sum(orders_submitted_total{outcome=\"rejected\", reason=\"unknown_sku\"})"))
                    .hasValueSatisfying(v -> assertThat(v).isPositive());
        });
    }

    @Test
    void stockLevelsAndConsumedEventsArePublished() {
        eventually(TELEMETRY_TIMEOUT).untilAsserted(() -> {
            assertThat(prometheus("max(stock_level{sku=\"SKU-002\"})")).hasValue(0.0);
            assertThat(prometheus("max(stock_level{sku=\"SKU-001\"})"))
                    .hasValueSatisfying(v -> assertThat(v).isPositive());
            assertThat(prometheus("sum(rate(stock_events_received_total[1m]))"))
                    .hasValueSatisfying(v -> assertThat(v).isPositive());
        });
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            // HTTP server & client (agent instrumentation)
            "http_server_request_duration_seconds_count{job=\"order-service\", http_route=\"/api/orders\"}",
            "http_client_request_duration_seconds_count{job=\"order-service\", server_address=\"microcks\"}",
            // JVM runtime
            "jvm_memory_used_bytes{job=\"order-service\", jvm_memory_type=\"heap\"}",
            "jvm_cpu_recent_utilization_ratio{job=\"order-service\"}",
            "jvm_thread_count{job=\"order-service\"}",
            // connection pool and Kafka client
            "db_client_connections_usage{job=\"order-service\"}",
            "kafka_consumer_records_consumed_total{job=\"order-service\"}",
            // RED metrics derived from spans by the collector, including the nginx frontend
            "traces_span_metrics_calls_total{job=\"frontend\"}",
            "traces_span_metrics_calls_total{job=\"order-service\", span_kind=\"SPAN_KIND_CONSUMER\"}",
            // infrastructure scraped directly
            "redpanda_kafka_request_bytes_total"
    })
    void systemMetricsAreCollected(String series) {
        eventually(TELEMETRY_TIMEOUT).untilAsserted(() -> assertThat(prometheus(series)).isPresent());
    }

    @Test
    void latencyHistogramsCarryTraceExemplars() {
        Instant start = Instant.now().minusSeconds(600);
        placeOrder("SKU-001", 1).then().statusCode(201);

        eventually(TELEMETRY_TIMEOUT).untilAsserted(() -> {
            List<Map<String, String>> exemplarLabels = given().baseUri(PROMETHEUS)
                    .queryParam("query", "http_server_request_duration_seconds_bucket{job=\"order-service\"}")
                    .queryParam("start", start.getEpochSecond())
                    .queryParam("end", Instant.now().getEpochSecond())
                    .get("/api/v1/query_exemplars")
                    .then().statusCode(200)
                    .extract().jsonPath().getList("data.exemplars.flatten().labels");
            assertThat(exemplarLabels).isNotEmpty().allSatisfy(labels -> assertThat(labels).containsKey("trace_id"));
        });
    }
}
