package io.otelsandbox.orders;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sandbox")
public record SandboxProperties(Pricing pricing, Warehouse warehouse) {

    /** External Pricing system (OpenAPI contract, mocked by Microcks). */
    public record Pricing(URI baseUrl, Duration timeout) {
    }

    /** External Warehouse system (AsyncAPI contract, mocked by Microcks on Kafka). */
    public record Warehouse(String stockTopic) {
    }
}
