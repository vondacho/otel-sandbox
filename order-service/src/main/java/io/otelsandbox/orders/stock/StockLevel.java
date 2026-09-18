package io.otelsandbox.orders.stock;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "stock_levels")
public class StockLevel {

    @Id
    private String sku;
    private long available;
    private String warehouse;
    private Instant updatedAt;

    protected StockLevel() {
    }

    public StockLevel(String sku) {
        this.sku = sku;
    }

    void update(long available, String warehouse, Instant updatedAt) {
        this.available = available;
        this.warehouse = warehouse;
        this.updatedAt = updatedAt;
    }

    void take(int quantity) {
        this.available -= quantity;
    }

    public String getSku() {
        return sku;
    }

    public long getAvailable() {
        return available;
    }

    public String getWarehouse() {
        return warehouse;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
