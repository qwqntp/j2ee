package com.j2ee.tour.integration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * A dedicated VNPay HTTP client. QueryDr runs in the customer's request path, so it needs
 * short timeouts instead of the framework defaults.
 */
@Configuration
class VnpayHttpConfig {

    @Bean
    RestClient vnpayRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(factory).build();
    }
}
