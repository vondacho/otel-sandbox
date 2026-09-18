package io.otelsandbox.e2e;

import static io.otelsandbox.e2e.E2E.TELEMETRY_TIMEOUT;
import static io.otelsandbox.e2e.E2E.eventually;
import static io.otelsandbox.e2e.E2E.newTraceId;
import static io.otelsandbox.e2e.E2E.placeOrder;
import static io.otelsandbox.e2e.E2E.traceparent;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;

import io.otelsandbox.e2e.Jaeger.Span;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Distributed traces in Jaeger. The test starts the trace itself (W3C traceparent header), so it
 * knows the trace id and can fetch exactly that trace.
 */
@ExtendWith(StackReadiness.class)
@DisplayName("Distributed tracing (Jaeger)")
class TracingIT {

    @Test
    void anOrderIsTracedFromTheFrontendDownToThePricingApiAndTheDatabase() {
        String traceId = newTraceId();
        placeOrder("SKU-001", 1, traceparent(traceId)).then().statusCode(201);

        eventually(TELEMETRY_TIMEOUT).untilAsserted(() -> {
            List<Span> spans = Jaeger.trace(traceId);

            Span frontend = single(spans, s -> s.service().equals("frontend") && s.kind() == Jaeger.SERVER);
            Span server = single(spans, s -> s.service().equals("order-service") && s.kind() == Jaeger.SERVER);
            Span business = single(spans, s -> s.name().equals("place order"));

            // nginx joined the caller's trace and propagated it to the order-service
            assertThat(server.parentSpanId()).isEqualTo(frontend.spanId());
            assertThat(server.name()).isEqualTo("POST /api/orders");
            assertThat(server.attribute("http.response.status_code")).isEqualTo("201");

            // business span with its custom attributes (@WithSpan / @SpanAttribute / Span.current())
            assertThat(business.parentSpanId()).isEqualTo(server.spanId());
            assertThat(business.attributes())
                    .containsEntry("order.sku", "SKU-001")
                    .containsEntry("order.status", "ACCEPTED")
                    .containsKey("order.id");

            // outgoing call to the Pricing API mock and SQL statements to PostgreSQL
            assertThat(spans).anySatisfy(span -> {
                assertThat(span.kind()).isEqualTo(Jaeger.CLIENT);
                assertThat(span.attributes()).containsEntry("server.address", "microcks");
                assertThat(String.valueOf(span.attribute("url.full"))).contains("/prices/SKU-001");
            });
            assertThat(spans)
                    .filteredOn(span -> span.kind() == Jaeger.CLIENT && "postgresql".equals(span.attribute("db.system")))
                    .extracting(Span::name)
                    .contains("INSERT orders.orders", "UPDATE orders.stock_levels");
        });
    }

    @Test
    void aRejectedOrderIsVisibleInItsTrace() {
        String traceId = newTraceId();
        placeOrder("SKU-002", 1, traceparent(traceId)).then().statusCode(201);

        eventually(TELEMETRY_TIMEOUT).untilAsserted(() -> assertThat(Jaeger.trace(traceId))
                .filteredOn(span -> span.name().equals("place order"))
                .singleElement()
                .satisfies(span -> assertThat(span.attributes()).containsEntry("order.status", "REJECTED")));
    }

    @Test
    void warehouseEventsAreTracedAsKafkaConsumerSpans() {
        Instant since = Instant.now().minus(Duration.ofMinutes(2));

        eventually(TELEMETRY_TIMEOUT).untilAsserted(() -> assertThat(
                Jaeger.search("order-service", "WarehouseEvents-1.0.0-stock-levels process", since))
                .filteredOn(span -> span.kind() == Jaeger.CONSUMER)
                .isNotEmpty()
                .allSatisfy(span -> assertThat(span.attributes())
                        .containsEntry("messaging.system", "kafka")
                        .containsEntry("messaging.destination.name", "WarehouseEvents-1.0.0-stock-levels")
                        .containsKey("stock.sku")));
    }

    private static Span single(List<Span> spans, Predicate<Span> predicate) {
        List<Span> matching = spans.stream().filter(predicate).toList();
        assertThat(matching).hasSize(1);
        return matching.getFirst();
    }
}
