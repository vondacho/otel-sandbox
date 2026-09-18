package io.otelsandbox.orders.pricing;

public class UnknownSkuException extends RuntimeException {

    public UnknownSkuException(String sku) {
        super("Unknown SKU: " + sku);
    }
}
