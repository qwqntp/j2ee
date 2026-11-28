package com.j2ee.tour;

import com.j2ee.tour.common.CachePropertiesTestConfig;
import com.j2ee.tour.dto.request.CreateBookingRequest;
import com.j2ee.tour.dto.request.TourDepartureRequest;
import com.j2ee.tour.dto.request.TourRequest;
import com.j2ee.tour.dto.request.TourSearchCriteria;
import com.j2ee.tour.dto.response.TourResponse;
import com.j2ee.tour.dto.response.TourSummaryResponse;
import com.j2ee.tour.model.Account;
import com.j2ee.tour.model.AccountRole;
import com.j2ee.tour.model.Accommodation;
import com.j2ee.tour.model.Tour;
import com.j2ee.tour.model.TourDeparture;
import com.j2ee.tour.model.TourGuide;
import com.j2ee.tour.repository.AccommodationRepository;
import com.j2ee.tour.repository.AccountRepository;
import com.j2ee.tour.repository.AccountRoleRepository;
import com.j2ee.tour.repository.TourDepartureRepository;
import com.j2ee.tour.repository.TourGuideRepository;
import com.j2ee.tour.repository.TourRepository;
import com.j2ee.tour.security.AuthPrincipal;
import com.j2ee.tour.service.BookingService;
import com.j2ee.tour.service.TourDepartureService;
import com.j2ee.tour.service.TourService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in database-backed coverage for the cache/live-data boundary. The class is disabled unless
 * TOUR_CACHE_TEST_DB_URL is set, and the initializer rejects any non-dedicated datasource before
 * Flyway or Hibernate sees it. Fixtures are committed explicitly so afterCommit is exercised;
 * cleanup deletes only rows created by this class.
 */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = TourCacheIntegrationTests.DedicatedDatabaseInitializer.class)
@TestPropertySource(properties = {
        "spring.mail.host=",
        "spring.mail.username=",
        "spring.mail.password="
})
@Import({TourCacheIntegrationTests.TestBeans.class, CachePropertiesTestConfig.class})
@EnabledIfEnvironmentVariable(named = "TOUR_CACHE_TEST_DB_URL", matches = ".+")
class TourCacheIntegrationTests {

