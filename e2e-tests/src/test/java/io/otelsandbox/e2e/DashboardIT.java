package io.otelsandbox.e2e;

import static io.otelsandbox.e2e.E2E.GRAFANA;
import static io.otelsandbox.e2e.E2E.TELEMETRY_TIMEOUT;
import static io.otelsandbox.e2e.E2E.eventually;
import static io.otelsandbox.e2e.E2E.placeOrder;
import static io.otelsandbox.e2e.E2E.prometheusQuery;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.empty;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The provisioned Grafana dashboard: present, datasources healthy, and every panel has data. */
@ExtendWith(StackReadiness.class)
@DisplayName("Grafana dashboard")
class DashboardIT {

    private static final String DASHBOARD_UID = "otel-sandbox";

    @BeforeAll
    static void generateSomeBusinessActivity() {
        placeOrder("SKU-001", 1).then().statusCode(201);
        placeOrder("SKU-002", 1).then().statusCode(201);
        placeOrder("SKU-999", 1).then().statusCode(422);
    }

    @Test
    void dashboardIsProvisioned() {
        given().baseUri(GRAFANA).get("/api/dashboards/uid/{uid}", DASHBOARD_UID)
                .then().statusCode(200)
                .body("dashboard.title", equalTo("OTEL sandbox orders"))
                .body("meta.folderTitle", equalTo("OTEL sandbox"))
                .body("dashboard.panels", not(empty()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"prometheus", "jaeger"})
    void datasourceIsHealthy(String uid) {
        given().baseUri(GRAFANA).get("/api/datasources/uid/{uid}/health", uid)
                .then().statusCode(200).body("status", equalTo("OK"));
    }

    @Test
    void traceTableFindsOrderTracesThroughGrafana() {
        String query = """
                {"from": "now-1h", "to": "now", "queries": [{"refId": "A",
                  "datasource": {"type": "jaeger", "uid": "jaeger"},
                  "queryType": "search", "service": "order-service", "operation": "POST /api/orders", "limit": 5}]}
                """;
        eventually(TELEMETRY_TIMEOUT).untilAsserted(() -> {
            List<?> traceIds = given().baseUri(GRAFANA).contentType(ContentType.JSON).body(query)
                    .post("/api/ds/query")
                    .then().statusCode(200)
                    .extract().path("results.A.frames[0].data.values[0]");
            assertThat(traceIds).isNotEmpty();
        });
    }

    /** One dynamic test per PromQL query of the dashboard, as deployed in Grafana. */
    @TestFactory
    Stream<DynamicTest> everyPrometheusPanelReturnsData() {
        JsonPath dashboard = given().baseUri(GRAFANA).get("/api/dashboards/uid/{uid}", DASHBOARD_UID)
                .then().statusCode(200).extract().jsonPath();
        List<Map<String, Object>> panels = dashboard.getList("dashboard.panels");

        return panels.stream()
                .filter(panel -> panel.get("targets") != null)
                .flatMap(panel -> targets(panel).stream()
                        .filter(target -> target.get("expr") != null)
                        .map(target -> DynamicTest.dynamicTest(
                                panel.get("title") + " [" + target.get("refId") + "]",
                                () -> assertReturnsData((String) target.get("expr")))));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> targets(Map<String, Object> panel) {
        return (List<Map<String, Object>>) panel.get("targets");
    }

    private static void assertReturnsData(String expr) {
        String promql = expr
                .replace("$__rate_interval", "1m")
                .replace("$__interval", "15s")
                .replace("$__range", "1h");
        eventually(TELEMETRY_TIMEOUT).untilAsserted(() -> prometheusQuery(promql)
                .then().statusCode(200)
                .body("status", equalTo("success"))
                .body("data.result", not(empty())));
    }
}
