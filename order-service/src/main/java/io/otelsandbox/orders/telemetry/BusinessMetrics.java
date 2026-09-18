package io.otelsandbox.orders.telemetry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.otelsandbox.orders.order.Order;
import org.springframework.stereotype.Component;

/**
 * Business metrics, recorded through the OpenTelemetry API and exported by the agent over OTLP.
 * In Prometheus they appear as orders_submitted_total, order_amount_*, stock_events_received_total
 * and stock_level.
 */
@Component
public class BusinessMetrics {

    public static final AttributeKey<String> SKU = AttributeKey.stringKey("sku");
    public static final AttributeKey<String> OUTCOME = AttributeKey.stringKey("outcome");
    public static final AttributeKey<String> REASON = AttributeKey.stringKey("reason");
    public static final AttributeKey<String> CURRENCY = AttributeKey.stringKey("currency");
    public static final AttributeKey<String> WAREHOUSE = AttributeKey.stringKey("warehouse");

    private final LongCounter ordersSubmitted;
    private final DoubleHistogram orderAmount;
    private final LongCounter stockEventsReceived;
    private final Map<String, Long> stockLevels = new ConcurrentHashMap<>();

    public BusinessMetrics(OpenTelemetry openTelemetry) {
        Meter meter = openTelemetry.getMeter("io.otelsandbox.orders");
        this.ordersSubmitted = meter.counterBuilder("orders.submitted")
                .setDescription("Orders submitted, by outcome (accepted/rejected) and rejection reason")
                .setUnit("{order}")
                .build();
        this.orderAmount = meter.histogramBuilder("order.amount")
                .setDescription("Total amount of accepted orders")
                .setUnit("{EUR}")
                .setExplicitBucketBoundariesAdvice(List.of(10.0, 25.0, 50.0, 100.0, 250.0, 500.0, 1000.0))
                .build();
        this.stockEventsReceived = meter.counterBuilder("stock.events.received")
                .setDescription("Stock level events consumed from the Warehouse Kafka topic")
                .setUnit("{event}")
                .build();
        meter.gaugeBuilder("stock.level")
                .setDescription("Last known available stock per SKU")
                .setUnit("{item}")
                .ofLongs()
                .buildWithCallback(measurement -> stockLevels.forEach(
                        (sku, level) -> measurement.record(level, Attributes.of(SKU, sku))));
    }

    public void orderSubmitted(Order order) {
        String reason = order.getRejectionReason() == null ? "none" : order.getRejectionReason();
        ordersSubmitted.add(1, Attributes.of(
                SKU, order.getSku(),
                OUTCOME, order.getStatus().name().toLowerCase(),
                REASON, reason));
        if (order.getTotalAmount() != null) {
            orderAmount.record(order.getTotalAmount().doubleValue(),
                    Attributes.of(SKU, order.getSku(), CURRENCY, order.getCurrency()));
        }
    }

    public void orderRejectedForUnknownSku() {
        // The SKU value is deliberately not recorded: arbitrary user input would explode cardinality.
        ordersSubmitted.add(1, Attributes.of(SKU, "unknown", OUTCOME, "rejected", REASON, "unknown_sku"));
    }

    public void stockEventReceived(String sku, String warehouse) {
        stockEventsReceived.add(1, Attributes.of(SKU, sku, WAREHOUSE, warehouse));
    }

    public void stockLevel(String sku, long available) {
        stockLevels.put(sku, available);
    }
}