    private static final Instant BASE = Instant.parse("2026-11-28T07:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String CACHE = "tours";

    @Autowired private TourService tourService;
    @Autowired private TourDepartureService departureService;
    @Autowired private BookingService bookingService;
    @Autowired private TourRepository tourRepository;
    @Autowired private TourDepartureRepository departureRepository;
    @Autowired private AccountRoleRepository roleRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AccommodationRepository accommodationRepository;
    @Autowired private TourGuideRepository guideRepository;
    @Autowired private CacheManager cacheManager;
    @Autowired private MutableClock testClock;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @PersistenceContext private EntityManager entityManager;

    private final List<Long> bookingIds = new ArrayList<>();
    private Long roleId;
    private Long accountId;
    private Long accommodationId;
    private Long guideId;
    private Long tourId;
    private Long departureId;
    private LocalDateTime departureStart;
    private LocalDateTime departureEnd;
    private BigDecimal price;
    private String tourName;
    private AuthPrincipal principal;

    @BeforeEach
    void setUp() {
        testClock.setInstant(BASE);
        bookingIds.clear();
        cacheManager.getCache(CACHE).clear();

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        tourName = "Cache tour " + suffix;
        LocalDateTime base = LocalDateTime.ofInstant(BASE, ZONE);
        departureStart = base.plusDays(1);
        departureEnd = base.plusDays(3);
        price = new BigDecimal("4500000");

        transactionTemplate.executeWithoutResult(status -> {
            AccountRole role = new AccountRole();
            role.setRoleName("CACHE_" + suffix);
            role = roleRepository.save(role);
            roleId = role.getAccountRoleId();

            Account account = new Account();
            account.setUsername("cache-" + suffix);
            account.setPassword("secret");
            account.setAccountEmail("cache-" + suffix + "@example.com");
            account.setEmailVerified(1);
            account.setStatus(1);
            account.setAccountCreatedAt(LocalDateTime.now(testClock));
            account.setAccountUpdatedAt(LocalDateTime.now(testClock));
            account.setRole(role);
            account = accountRepository.save(account);
            accountId = account.getAccountId();
            principal = new AuthPrincipal(accountId, account.getUsername(), "ROLE_USER");

            Accommodation accommodation = new Accommodation();
            accommodation.setAccommodationName("Cache hotel " + suffix);
            accommodation.setLocation("Da Nang");
            accommodation = accommodationRepository.save(accommodation);
            accommodationId = accommodation.getAccommodationId();

            TourGuide guide = new TourGuide();
            guide.setTourGuideName("Cache guide " + suffix);
            guide.setTourGuideExperienceYears(1);
            guide = guideRepository.save(guide);
            guideId = guide.getTourGuideId();

            Tour tour = new Tour();
            tour.setTourName(tourName);
            tour.setTourDescription("Day 1: Da Nang|Day 2: Hoi An");
            tour.setTourDurationDays(3);
            tour.setTourStatus("OPEN");
            tour.setAccommodation(accommodation);
            tour.setTourGuide(guide);
            tour = tourRepository.save(tour);
            tourId = tour.getTourId();

            TourDeparture departure = new TourDeparture();
            departure.setTour(tour);
            departure.setDepartureStartDate(departureStart);
            departure.setDepartureEndDate(departureEnd);
            departure.setDepartureCapacity(30);
            departure.setDepartureBookedSlots(2);
            departure.setDeparturePrice(price);
            departure.setDepartureStatus("OPEN");
            departure.setDepartureCreatedAt(base.minusDays(1));
            departure = departureRepository.save(departure);
            departureId = departure.getDepartureId();
        });
        clearPersistenceContext();
    }

    @AfterEach
    void cleanUp() {
        bookingIds.forEach(id -> jdbcTemplate.update(
                "delete from bookings where booking_id = ?", id));
        jdbcTemplate.update("delete from tour_departures where departure_id = ?", departureId);
        jdbcTemplate.update("delete from tours where tour_id = ?", tourId);
        jdbcTemplate.update("delete from accounts where account_id = ?", accountId);
        jdbcTemplate.update("delete from account_roles where account_role_id = ?", roleId);
        jdbcTemplate.update("delete from accommodations where accommodation_id = ?", accommodationId);
        jdbcTemplate.update("delete from tour_guides where tour_guide_id = ?", guideId);
        cacheManager.getCache(CACHE).clear();
    }

    @Test
    void bookingAndCancellationRefreshSeatsWithoutEvictingContent() {
        TourResponse warm = tourService.getTour(tourId);
        CatalogPage warmPage = fixtureCatalog();
        TourSummaryResponse warmCard = warmPage.card();
        assertThat(warm.name()).isEqualTo(tourName);
        assertThat(warm.departures().getFirst().seatsAvailable()).isEqualTo(28);
        assertThat(warmCard.seatsAvailable()).isEqualTo(28);
        Cache cache = cacheManager.getCache(CACHE);
        Object warmDetail = cache.get("v2:detail:" + tourId).get();
        Object warmCatalog = cache.get(warmPage.key()).get();
        assertThat(warmDetail).isNotNull();
        assertThat(warmCatalog).isNotNull();

        Long bookingId = transactionTemplate.execute(status -> bookingService.create(
                new CreateBookingRequest(departureId, 1, "cache test"), principal).id());
        bookingIds.add(bookingId);
        clearPersistenceContext();
        // Check reuse before a public read could repopulate an evicted entry.
        assertThat(cache.get("v2:detail:" + tourId).get()).isSameAs(warmDetail);
        assertThat(cache.get(warmPage.key()).get()).isSameAs(warmCatalog);
        assertThat(tourService.getTour(tourId).departures().getFirst().seatsAvailable()).isEqualTo(27);
        assertThat(fixtureCatalog().card().seatsAvailable()).isEqualTo(27);

        transactionTemplate.executeWithoutResult(status -> bookingService.cancel(bookingId, principal));
        clearPersistenceContext();
        assertThat(cache.get("v2:detail:" + tourId).get()).isSameAs(warmDetail);
        assertThat(cache.get(warmPage.key()).get()).isSameAs(warmCatalog);
        assertThat(tourService.getTour(tourId).departures().getFirst().seatsAvailable()).isEqualTo(28);
        assertThat(fixtureCatalog().card().seatsAvailable()).isEqualTo(28);
    }

    @Test
    void departureCancellationIsImmediatelyVisible() {
        tourService.getTour(tourId);
        Cache cache = cacheManager.getCache(CACHE);
        assertThat(cache.get("v2:detail:" + tourId)).isNotNull();

        TourDepartureRequest cancel = new TourDepartureRequest(tourId, departureStart,
                departureEnd, 30, 2, price, "Meeting point", "CANCELLED");
        transactionTemplate.executeWithoutResult(status -> departureService.update(departureId, cancel));
        clearPersistenceContext();

        assertThat(tourService.getTour(tourId).departures()).isEmpty();
        assertThat(tourService.getDepartures(tourId)).isEmpty();
        TourSummaryResponse card = fixtureCatalog().card();
        assertThat(card.priceFrom()).isNull();
        assertThat(card.nextDepartureDate()).isNull();
        assertThat(card.seatsAvailable()).isZero();
        assertThat(card.departureCount()).isZero();
        assertThat(cache.get("v2:detail:" + tourId)).isNotNull();
    }

    @Test
    void contentCommitAndRollbackRespectCache() {
        assertThat(tourService.getTour(tourId).name()).isEqualTo(tourName);
        CatalogPage warmPage = fixtureCatalog();
        Cache cache = cacheManager.getCache(CACHE);
        assertThat(cache.get("v2:detail:" + tourId).get()).isNotNull();
        assertThat(cache.get(warmPage.key()).get()).isNotNull();

        transactionTemplate.executeWithoutResult(status ->
                tourService.update(tourId, request("Cache committed name")));
        clearPersistenceContext();
        assertThat(tourService.getTour(tourId).name()).isEqualTo("Cache committed name");
        fixtureCatalog(); // repopulate after the committed invalidation
        Object afterCommit = cache.get("v2:detail:" + tourId).get();
        Object afterCommitCatalog = cache.get(warmPage.key()).get();
        assertThat(afterCommit).isNotNull();
        assertThat(afterCommitCatalog).isNotNull();

        transactionTemplate.executeWithoutResult(status -> {
            tourService.update(tourId, request("Cache rolled back name"));
            status.setRollbackOnly();
        });
        clearPersistenceContext();
        // Rollback must leave cached content untouched; check before any public read.
        assertThat(cache.get("v2:detail:" + tourId).get()).isSameAs(afterCommit);
        assertThat(cache.get(warmPage.key()).get()).isSameAs(afterCommitCatalog);
        assertThat(tourService.getTour(tourId).name()).isEqualTo("Cache committed name");
    }

    @Test
    void clockAdvanceRemovesDepartureWithoutAnyWriteOrCacheClear() {
        assertThat(tourService.getTour(tourId).departures()).hasSize(1);
        CatalogPage warmPage = fixtureCatalog();
        assertThat(warmPage.card().seatsAvailable()).isEqualTo(28);
        Cache cache = cacheManager.getCache(CACHE);
        Object warmDetail = cache.get("v2:detail:" + tourId).get();
        Object warmCatalog = cache.get(warmPage.key()).get();

        testClock.setInstant(BASE.plusSeconds(2 * 24 * 60 * 60));
        clearPersistenceContext();

        assertThat(tourService.getTour(tourId).departures()).isEmpty();
        assertThat(tourService.getDepartures(tourId)).isEmpty();
        TourSummaryResponse card = fixtureCatalog().card();
        assertThat(card.priceFrom()).isNull();
        assertThat(card.nextDepartureDate()).isNull();
        assertThat(card.seatsAvailable()).isZero();
        assertThat(card.departureCount()).isZero();
        assertThat(cache.get("v2:detail:" + tourId).get()).isEqualTo(warmDetail);
        assertThat(cache.get(warmPage.key()).get()).isEqualTo(warmCatalog);
    }

    private TourRequest request(String name) {
        return new TourRequest(name, "Day 1: Da Nang|Day 2: Hoi An", 3, "OPEN",
                accommodationId, guideId, null, List.of(), List.of(), List.of());
    }

    /**
     * Finds the fixture on an unfiltered page and derives the cache key from that page's ordered
     * IDs. A keyword filter would bypass the catalog cache this class verifies.
     */
    private CatalogPage fixtureCatalog() {
        TourSearchCriteria unfiltered = new TourSearchCriteria(null, null, null, null,
                null, null, null, null, TourSearchCriteria.Sort.DEFAULT);
        for (int page = 0; page < 100; page++) {
            var result = tourService.listTours(page, 12, unfiltered);
            if (result.content().isEmpty()) {
                break;
            }
            List<Long> ids = result.content().stream().map(TourSummaryResponse::id).toList();
            for (TourSummaryResponse card : result.content()) {
                if (card.id().equals(tourId)) {
                    return new CatalogPage(card, "v2:catalog:" + ids);
                }
            }
            if (!result.hasNext()) {
                break;
            }
        }
        throw new IllegalStateException("Fixture tour missing from unfiltered catalog pages");
    }

    private record CatalogPage(TourSummaryResponse card, String key) {
    }

    private void clearPersistenceContext() {
        transactionTemplate.executeWithoutResult(status -> entityManager.clear());
    }

    /** Test-only mutable clock; @Primary selects it over ClockConfig.clock(). */
    static class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(BASE, ZONE);
        }

