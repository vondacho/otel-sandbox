package io.otelsandbox.e2e;

import static io.otelsandbox.e2e.E2E.MICROCKS;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** The external systems are mocked by Microcks straight from their contracts (contracts/). */
@ExtendWith(StackReadiness.class)
@DisplayName("External system contracts (Microcks)")
class ContractsIT {

    @Test
    void microcksMocksBothContracts() {
        given().baseUri(MICROCKS).get("/api/services")
                .then().statusCode(200)
                .body("name", hasItems("Pricing API", "Warehouse Events"))
                .body("find { it.name == 'Pricing API' }.type", equalTo("REST"))
                .body("find { it.name == 'Warehouse Events' }.type", equalTo("EVENT"));
    }

    @Test
    void pricingMockAnswersWithTheContractExamples() {
        given().baseUri(MICROCKS).urlEncodingEnabled(false).get("/rest/Pricing%20API/1.0.0/prices/SKU-003")
                .then().statusCode(200).body("amount", is(129.0f)).body("currency", equalTo("EUR"));
        given().baseUri(MICROCKS).urlEncodingEnabled(false).get("/rest/Pricing%20API/1.0.0/prices/SKU-999")
                .then().statusCode(404).body("code", equalTo("UNKNOWN_SKU"));
    }
}
