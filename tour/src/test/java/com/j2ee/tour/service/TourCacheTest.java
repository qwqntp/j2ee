package com.j2ee.tour.service;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.TourSearchCriteria;
import com.j2ee.tour.dto.response.TourDepartureResponse;
import com.j2ee.tour.dto.response.TourResponse;
import com.j2ee.tour.dto.response.TourSummaryResponse;
import com.j2ee.tour.model.Tour;
import com.j2ee.tour.model.TourDeparture;
import com.j2ee.tour.model.TourImage;
import com.j2ee.tour.repository.AccommodationRepository;
import com.j2ee.tour.repository.PromotionRepository;
import com.j2ee.tour.repository.TourDepartureRepository;
import com.j2ee.tour.repository.TourGuideRepository;
import com.j2ee.tour.repository.TourImageRepository;
import com.j2ee.tour.repository.TourRepository;
import com.j2ee.tour.repository.TourStatsProjection;
import com.j2ee.tour.repository.TouristDestinationRepository;
import com.j2ee.tour.repository.TravelVehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(TourCacheTest.Config.class)
class TourCacheTest {

    private static final Instant BASE = Instant.parse("2026-11-28T07:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDateTime LOCAL_BASE = LocalDateTime.ofInstant(BASE, ZONE);
    private static final LocalDateTime LOCAL_AFTER = LocalDateTime.ofInstant(BASE.plusSeconds(2), ZONE);
    private static final LocalDateTime TRIP_START = LocalDateTime.ofInstant(BASE.plusSeconds(1), ZONE);
    private static final LocalDateTime UPCOMING = LocalDateTime.of(2026, 12, 1, 8, 0);

    @Autowired
    private TourService tourService;
    @Autowired
    private TourRepository tourRepository;
    @Autowired
    private TourDepartureRepository departureRepository;
    @Autowired
    private TourImageRepository tourImageRepository;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        cacheManager.getCache("tours").clear();
        reset(tourRepository, departureRepository, tourImageRepository);
        clock.set(BASE);
    }

    @Test
    void detailReusesContentButReloadsDepartures() {
        when(tourRepository.existsByTourIdAndTourStatus(1L, "OPEN")).thenReturn(true);
        when(tourRepository.findByTourIdAndTourStatus(1L, "OPEN")).thenReturn(Optional.of(tour()));
        when(departureRepository
                .findByTourTourIdAndDepartureStatusInAndDepartureStartDateAfterOrderByDepartureStartDateAsc(
                        eq(1L), eq(List.of("OPEN", "FULL")), any(LocalDateTime.class)))
                .thenReturn(List.of(departure(1L, 2, UPCOMING)),
                        List.of(departure(1L, 3, UPCOMING)));

        TourResponse first = tourService.getTour(1L);
        TourResponse second = tourService.getTour(1L);

        assertThat(first.description()).isEqualTo("Ngay 1: Da Nang");
        assertThat(first.itinerary()).containsExactly("Ngay 1: Da Nang", "Ngay 2: Hoi An");
        assertThat(first.departures().getFirst().seatsAvailable()).isEqualTo(28);
        assertThat(second.departures().getFirst().seatsAvailable()).isEqualTo(27);
        verify(tourRepository, times(1)).findByTourIdAndTourStatus(1L, "OPEN");
        verify(tourRepository, times(2)).existsByTourIdAndTourStatus(1L, "OPEN");
        verify(departureRepository, times(2))
                .findByTourTourIdAndDepartureStatusInAndDepartureStartDateAfterOrderByDepartureStartDateAsc(
                        eq(1L), eq(List.of("OPEN", "FULL")), any(LocalDateTime.class));
    }

