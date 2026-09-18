package io.otelsandbox.e2e;

import static io.otelsandbox.e2e.E2E.ORDER_SERVICE;
import static io.otelsandbox.e2e.E2E.eventually;
import static io.otelsandbox.e2e.E2E.shop;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;

import java.time.Duration;

import io.restassured.RestAssured;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Waits (once per run) until the stack is usable: order-service ready, frontend proxying, and the
 * first Warehouse events consumed from Kafka.
 */
class StackReadiness implements BeforeAllCallback {

    private static volatile boolean ready;

    @Override
    public void beforeAll(ExtensionContext context) {
        if (ready) {
            return;
        }
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        eventually(Duration.ofMinutes(3)).untilAsserted(() -> {
            given().baseUri(ORDER_SERVICE).get("/actuator/health/readiness")
                    .then().statusCode(200).body("status", equalTo("UP"));
            shop().get("/api/stock")
                    .then().statusCode(200).body("sku", hasItems("SKU-001", "SKU-002", "SKU-003"));
        });
        ready = true;
    }
}
