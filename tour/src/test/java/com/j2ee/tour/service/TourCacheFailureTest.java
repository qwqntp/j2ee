package com.j2ee.tour.service;

import com.j2ee.tour.common.CacheConfig;
import com.j2ee.tour.common.CachePropertiesTestConfig;
import com.j2ee.tour.repository.AccommodationRepository;
import com.j2ee.tour.repository.PromotionRepository;
import com.j2ee.tour.repository.TourDepartureRepository;
import com.j2ee.tour.repository.TourGuideRepository;
import com.j2ee.tour.repository.TourImageRepository;
import com.j2ee.tour.repository.TourRepository;
import com.j2ee.tour.repository.TouristDestinationRepository;
import com.j2ee.tour.repository.TravelVehicleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Separate plain Spring contexts: production CacheConfig with a broken or no-op cache manager. */
class TourCacheFailureTest {

    private static final Instant BASE = Instant.parse("2026-11-28T07:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Test
    void redisReadFailureFallsBackToDatabase() {
        TourRepository tourRepository = mock(TourRepository.class);
        TourDepartureRepository departureRepository = mock(TourDepartureRepository.class);
        stubReads(tourRepository, departureRepository);

        withService(new SingleCacheManager(new FaultyCache(true, false)),
                tourRepository, departureRepository, service -> {
                    assertThat(service.getTour(1L).departures().getFirst().seatsAvailable()).isEqualTo(28);
                    assertThat(service.getTour(1L).departures().getFirst().seatsAvailable()).isEqualTo(27);
                });

        verify(tourRepository, times(2)).findByTourIdAndTourStatus(1L, "OPEN");
        verify(departureRepository, times(2)).findByTourTourIdAndDepartureStatusInAndDepartureStartDateAfterOrderByDepartureStartDateAsc(
                eq(1L), eq(List.of("OPEN", "FULL")), any(LocalDateTime.class));
    }

    @Test
    void redisWriteFailureKeepsLiveResponse() {
        TourRepository tourRepository = mock(TourRepository.class);
        TourDepartureRepository departureRepository = mock(TourDepartureRepository.class);
        stubReads(tourRepository, departureRepository);

        withService(new SingleCacheManager(new FaultyCache(false, true)),
                tourRepository, departureRepository, service -> {
                    com.j2ee.tour.dto.response.TourResponse first = service.getTour(1L);
                    assertThat(first.name()).isEqualTo("Da Nang - Hoi An");
                    assertThat(first.departures().getFirst().seatsAvailable()).isEqualTo(28);
                    assertThat(service.getTour(1L).departures().getFirst().seatsAvailable()).isEqualTo(27);
                });

        verify(tourRepository, times(2)).findByTourIdAndTourStatus(1L, "OPEN");
        verify(departureRepository, times(2)).findByTourTourIdAndDepartureStatusInAndDepartureStartDateAfterOrderByDepartureStartDateAsc(
                eq(1L), eq(List.of("OPEN", "FULL")), any(LocalDateTime.class));
    }

    @Test
    void cacheDisabledStillReturnsFreshSeats() {
        TourRepository tourRepository = mock(TourRepository.class);
        TourDepartureRepository departureRepository = mock(TourDepartureRepository.class);
        stubReads(tourRepository, departureRepository);
        NoOpCacheManager noOp = new NoOpCacheManager();

        withService(noOp, tourRepository, departureRepository, service -> {
            assertThat(service.getTour(1L).departures().getFirst().seatsAvailable()).isEqualTo(28);
            assertThat(noOp.getCache("tours").get("v2:detail:1")).isNull();
            assertThat(service.getTour(1L).departures().getFirst().seatsAvailable()).isEqualTo(27);
        });

        verify(tourRepository, times(2)).findByTourIdAndTourStatus(1L, "OPEN");
        verify(departureRepository, times(2)).findByTourTourIdAndDepartureStatusInAndDepartureStartDateAfterOrderByDepartureStartDateAsc(
                eq(1L), eq(List.of("OPEN", "FULL")), any(LocalDateTime.class));
    }

    private static void withService(CacheManager cacheManager, TourRepository tourRepository,
                                    TourDepartureRepository departureRepository,
                                    Consumer<TourService> checks) {
        new ApplicationContextRunner()
                .withUserConfiguration(CacheConfig.class, CachePropertiesTestConfig.class,
                        TourContentCache.class, TourCacheInvalidator.class, TourService.class,
                        GuideOverlapValidator.class, MockRepositories.class)
                .withBean(CacheManager.class, () -> cacheManager)
                .withBean(Clock.class, () -> Clock.fixed(BASE, ZONE))
                .withBean(TourRepository.class, () -> tourRepository)
                .withBean(TourDepartureRepository.class, () -> departureRepository)
                .run(context -> checks.accept(context.getBean(TourService.class)));
    }

    private static void stubReads(TourRepository tourRepository,
                                  TourDepartureRepository departureRepository) {
        when(tourRepository.existsByTourIdAndTourStatus(1L, "OPEN")).thenReturn(true);
        when(tourRepository.findByTourIdAndTourStatus(1L, "OPEN")).thenReturn(Optional.of(tour()));
        when(departureRepository
                .findByTourTourIdAndDepartureStatusInAndDepartureStartDateAfterOrderByDepartureStartDateAsc(
                        eq(1L), eq(List.of("OPEN", "FULL")), any(LocalDateTime.class)))
                .thenReturn(List.of(departure(2)), List.of(departure(3)));
    }

    private static com.j2ee.tour.model.Tour tour() {
        com.j2ee.tour.model.Tour tour = new com.j2ee.tour.model.Tour();
        tour.setTourId(1L);
        tour.setTourName("Da Nang - Hoi An");
        tour.setTourDescription("Ngay 1: Da Nang|Ngay 2: Hoi An");
        tour.setTourStatus("OPEN");
        tour.setTourDurationDays(3);
        return tour;
    }

    private static com.j2ee.tour.model.TourDeparture departure(int bookedSlots) {
        com.j2ee.tour.model.TourDeparture departure = new com.j2ee.tour.model.TourDeparture();
        departure.setDepartureId(1L);
        departure.setDepartureStatus("OPEN");
        departure.setDepartureStartDate(LocalDateTime.ofInstant(BASE.plusSeconds(3600), ZONE));
        departure.setDepartureEndDate(LocalDateTime.ofInstant(BASE.plusSeconds(3600 * 49), ZONE));
        departure.setDepartureCapacity(30);
        departure.setDepartureBookedSlots(bookedSlots);
        departure.setDeparturePrice(new BigDecimal("4500000"));
        departure.setDepartureCreatedAt(LocalDateTime.ofInstant(BASE.minusSeconds(86400), ZONE));
        return departure;
    }

    @Configuration(proxyBeanMethods = false)
    static class MockRepositories {

        @org.springframework.context.annotation.Bean
        AccommodationRepository accommodationRepository() {
            return mock(AccommodationRepository.class);
        }

        @org.springframework.context.annotation.Bean
        TourGuideRepository tourGuideRepository() {
            return mock(TourGuideRepository.class);
        }

        @org.springframework.context.annotation.Bean
        PromotionRepository promotionRepository() {
            return mock(PromotionRepository.class);
        }

        @org.springframework.context.annotation.Bean
        TouristDestinationRepository destinationRepository() {
            return mock(TouristDestinationRepository.class);
        }

        @org.springframework.context.annotation.Bean
        TravelVehicleRepository vehicleRepository() {
            return mock(TravelVehicleRepository.class);
        }

        @org.springframework.context.annotation.Bean
        TourImageRepository tourImageRepository() {
            return mock(TourImageRepository.class);
        }
    }

    private static final class SingleCacheManager implements CacheManager {
        private final Cache cache;

        SingleCacheManager(Cache cache) {
            this.cache = cache;
        }

        @Override
        public Cache getCache(String name) {
            return "tours".equals(name) ? cache : null;
        }

        @Override
        public Collection<String> getCacheNames() {
            return List.of("tours");
        }
    }

    private static final class FaultyCache implements Cache {
        private final ConcurrentMap<Object, Object> store = new ConcurrentHashMap<>();
        private final boolean failGet;
        private final boolean failPut;

        FaultyCache(boolean failGet, boolean failPut) {
            this.failGet = failGet;
            this.failPut = failPut;
        }

        @Override
        public String getName() {
            return "tours";
        }

        @Override
        public Object getNativeCache() {
            return store;
        }

        @Override
        public ValueWrapper get(Object key) {
            if (failGet) {
                throw new IllegalStateException("simulated cache outage");
            }
            Object value = store.get(key);
            return value == null ? null : () -> value;
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            ValueWrapper wrapper = get(key);
            if (wrapper == null) {
                return null;
            }
            Object value = wrapper.get();
            if (type == null) {
                @SuppressWarnings("unchecked")
                T untyped = (T) value;
                return untyped;
            }
            return type.isInstance(value) ? type.cast(value) : null;
        }

        @Override
        public <T> T get(Object key, Callable<T> valueLoader) {
            ValueWrapper wrapper = get(key);
            if (wrapper != null) {
                @SuppressWarnings("unchecked")
                T value = (T) wrapper.get();
                return value;
            }
            try {
                T value = valueLoader.call();
                put(key, value);
                return value;
            } catch (Exception exception) {
                throw new Cache.ValueRetrievalException(key, valueLoader, exception);
            }
        }

        @Override
        public void put(Object key, Object value) {
            if (failPut) {
                throw new IllegalStateException("simulated cache outage");
            }
            store.put(key, value);
        }

        @Override
        public ValueWrapper putIfAbsent(Object key, Object value) {
            ValueWrapper existing = get(key);
            if (existing == null) {
                put(key, value);
            }
            return existing;
        }

        @Override
        public void evict(Object key) {
            store.remove(key);
        }

        @Override
        public void clear() {
            store.clear();
        }
    }
}
