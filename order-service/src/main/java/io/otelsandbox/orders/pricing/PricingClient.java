package io.otelsandbox.orders.pricing;

import java.net.http.HttpClient;

import io.otelsandbox.orders.SandboxProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Client of the external Pricing API (contracts/pricing-openapi.yaml). The outgoing HTTP call is
 * traced by the agent (JDK HttpClient instrumentation), which also propagates the W3C trace context.
 */
@Component
public class PricingClient {

    private final RestClient restClient;

    public PricingClient(RestClient.Builder builder, SandboxProperties properties) {
        var pricing = properties.pricing();
        var httpClient = HttpClient.newBuilder().connectTimeout(pricing.timeout()).build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(pricing.timeout());
        this.restClient = builder
                .baseUrl(pricing.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }

    public PriceQuote priceOf(String sku) {
        try {
            return restClient.get()
                    .uri("/prices/{sku}", sku)
                    .retrieve()
                    .body(PriceQuote.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new UnknownSkuException(sku);
            }
            throw new PricingUnavailableException("Pricing API rejected the request: " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new PricingUnavailableException("Pricing API unavailable", e);
        }
    }
}
