# OTEL-sandbox
Some examples of setting up and using an OTEL-compliant observability stack

A small, fully containerized system instrumented with OpenTelemetry: a Spring Boot REST service with its
PostgreSQL datastore, a minimal web frontend, two external systems mocked from their contracts by
Microcks (one REST/OpenAPI, one Kafka/AsyncAPI on Redpanda), and an observability stack
(OTel Collector, Prometheus, Jaeger, Grafana). An end-to-end test suite verifies the whole thing.

## Architecture

```
 browser ──► frontend (nginx + ngx_otel_module) ──/api──► order-service (Spring Boot 4, Java 25)
                                                             │  │  │
                     Pricing API (OpenAPI) ◄──── HTTP ───────┘  │  └──── JDBC ───► PostgreSQL
                     mocked by Microcks                         │
                                                                ▼ Kafka consumer
 Warehouse Events (AsyncAPI) ── Microcks async minion ──► Redpanda topic WarehouseEvents-1.0.0-stock-levels

 frontend, order-service ──OTLP──► OTel Collector ──► Jaeger (traces, Badger storage)
                                        │  └─ span_metrics connector (RED metrics from spans)
                                        └──► :8889 ◄── scraped by Prometheus ◄── Grafana (dashboard)
                                                       (also scrapes Redpanda and the collector itself)
```

**Domain.** The shop sells three products. Placing an order (`POST /api/orders {sku, quantity}`):

1. prices it with the external **Pricing API**. An unknown SKU (`SKU-999`) returns 404, so the order service answers `422`.
2. checks the stock known from the **Warehouse** events. Microcks publishes one event per SKU every 3 s:
   `SKU-001`: 500 items, `SKU-002`: 0 (sold out), `SKU-003`: 3.
3. stores the order as `ACCEPTED`, or as `REJECTED` with a reason: `out_of_stock`, `insufficient_stock` or `stock_unknown`.

| Module / folder             | Content                                                                         |
|-----------------------------|---------------------------------------------------------------------------------|
| `order-service/`            | Spring Boot service (Maven module), Flyway schema, Dockerfile with the OTel Java agent |
| `e2e-tests/`                | Black-box e2e suite (Maven module, JUnit 5 + RestAssured + Awaitility)          |
| `frontend/`                 | Static HTML/JS page served by nginx, which proxies `/api` and traces requests   |
| `contracts/`                | `pricing-openapi.yaml`, `warehouse-asyncapi.yaml`: the Microcks mock sources    |
| `observability/`            | Collector, Prometheus, Jaeger configs; Grafana datasources and dashboard         |
| `loadgen/`                  | Optional traffic generator (compose profile `load`)                             |

## Run it

Requirements: Docker with Compose v2 and about 6 GB of RAM for the containers. JDK 25 is only needed to run the tests
(the images are built with Maven inside Docker).

```sh
docker compose up -d --build --wait         # whole stack (~1 min on first build)
docker compose --profile load up -d loadgen # optional: steady traffic for the dashboard
docker compose --profile tools up -d        # optional: Redpanda Console on :8088
docker compose down -v                      # stop and wipe the data volumes
```

| URL                          | What                                                   |
|------------------------------|--------------------------------------------------------|
| http://localhost:8000        | Shop frontend                                          |
| http://localhost:8080/api/orders | order-service API (direct)                         |
| http://localhost:3000        | Grafana: home dashboard **OTEL sandbox orders** (anonymous admin) |
| http://localhost:16686       | Jaeger UI (Search, Monitor tab fed by span metrics)    |
| http://localhost:9090        | Prometheus                                             |
| http://localhost:8585        | Microcks (mocks and their contracts)                   |

`localhost:19092` (Kafka), `localhost:15432` (PostgreSQL `orders`/`orders`) and `localhost:4317/4318` (OTLP)
are exposed too, so the service can also run from an IDE (`./mvnw -pl order-service spring-boot:run`).
Without `-javaagent` it runs with no-op telemetry.

## Telemetry

