package io.otelsandbox.orders.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;
    private String sku;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private String currency;
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    private String rejectionReason;
    private Instant createdAt;

    protected Order() {
    }

    private Order(String sku, int quantity, BigDecimal unitPrice, String currency, OrderStatus status,
            String rejectionReason) {
        this.id = UUID.randomUUID();
        this.sku = sku;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.currency = currency;
        this.status = status;
        this.rejectionReason = rejectionReason;
        this.totalAmount = status == OrderStatus.ACCEPTED ? unitPrice.multiply(BigDecimal.valueOf(quantity)) : null;
        this.createdAt = Instant.now();
    }

    public static Order accepted(String sku, int quantity, BigDecimal unitPrice, String currency) {
        return new Order(sku, quantity, unitPrice, currency, OrderStatus.ACCEPTED, null);
    }

    public static Order rejected(String sku, int quantity, BigDecimal unitPrice, String currency, String reason) {
        return new Order(sku, quantity, unitPrice, currency, OrderStatus.REJECTED, reason);
    }

    public UUID getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
