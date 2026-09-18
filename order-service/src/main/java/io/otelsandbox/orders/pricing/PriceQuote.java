package io.otelsandbox.orders.pricing;

import java.math.BigDecimal;

public record PriceQuote(String sku, BigDecimal amount, String currency) {
}