**Traces.** The order-service runs with the OpenTelemetry Java agent (`JAVA_TOOL_OPTIONS=-javaagent:...`).
The agent traces Spring MVC, the JDK HTTP client (calls to Pricing), JDBC/Hibernate and the Kafka consumer.
The code adds business spans and attributes through the OTel API: `@WithSpan("place order")`,
`@SpanAttribute`, and `Span.current().setAttribute("order.status", ...)`. nginx creates the frontend
server span and propagates the W3C `traceparent` header, so one trace runs from the frontend through the service to Pricing and PostgreSQL.

**Metrics** are pushed over OTLP every 10 s, then exposed by the collector and scraped by Prometheus:

| Kind      | Prometheus series                                                                                   |
|-----------|-----------------------------------------------------------------------------------------------------|
| Business  | `orders_submitted_total{outcome,reason,sku}`, `order_amount_*{sku,currency}` (histogram), `stock_level{sku}`, `stock_events_received_total{sku,warehouse}` (see `BusinessMetrics.java`) |
| System    | `http_server_request_duration_seconds_*`, `http_client_request_duration_seconds_*`, `jvm_*`, `db_client_connections_*`, `kafka_consumer_*` (Java agent) |
| From spans| `traces_span_metrics_calls_total`, `traces_span_metrics_duration_milliseconds_*` for every service, including the nginx frontend |
| Infra     | `redpanda_*`, `otelcol_*`                                                                           |

Latency histograms carry **exemplars** (trace ids). In Grafana, the dots on the latency panels link to the matching trace in Jaeger.

**Dashboard** (`observability/grafana/dashboards/otel-sandbox.json`) has these sections:
- **Business KPIs:** accepted and rejected orders, rejection ratio, revenue, average basket, stock events, orders by outcome and reason, order amount p50/p95, stock per SKU.
- **Service health (RED):** request rate, latency and errors per route, plus span-derived RED for every service.
- **Dependencies:** Pricing API latency and status codes, DB pool, Kafka consumption and lag.
- **JVM:** heap, CPU, GC, threads.
- **Traces:** table of recent traces from the Jaeger datasource.

It is provisioned and editable; export it from the UI to update the file.

## End-to-end tests

The e2e suite is black-box: it runs against the compose stack that is already up.

```sh
docker compose up -d --build --wait
./mvnw verify -Pe2e          # unit tests + e2e suite (~2 min)
./mvnw verify                # unit tests only (e2e skipped)
```

URLs can be overridden with `-De2e.frontend.url=...`, and similarly for `order-service`, `microcks`, `jaeger`,
`prometheus` and `grafana`.

| Test class        | Verifies                                                                                       |
|-------------------|------------------------------------------------------------------------------------------------|
| `ShopApiIT`       | Business rules through the frontend proxy: pricing, out of stock, insufficient stock, unknown SKU, validation |
| `WarehouseSyncIT` | Stock levels come from the Kafka events and are refreshed continuously                      |
| `ContractsIT`     | Microcks serves both contracts; the Pricing mock returns the contract examples              |
| `TracingIT`       | The test sets its own `traceparent`, then checks that trace in Jaeger: frontend → order-service → `place order` → Pricing HTTP call + SQL spans; Kafka consumer spans |
| `MetricsIT`       | Business counters, histogram and gauge; system metrics; span metrics; Redpanda; exemplars   |
| `DashboardIT`     | Dashboard provisioned, datasources healthy, Jaeger search through Grafana, and **every PromQL query of every panel returns data** |

## Notes / choices

- **Jaeger storage:** Badger (embedded, on a volume) keeps the stack light. For Elasticsearch or OpenSearch, add
  the container and swap the `jaeger_storage.backends` entry in `observability/jaeger/config.yaml`.
- **Jaeger is pinned to 2.20.0:** 2.21 removed the v1 HTTP endpoints (`/api/services`...) that the Grafana
  Jaeger datasource still calls.
- **Microcks async frequency:** the minion only schedules 3, 10 or 30 s ticks (`x-microcks-operation.frequency`).
- **Topic naming:** Microcks names the mock topic `<service name without spaces>-<version>-<channel>`.
- The frontend is traced server-side (nginx). Browser-side instrumentation (OTel JS web SDK) would need a
  bundler and a collector CORS setup; it is left out to keep the frontend dependency-free.
