package com.j2ee.tour;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure guard checks: no Spring refresh, no datasource connection, no .env access. */
class TourCacheDatabaseGuardTest {

    private static final String URL = "TOUR_CACHE_TEST_DB_URL";
    private static final String USERNAME = "TOUR_CACHE_TEST_DB_USERNAME";
    private static final String PASSWORD = "TOUR_CACHE_TEST_DB_PASSWORD";
    private static final String VALID_URL = "jdbc:postgresql://localhost:5432/tour_cache_test";

    @Test
    void missingVariablesRejected() {
        assertThatThrownBy(() -> validated(Map.of()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> validated(Map.of(URL, VALID_URL)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> validated(Map.of(URL, VALID_URL, USERNAME, "cache-user")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sharedAndWrongDatabaseUrlsRejected() {
        List.of(
                "jdbc:postgresql://db.example.com:5432/tour_cache_test",
                "jdbc:postgresql://localhost:5432/tour",
                "jdbc:mysql://localhost:3306/tour_cache_test",
                "postgresql://localhost:5432/tour_cache_test",
                "jdbc:postgresql://localhost:5432/tour_cache_test/"
        ).forEach(url -> assertThatThrownBy(() -> validated(validEnv(url)))
                .isInstanceOf(IllegalStateException.class)
                .as("reject %s", url));
    }

    @Test
    void queryFragmentAndUserInfoRejectedWithoutCredentialsInMessage() {
        List.of(
                "jdbc:postgresql://localhost:5432/tour_cache_test?sslmode=disable",
                "jdbc:postgresql://localhost:5432/tour_cache_test#ignored",
                "jdbc:postgresql://cache-user:top-secret@localhost:5432/tour_cache_test"
        ).forEach(url -> assertThatThrownBy(() -> validated(validEnv(url)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("top-secret")
                .as("reject %s", url));
    }

    @Test
    void loopbackHostsIncludingBracketedIpv6AreAccepted() {
        List.of(
                VALID_URL,
                "jdbc:postgresql://127.0.0.1:5432/tour_cache_test",
                "jdbc:postgresql://[::1]:5432/tour_cache_test"
        ).forEach(url -> assertThat(validated(validEnv(url)))
                .containsOnlyKeys("spring.datasource.url", "spring.datasource.username",
                        "spring.datasource.password")
                .containsEntry("spring.datasource.url", url)
                .containsEntry("spring.datasource.username", "cache-user")
                .containsEntry("spring.datasource.password", "synthetic-password")
                .as("accept %s", url));
    }

    @Test
    void initializerOverridesPreexistingDatasourcePropertiesWithoutRefresh() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("dummy", Map.of(
                "spring.datasource.url", "jdbc:postgresql://db.example.com/production",
                "spring.datasource.username", "shared-user",
                "spring.datasource.password", "shared-password")));

        TourCacheIntegrationTests.DedicatedDatabaseInitializer.initialize(context, validEnv(VALID_URL));

        assertThat(context.getEnvironment().getProperty("spring.datasource.url")).isEqualTo(VALID_URL);
        assertThat(context.getEnvironment().getProperty("spring.datasource.username"))
                .isEqualTo("cache-user");
        assertThat(context.getEnvironment().getProperty("spring.datasource.password"))
                .isEqualTo("synthetic-password");
    }

    private static Map<String, String> validated(Map<String, String> environment) {
        return TourCacheIntegrationTests.DedicatedDatabaseInitializer
                .validatedDatasourceProperties(environment);
    }

    private static Map<String, String> validEnv(String url) {
        Map<String, String> environment = new HashMap<>();
        environment.put(URL, url);
        environment.put(USERNAME, "cache-user");
        environment.put(PASSWORD, "synthetic-password");
        return environment;
    }
}
