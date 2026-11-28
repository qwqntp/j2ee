package com.j2ee.tour.common;

import org.springframework.boot.cache.autoconfigure.CacheProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

/** Plain Spring contexts importing CacheConfig lack Boot's CacheProperties auto-configuration. */
@TestConfiguration(proxyBeanMethods = false)
public class CachePropertiesTestConfig {

    @Bean
    CacheProperties cacheProperties() {
        CacheProperties properties = new CacheProperties();
        properties.getRedis().setTimeToLive(Duration.ofMinutes(10));
        properties.getRedis().setKeyPrefix("tour:");
        properties.getRedis().setCacheNullValues(false);
        return properties;
    }
}
