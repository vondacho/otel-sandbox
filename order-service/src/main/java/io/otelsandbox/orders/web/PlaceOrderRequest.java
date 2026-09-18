package io.otelsandbox.orders.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PlaceOrderRequest(
        @NotBlank @Pattern(regexp = "[A-Z0-9-]{1,32}") String sku,
        @Min(1) @Max(100) int quantity) {
}
