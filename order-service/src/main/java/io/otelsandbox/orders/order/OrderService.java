package io.otelsandbox.orders.order;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.otelsandbox.orders.pricing.PriceQuote;
import io.otelsandbox.orders.pricing.PricingClient;
import io.otelsandbox.orders.pricing.UnknownSkuException;
import io.otelsandbox.orders.stock.StockService;
import io.otelsandbox.orders.telemetry.BusinessMetrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final PricingClient pricing;
    private final StockService stock;
    private final BusinessMetrics metrics;

    OrderService(OrderRepository orders, PricingClient pricing, StockService stock, BusinessMetrics metrics) {
        this.orders = orders;
        this.pricing = pricing;
        this.stock = stock;
        this.metrics = metrics;
    }

    /**
     * Prices the order with the external Pricing API, then reserves stock known from the Warehouse
     * events. The {@code @WithSpan} business span groups the HTTP client, JDBC and business steps.
     */
    @WithSpan("place order")
    @Transactional
    public Order placeOrder(@SpanAttribute("order.sku") String sku, @SpanAttribute("order.quantity") int quantity) {
        PriceQuote price;
        try {
            price = pricing.priceOf(sku);
        } catch (UnknownSkuException e) {
            metrics.orderRejectedForUnknownSku();
            throw e;
        }

        Optional<String> rejection = stock.reserve(sku, quantity);
        Order order = rejection
                .map(reason -> Order.rejected(sku, quantity, price.amount(), price.currency(), reason))
                .orElseGet(() -> Order.accepted(sku, quantity, price.amount(), price.currency()));
        orders.save(order);

        Span.current()
                .setAttribute("order.id", order.getId().toString())
                .setAttribute("order.status", order.getStatus().name())
                .addEvent("order " + order.getStatus().name().toLowerCase());
        metrics.orderSubmitted(order);
        return order;
    }

    @Transactional(readOnly = true)
    public List<Order> latestOrders() {
        return orders.findTop50ByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Optional<Order> find(UUID id) {
        return orders.findById(id);
    }
}
