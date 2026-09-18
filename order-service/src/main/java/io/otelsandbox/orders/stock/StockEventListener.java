package io.otelsandbox.orders.stock;

import io.opentelemetry.api.trace.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumes the Warehouse stock events. The agent's Kafka instrumentation creates a CONSUMER span
 * per record, linked to the producer's context when a traceparent header is present.
 */
@Component
class StockEventListener {

    private static final Logger log = LoggerFactory.getLogger(StockEventListener.class);

    private final StockService stockService;
    private final JsonMapper jsonMapper;

    StockEventListener(StockService stockService, JsonMapper jsonMapper) {
        this.stockService = stockService;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = "${sandbox.warehouse.stock-topic}")
    void onStockLevelChanged(String payload) {
        StockLevelChanged event;
        try {
            event = jsonMapper.readValue(payload, StockLevelChanged.class);
        } catch (JacksonException e) {
            log.warn("Discarding malformed stock event: {}", payload, e);
            Span.current().recordException(e);
            return;
        }
        Span.current()
                .setAttribute("stock.sku", event.sku())
                .setAttribute("stock.available", event.available())
                .setAttribute("stock.warehouse", event.warehouse());
        stockService.apply(event);
    }
}
