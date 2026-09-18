package io.otelsandbox.e2e;

import static io.otelsandbox.e2e.E2E.FRONTEND;
import static io.otelsandbox.e2e.E2E.placeOrder;
import static io.otelsandbox.e2e.E2E.shop;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.oneOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Business behaviour, exercised through the frontend's /api proxy. */
@ExtendWith(StackReadiness.class)
@DisplayName("Shop API")
class ShopApiIT {

    @Test
    void frontendServesTheShopPage() {
        given().baseUri(FRONTEND).get("/")
                .then().statusCode(200).body(containsString("OTEL Sandbox Shop"));
    }

    @Test
    void acceptsAnOrderPricedByThePricingApi() {
        String location = placeOrder("SKU-001", 3)
                .then().statusCode(201)
                .body("status", equalTo("ACCEPTED"))
                .body("unitPrice", is(19.99f))
                .body("totalAmount", is(59.97f))
                .body("currency", equalTo("EUR"))
                .header("Location", notNullValue())
                .extract().header("Location");

        shop().get(location)
                .then().statusCode(200).body("sku", equalTo("SKU-001")).body("quantity", equalTo(3));
    }

    @Test
    void rejectsAnOrderForASoldOutProduct() {
        placeOrder("SKU-002", 1)
                .then().statusCode(201)
                .body("status", equalTo("REJECTED"))
                .body("rejectionReason", equalTo("out_of_stock"))
                .body("unitPrice", is(5.49f))
                .body("totalAmount", nullValue());
    }

    @Test
    void rejectsAnOrderExceedingTheAvailableStock() {
        // SKU-003 is published with 3 items; concurrent traffic (loadgen) may have taken them all
        placeOrder("SKU-003", 50)
                .then().statusCode(201)
                .body("status", equalTo("REJECTED"))
                .body("rejectionReason", oneOf("insufficient_stock", "out_of_stock"));
    }

    @Test
    void rejectsAnUnknownProductWithAProblemDetail() {
        placeOrder("SKU-999", 1)
                .then().statusCode(422)
                .body("detail", equalTo("Unknown SKU: SKU-999"));
    }

    @Test
    void rejectsAnInvalidRequest() {
        placeOrder("SKU-001", 0).then().statusCode(400);
    }

    @Test
    void listsTheLatestOrders() {
        String id = placeOrder("SKU-001", 1).then().statusCode(201).extract().path("id");

        shop().get("/api/orders").then().statusCode(200).body("id", hasItem(id));
    }
}
