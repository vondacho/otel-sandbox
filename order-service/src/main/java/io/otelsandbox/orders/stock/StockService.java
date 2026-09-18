package io.otelsandbox.orders.stock;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

import io.otelsandbox.orders.telemetry.BusinessMetrics;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockService {

    public static final String UNKNOWN_STOCK = "stock_unknown";
    public static final String OUT_OF_STOCK = "out_of_stock";
    public static final String INSUFFICIENT_STOCK = "insufficient_stock";

    private final StockLevelRepository repository;
    private final BusinessMetrics metrics;

    StockService(StockLevelRepository repository, BusinessMetrics metrics) {
        this.repository = repository;
        this.metrics = metrics;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void publishInitialStockLevels() {
        repository.findAll().forEach(level -> metrics.stockLevel(level.getSku(), level.getAvailable()));
    }

    @Transactional
    public void apply(StockLevelChanged event) {
        StockLevel level = repository.findForUpdate(event.sku()).orElseGet(() -> new StockLevel(event.sku()));
        level.update(event.available(), event.warehouse(), parseTimestamp(event.occurredAt()));
        repository.save(level);
        metrics.stockEventReceived(event.sku(), event.warehouse());
        metrics.stockLevel(event.sku(), event.available());
    }

    /**
     * Takes {@code quantity} items out of the stock of {@code sku}.
     *
     * @return the rejection reason, or empty when the stock was reserved
     */
    @Transactional
    public Optional<String> reserve(String sku, int quantity) {
        Optional<StockLevel> found = repository.findForUpdate(sku);
        if (found.isEmpty()) {
            return Optional.of(UNKNOWN_STOCK);
        }
        StockLevel level = found.get();
        Optional<String> rejection = decide(level.getAvailable(), quantity);
        if (rejection.isEmpty()) {
            level.take(quantity);
            metrics.stockLevel(sku, level.getAvailable());
        }
        return rejection;
    }

    static Optional<String> decide(long available, int quantity) {
        if (available <= 0) {
            return Optional.of(OUT_OF_STOCK);
        }
        if (available < quantity) {
            return Optional.of(INSUFFICIENT_STOCK);
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public List<StockLevel> all() {
        return repository.findAllByOrderBySkuAsc();
    }

    private static Instant parseTimestamp(String value) {
        try {
            return value == null ? Instant.now() : Instant.parse(value);
        } catch (DateTimeParseException e) {
            return Instant.now();
        }
    }
}