        @Bean
        @Primary
        CacheManager testCacheManager() {
            return new ConcurrentMapCacheManager(CACHE);
        }

        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }
    }

    /**
     * Sets dedicated datasource properties before Flyway or Hibernate connects. Values come only
     * from process environment; no .env file is read and errors never contain credentials.
     */
    public static class DedicatedDatabaseInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        private static final String URL_ENV = "TOUR_CACHE_TEST_DB_URL";
        private static final String USERNAME_ENV = "TOUR_CACHE_TEST_DB_USERNAME";
        private static final String PASSWORD_ENV = "TOUR_CACHE_TEST_DB_PASSWORD";
        private static final List<String> ALLOWED_HOSTS =
                List.of("localhost", "127.0.0.1", "[::1]");

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            initialize(applicationContext, System.getenv());
        }

        static void initialize(ConfigurableApplicationContext applicationContext,
                               Map<String, String> environment) {
            TestPropertyValues.of(validatedDatasourceProperties(environment))
                    .applyTo(applicationContext.getEnvironment());
        }

        public static Map<String, String> validatedDatasourceProperties(
                Map<String, String> environment) {
            String url = require(environment, URL_ENV);
            String username = require(environment, USERNAME_ENV);
            String password = require(environment, PASSWORD_ENV);
            validateUrl(url);

            Map<String, String> properties = new LinkedHashMap<>();
            properties.put("spring.datasource.url", url);
            properties.put("spring.datasource.username", username);
            properties.put("spring.datasource.password", password);
            return properties;
        }

        private static String require(Map<String, String> environment, String key) {
            String value = environment.get(key);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("Missing required test database variable: " + key);
            }
            return value.trim();
        }

        private static void validateUrl(String url) {
            if (!url.startsWith("jdbc:postgresql://")) {
                throw invalidUrl();
            }
            URI uri;
            try {
                uri = URI.create(url.substring("jdbc:".length()));
            } catch (IllegalArgumentException exception) {
                throw invalidUrl();
            }
            if (uri.getUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null
                    || !"/tour_cache_test".equals(uri.getPath())
                    || !ALLOWED_HOSTS.contains(uri.getHost())) {
                throw invalidUrl();
            }
        }

        private static IllegalStateException invalidUrl() {
            return new IllegalStateException(
                    "TOUR_CACHE_TEST_DB_URL must use a loopback jdbc:postgresql URL for database "
                            + "tour_cache_test without query, fragment or user info");
        }
    }
}
