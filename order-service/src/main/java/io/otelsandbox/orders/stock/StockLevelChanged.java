package io.otelsandbox.orders.stock;

/** Payload of the Warehouse "stock level changed" event (contracts/warehouse-asyncapi.yaml). */
public record StockLevelChanged(String sku, long available, String warehouse, String occurredAt) {
}