    @Test
    void departuresAlwaysReloadWithoutContentCache() {
        when(tourRepository.existsByTourIdAndTourStatus(1L, "OPEN")).thenReturn(true);
        when(departureRepository
                .findByTourTourIdAndDepartureStatusInAndDepartureStartDateAfterOrderByDepartureStartDateAsc(
                        eq(1L), eq(List.of("OPEN", "FULL")), any(LocalDateTime.class)))
                .thenReturn(List.of(departure(1L, 2, UPCOMING)),
                        List.of(departure(1L, 3, UPCOMING)));

        List<TourDepartureResponse> first = tourService.getDepartures(1L);
        List<TourDepartureResponse> second = tourService.getDepartures(1L);

        assertThat(first.getFirst().bookedSlots()).isEqualTo(2);
        assertThat(first.getFirst().seatsAvailable()).isEqualTo(28);
        assertThat(second.getFirst().bookedSlots()).isEqualTo(3);
        assertThat(second.getFirst().seatsAvailable()).isEqualTo(27);
        verify(tourRepository, never()).findByTourIdAndTourStatus(1L, "OPEN");
        verify(departureRepository, times(2))
                .findByTourTourIdAndDepartureStatusInAndDepartureStartDateAfterOrderByDepartureStartDateAsc(
                        eq(1L), eq(List.of("OPEN", "FULL")), any(LocalDateTime.class));
        assertThat(cacheManager.getCache("tours").get("v2:detail:1")).isNull();
    }

