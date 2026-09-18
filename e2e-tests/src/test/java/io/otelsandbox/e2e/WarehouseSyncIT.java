package io.otelsandbox.e2e;

import static io.otelsandbox.e2e.E2E.eventually;
import static io.otelsandbox.e2e.E2E.shop;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Stock levels come from the Warehouse AsyncAPI mock (Microcks -> Redpanda -> order-service). */
@ExtendWith(StackReadiness.class)
@DisplayName("Warehouse stock events (Kafka)")
class WarehouseSyncIT {

    @Test
    void stockLevelsReflectTheWarehouseEvents() {
        shop().get("/api/stock")
                .then().statusCode(200)
                .body("sku", hasItems("SKU-001", "SKU-002", "SKU-003"))
                .body("find { it.sku == 'SKU-001' }.warehouse", equalTo("WH-PARIS"))
                .body("find { it.sku == 'SKU-002' }.available", equalTo(0))
                .body("find { it.sku == 'SKU-003' }.warehouse", equalTo("WH-LYON"));
    }

    @Test
    void stockLevelsAreRefreshedContinuously() {
        Instant first = sku002UpdatedAt();

        eventually().untilAsserted(() -> assertThat(sku002UpdatedAt()).isAfter(first));
    }

    private static Instant sku002UpdatedAt() {
        return Instant.parse(shop().get("/api/stock").then().statusCode(200)
                .extract().<String>path("find { it.sku == 'SKU-002' }.updatedAt"));
    }
}
