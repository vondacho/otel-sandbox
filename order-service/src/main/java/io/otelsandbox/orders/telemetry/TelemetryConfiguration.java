package io.otelsandbox.orders.telemetry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class TelemetryConfiguration {

    /**
     * The OpenTelemetry SDK is installed by the Java agent (see Dockerfile); without the agent this
     * resolves to a no-op implementation, so the service still runs from an IDE.
     */
    @Bean
    OpenTelemetry openTelemetry() {
        return GlobalOpenTelemetry.get();
    }
}