    @Test
    void databaseFailureDoesNotServeStaleSeats() {
        when(tourRepository.existsByTourIdAndTourStatus(1L, "OPEN")).thenReturn(true);
        when(tourRepository.findByTourIdAndTourStatus(1L, "OPEN")).thenReturn(Optional.of(tour()));
        when(departureRepository
                .findByTourTourIdAndDepartureStatusInAndDepartureStartDateAfterOrderByDepartureStartDateAsc(
                        eq(1L), eq(List.of("OPEN", "FULL")), any(LocalDateTime.class)))
                .thenReturn(List.of(departure(1L, 2, UPCOMING)))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThat(tourService.getTour(1L).departures().getFirst().seatsAvailable()).isEqualTo(28);
        assertThatThrownBy(() -> tourService.getTour(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        Cache.ValueWrapper cached = cacheManager.getCache("tours").get("v2:detail:1");
        assertThat(cached).isNotNull();
        assertThat(cached.get()).isInstanceOf(TourResponse.class);
        assertThat(((TourResponse) cached.get()).departures()).isEmpty();
    }

    @Test
    void warmDetailCannotExposeNonpublicTour() {
        when(tourRepository.existsByTourIdAndTourStatus(1L, "OPEN")).thenReturn(true);
        when(tourRepository.findByTourIdAndTourStatus(1L, "OPEN")).thenReturn(Optional.of(tour()));
        when(departureRepository
                .findByTourTourIdAndDepartureStatusInAndDepartureStartDateAfterOrderByDepartureStartDateAsc(
                        eq(1L), eq(List.of("OPEN", "FULL")), any(LocalDateTime.class)))
                .thenReturn(List.of(departure(1L, 2, UPCOMING)));

        tourService.getTour(1L);
        assertThat(cacheManager.getCache("tours").get("v2:detail:1")).isNotNull();
        when(tourRepository.existsByTourIdAndTourStatus(1L, "OPEN")).thenReturn(false);

        assertThatThrownBy(() -> tourService.getTour(1L))
                .isInstanceOf(ApiException.class)
                .hasMessage("Tour not found")
                .satisfies(error -> assertThat(((ApiException) error).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> tourService.getDepartures(1L))
                .isInstanceOf(ApiException.class)
                .hasMessage("Tour not found")
                .satisfies(error -> assertThat(((ApiException) error).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
        verify(tourRepository, times(3)).existsByTourIdAndTourStatus(1L, "OPEN");
    }

    @Test
    void catalogReloadsStatsButReusesContent() {
        Page<Long> idPage = new PageImpl<>(List.of(1L), PageRequest.of(0, 12), 1);
        when(tourRepository.searchTourIds(any(), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(idPage);
        when(tourRepository.findAllById(List.of(1L))).thenReturn(List.of(tour()));
        TourImageRepository.TourImageProjection firstImage = image(1L, "https://img/1.jpg");
        when(tourImageRepository.findImageUrlsByTourIds(List.of(1L)))
                .thenReturn(List.of(firstImage));
        TourStatsProjection firstStats = stats(1L, new BigDecimal("4500000"), UPCOMING, 28, 1);
        TourStatsProjection secondStats = stats(1L, new BigDecimal("4500000"), UPCOMING, 27, 1);
        when(departureRepository.findStatsByTourIds(anyCollection(), any(LocalDateTime.class)))
                .thenReturn(List.of(firstStats), List.of(secondStats));

        PageResponse<TourSummaryResponse> first = tourService.listTours(0, 12, criteria());
        PageResponse<TourSummaryResponse> second = tourService.listTours(0, 12, criteria());

        assertThat(first.content().getFirst().name()).isEqualTo("Da Nang - Hoi An");
        assertThat(first.content().getFirst().seatsAvailable()).isEqualTo(28);
        assertThat(second.content().getFirst().seatsAvailable()).isEqualTo(27);
        verify(tourRepository, times(2))
                .searchTourIds(any(), any(LocalDateTime.class), any(Pageable.class));
        verify(departureRepository, times(2))
                .findStatsByTourIds(anyCollection(), any(LocalDateTime.class));
        verify(tourRepository, times(1)).findAllById(List.of(1L));
        verify(tourImageRepository, times(1)).findImageUrlsByTourIds(List.of(1L));
    }

    @Test
    void clockAdvanceRemovesDepartedTrip() {
        Page<Long> idPage = new PageImpl<>(List.of(1L), PageRequest.of(0, 12), 1);
        when(tourRepository.searchTourIds(any(), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(idPage);
        when(tourRepository.findAllById(List.of(1L))).thenReturn(List.of(tour()));
        when(tourImageRepository.findImageUrlsByTourIds(List.of(1L))).thenReturn(List.of());
        when(tourRepository.existsByTourIdAndTourStatus(1L, "OPEN")).thenReturn(true);
        when(tourRepository.findByTourIdAndTourStatus(1L, "OPEN")).thenReturn(Optional.of(tour()));
        when(departureRepository
                .findByTourTourIdAndDepartureStatusInAndDepartureStartDateAfterOrderByDepartureStartDateAsc(
                        eq(1L), eq(List.of("OPEN", "FULL")), any(LocalDateTime.class)))
                .thenAnswer(invocation -> {
                    LocalDateTime now = invocation.getArgument(2);
                    return now.isBefore(TRIP_START)
                            ? List.of(departure(1L, 2, TRIP_START)) : List.of();
                });
        when(departureRepository.findStatsByTourIds(anyCollection(), any(LocalDateTime.class)))
                .thenAnswer(invocation -> {
                    LocalDateTime now = invocation.getArgument(1);
                    return now.isBefore(TRIP_START)
                            ? List.of(stats(1L, new BigDecimal("4500000"), TRIP_START, 28, 1))
                            : List.of();
                });

        assertThat(tourService.getTour(1L).departures()).hasSize(1);
        assertThat(tourService.getDepartures(1L)).hasSize(1);
        TourSummaryResponse before = tourService.listTours(0, 12, criteria()).content().getFirst();
        assertThat(before.priceFrom()).isEqualByComparingTo("4500000");
        assertThat(before.nextDepartureDate()).isEqualTo(TRIP_START);
        assertThat(before.seatsAvailable()).isEqualTo(28);

        clock.set(BASE.plusSeconds(2));

        assertThat(tourService.getTour(1L).departures()).isEmpty();
        assertThat(tourService.getDepartures(1L)).isEmpty();
        TourSummaryResponse after = tourService.listTours(0, 12, criteria()).content().getFirst();
        assertThat(after.priceFrom()).isNull();
        assertThat(after.nextDepartureDate()).isNull();
        assertThat(after.seatsAvailable()).isZero();
        assertThat(after.departureCount()).isZero();

        ArgumentCaptor<LocalDateTime> searchTimes = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(tourRepository, times(2)).searchTourIds(any(), searchTimes.capture(), any(Pageable.class));
        assertThat(searchTimes.getAllValues()).containsExactly(LOCAL_BASE, LOCAL_AFTER);
        ArgumentCaptor<LocalDateTime> statTimes = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(departureRepository, times(2)).findStatsByTourIds(anyCollection(), statTimes.capture());
        assertThat(statTimes.getAllValues()).containsExactly(LOCAL_BASE, LOCAL_AFTER);
        verify(tourRepository, times(1)).findByTourIdAndTourStatus(1L, "OPEN");
    }

    @Test
    void filteredAndSortedPagesBypassCache() {
        Page<Long> idPage = new PageImpl<>(List.of(1L), PageRequest.of(0, 12), 1);
        when(tourRepository.searchTourIds(any(), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(idPage);
        when(tourRepository.findAllById(List.of(1L))).thenReturn(List.of(tour()));
        when(tourImageRepository.findImageUrlsByTourIds(List.of(1L))).thenReturn(List.of());
        TourStatsProjection firstStats = stats(1L, new BigDecimal("4500000"), UPCOMING, 28, 1);
        TourStatsProjection secondStats = stats(1L, new BigDecimal("4500000"), UPCOMING, 27, 1);
        when(departureRepository.findStatsByTourIds(anyCollection(), any(LocalDateTime.class)))
                .thenReturn(List.of(firstStats), List.of(secondStats));

        PageResponse<TourSummaryResponse> keyword = tourService.listTours(0, 12, keyword("DaNang"));
        PageResponse<TourSummaryResponse> sorted = tourService.listTours(0, 12,
                criteria(TourSearchCriteria.Sort.PRICE_ASC));

        assertThat(keyword.content()).extracting(TourSummaryResponse::id).containsExactly(1L);
        assertThat(keyword.content().getFirst().seatsAvailable()).isEqualTo(28);
        assertThat(sorted.content()).extracting(TourSummaryResponse::id).containsExactly(1L);
        assertThat(sorted.content().getFirst().seatsAvailable()).isEqualTo(27);
        assertThat(cacheManager.getCache("tours").get("v2:catalog:[1]")).isNull();
        verify(tourRepository, times(2)).findAllById(List.of(1L));
        verify(tourImageRepository, times(2)).findImageUrlsByTourIds(List.of(1L));
        verify(departureRepository, times(2))
                .findStatsByTourIds(anyCollection(), any(LocalDateTime.class));
    }

    @Test
    void contentReloadsAfterDeleteWithoutTransaction() {
        when(tourRepository.existsByTourIdAndTourStatus(1L, "OPEN")).thenReturn(true);
        when(tourRepository.findByTourIdAndTourStatus(1L, "OPEN")).thenReturn(Optional.of(tour()));
        when(departureRepository
                .findByTourTourIdAndDepartureStatusInAndDepartureStartDateAfterOrderByDepartureStartDateAsc(
                        eq(1L), eq(List.of("OPEN", "FULL")), any(LocalDateTime.class)))
                .thenReturn(List.of());
        Page<Long> idPage = new PageImpl<>(List.of(1L), PageRequest.of(0, 12), 1);
        when(tourRepository.searchTourIds(any(), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(idPage);
        when(tourRepository.findAllById(List.of(1L))).thenReturn(List.of(tour()));
        TourImageRepository.TourImageProjection firstImage = image(1L, "https://img/1.jpg");
        when(tourImageRepository.findImageUrlsByTourIds(List.of(1L)))
                .thenReturn(List.of(firstImage));
        when(departureRepository.findStatsByTourIds(anyCollection(), any(LocalDateTime.class)))
                .thenReturn(List.of());

        assertThat(tourService.getTour(1L).name()).isEqualTo("Da Nang - Hoi An");
        assertThat(tourService.listTours(0, 12, criteria()).content().getFirst().name())
                .isEqualTo("Da Nang - Hoi An");
        assertThat(cacheManager.getCache("tours").get("v2:detail:1")).isNotNull();
        assertThat(cacheManager.getCache("tours").get("v2:catalog:[1]")).isNotNull();

        when(tourRepository.findById(1L)).thenReturn(Optional.of(tour()));
        when(departureRepository.existsByTourTourId(1L)).thenReturn(false);
        tourService.delete(1L);

        assertThat(cacheManager.getCache("tours").get("v2:detail:1")).isNull();
        assertThat(cacheManager.getCache("tours").get("v2:catalog:[1]")).isNull();

        when(tourRepository.findByTourIdAndTourStatus(1L, "OPEN"))
                .thenReturn(Optional.of(tour("Renamed")));
        when(tourRepository.findAllById(List.of(1L))).thenReturn(List.of(tour("Renamed")));
        assertThat(tourService.getTour(1L).name()).isEqualTo("Renamed");
        assertThat(tourService.listTours(0, 12, criteria()).content().getFirst().name())
                .isEqualTo("Renamed");
    }

    private TourSearchCriteria criteria() {
        return criteria(TourSearchCriteria.Sort.DEFAULT);
    }

    private TourSearchCriteria criteria(TourSearchCriteria.Sort sort) {
        return new TourSearchCriteria(null, null, null, null, null, null, null, null, sort);
    }

    private TourSearchCriteria keyword(String keyword) {
        return new TourSearchCriteria(keyword, null, null, null, null, null, null, null,
                TourSearchCriteria.Sort.DEFAULT);
    }

    private TourStatsProjection stats(Long id, BigDecimal price, LocalDateTime date,
                                      long seats, long count) {
        TourStatsProjection stats = mock(TourStatsProjection.class);
        when(stats.getTourId()).thenReturn(id);
        when(stats.getPriceFrom()).thenReturn(price);
        when(stats.getNextDepartureDate()).thenReturn(date);
        when(stats.getSeatsAvailable()).thenReturn(seats);
        when(stats.getDepartureCount()).thenReturn(count);
        return stats;
    }

    private TourImageRepository.TourImageProjection image(Long tourId, String url) {
        TourImageRepository.TourImageProjection projection =
                mock(TourImageRepository.TourImageProjection.class);
        when(projection.getTourId()).thenReturn(tourId);
        when(projection.getImageUrl()).thenReturn(url);
        return projection;
    }

    private Tour tour() {
        return tour("Da Nang - Hoi An");
    }

    private Tour tour(String name) {
        Tour tour = new Tour();
        tour.setTourId(1L);
        tour.setTourName(name);
        tour.setTourDescription("Ngay 1: Da Nang|Ngay 2: Hoi An");
        tour.setTourStatus("OPEN");
        tour.setTourDurationDays(3);
        TourImage image = new TourImage();
        image.setImageUrl("https://img/1.jpg");
        image.setTour(tour);
        tour.getTourImages().add(image);
        return tour;
    }

    private TourDeparture departure(Long id, int bookedSlots, LocalDateTime start) {
        TourDeparture departure = new TourDeparture();
        departure.setDepartureId(id);
        departure.setDepartureStatus("OPEN");
        departure.setDepartureStartDate(start);
        departure.setDepartureEndDate(start.plusDays(2));
        departure.setDepartureCapacity(30);
        departure.setDepartureBookedSlots(bookedSlots);
        departure.setDeparturePrice(new BigDecimal("4500000"));
        departure.setDepartureCreatedAt(start.minusDays(1));
        return departure;
    }

    static class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void set(Instant instant) {
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

    @Configuration
    @EnableCaching
    static class Config {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("tours");
        }

        @Bean
        MutableClock clock() {
            return new MutableClock(BASE, ZONE);
        }

        @Bean
        TourContentCache tourContentCache(TourRepository tourRepository,
                                          TourImageRepository tourImageRepository) {
            return new TourContentCache(tourRepository, tourImageRepository);
        }

        @Bean
        TourCacheInvalidator cacheInvalidator(CacheManager cacheManager) {
            return new TourCacheInvalidator(cacheManager);
        }

        @Bean
        TourService tourService(TourRepository tourRepository,
                                TourDepartureRepository departureRepository,
                                Clock clock,
                                AccommodationRepository accommodationRepository,
                                TourGuideRepository tourGuideRepository,
                                PromotionRepository promotionRepository,
                                TouristDestinationRepository destinationRepository,
                                TravelVehicleRepository vehicleRepository,
                                TourImageRepository tourImageRepository,
                                TourContentCache tourContentCache,
                                TourCacheInvalidator cacheInvalidator) {
            return new TourService(tourRepository, departureRepository, clock,
                    accommodationRepository, tourGuideRepository, promotionRepository,
                    destinationRepository, vehicleRepository, tourImageRepository,
                    new GuideOverlapValidator(departureRepository, tourGuideRepository),
                    tourContentCache, cacheInvalidator);
        }

        @Bean
        TourRepository tourRepository() {
            return mock(TourRepository.class);
        }

        @Bean
        TourDepartureRepository departureRepository() {
            return mock(TourDepartureRepository.class);
        }

        @Bean
        AccommodationRepository accommodationRepository() {
            return mock(AccommodationRepository.class);
        }

        @Bean
        TourGuideRepository tourGuideRepository() {
            return mock(TourGuideRepository.class);
        }

        @Bean
        PromotionRepository promotionRepository() {
            return mock(PromotionRepository.class);
        }

        @Bean
        TouristDestinationRepository destinationRepository() {
            return mock(TouristDestinationRepository.class);
        }

        @Bean
        TravelVehicleRepository vehicleRepository() {
            return mock(TravelVehicleRepository.class);
        }

        @Bean
        TourImageRepository tourImageRepository() {
            return mock(TourImageRepository.class);
        }
    }
}
