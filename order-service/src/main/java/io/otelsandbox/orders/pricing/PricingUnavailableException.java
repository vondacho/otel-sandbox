package io.otelsandbox.orders.pricing;

public class PricingUnavailableException extends RuntimeException {

    public PricingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
