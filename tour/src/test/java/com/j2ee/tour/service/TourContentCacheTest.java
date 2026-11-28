package com.j2ee.tour.service;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.common.CacheConfig;
import com.j2ee.tour.common.CachePropertiesTestConfig;
import com.j2ee.tour.dto.cache.TourCardContent;
import com.j2ee.tour.dto.response.TourDepartureResponse;
import com.j2ee.tour.dto.response.TourResponse;
import com.j2ee.tour.mapper.TourMapper;
import com.j2ee.tour.model.Accommodation;
import com.j2ee.tour.model.Promotion;
import com.j2ee.tour.model.Tour;
import com.j2ee.tour.model.TourGuide;
import com.j2ee.tour.model.TourImage;
import com.j2ee.tour.model.TouristDestination;
import com.j2ee.tour.model.TravelVehicle;
import com.j2ee.tour.repository.TourImageRepository;
import com.j2ee.tour.repository.TourRepository;
import com.j2ee.tour.service.TourContentCache.TourCardCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(classes = {
        CacheConfig.class,
        CachePropertiesTestConfig.class,
        TourContentCache.class,
        TourContentCacheTest.TestCaches.class})
class TourContentCacheTest {

    @MockitoBean
    private TourRepository tourRepository;
    @MockitoBean
    private TourImageRepository tourImageRepository;

    @Autowired
    private TourContentCache tourContentCache;
    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        cacheManager.getCache("tours").clear();
        reset(tourRepository, tourImageRepository);
    }

    @Test
    void cachesDetailWithoutDepartures() {
        Tour tour = tour(1L, "Da Nang - Hoi An", "Ngay 1: Da Nang|Ngay 2: Hoi An");
        when(tourRepository.findByTourIdAndTourStatus(1L, "OPEN")).thenReturn(Optional.of(tour));
        TourResponse expected = TourMapper.toDetail(tour, List.of());

        TourResponse first = tourContentCache.getDetailContent(1L);
        TourResponse second = tourContentCache.getDetailContent(1L);

        assertThat(first).isEqualTo(expected);
        assertThat(second).isEqualTo(first);
        assertThat(second.departures()).isEmpty();
        verify(tourRepository, times(1)).findByTourIdAndTourStatus(1L, "OPEN");
        Cache.ValueWrapper cached = cacheManager.getCache("tours").get("v2:detail:1");
        assertThat(cached).isNotNull();
        assertThat(cached.get()).isEqualTo(expected);
    }

    @Test
    void cachesCatalogInRequestedIdOrder() {
        var image1 = image(1L, "https://img/1.jpg");
        var image2 = image(2L, "https://img/2.jpg");
        when(tourRepository.findAllById(List.of(2L, 1L)))
                .thenReturn(List.of(tour(1L, "One", "Ngay 1: One"), tour(2L, "Two", "Ngay 1: Two")));
        when(tourImageRepository.findImageUrlsByTourIds(List.of(2L, 1L)))
                .thenReturn(List.of(image1, image2));

        TourCardCatalog first = tourContentCache.getCatalogContent(List.of(2L, 1L), true);
        TourCardCatalog second = tourContentCache.getCatalogContent(List.of(2L, 1L), true);

        assertThat(first.items()).extracting(TourCardContent::id).containsExactly(2L, 1L);
        assertThat(first.items()).extracting(TourCardContent::name).containsExactly("Two", "One");
        assertThat(first.items().getFirst().imageUrl()).isEqualTo("https://img/2.jpg");
        assertThat(second.items()).isEqualTo(first.items());
        verify(tourRepository, times(1)).findAllById(List.of(2L, 1L));
        verify(tourImageRepository, times(1)).findImageUrlsByTourIds(List.of(2L, 1L));
    }

    @Test
    void multipleImagesKeepFirstByImageId() {
        var first = image(1L, "https://img/first.jpg");
        var second = image(1L, "https://img/second.jpg");
        when(tourRepository.findAllById(List.of(1L)))
                .thenReturn(List.of(tour(1L, "One", "Ngay 1: One")));
        when(tourImageRepository.findImageUrlsByTourIds(List.of(1L)))
                .thenReturn(List.of(first, second));

        TourCardCatalog result = tourContentCache.getCatalogContent(List.of(1L), true);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().imageUrl()).isEqualTo("https://img/first.jpg");
    }

    @Test
    void filteredCatalogBypassesWarmCache() {
        var image = image(1L, "https://img/1.jpg");
        when(tourRepository.findAllById(List.of(1L)))
                .thenReturn(List.of(tour(1L, "Old name", "Ngay 1: Old")));
        when(tourImageRepository.findImageUrlsByTourIds(List.of(1L)))
                .thenReturn(List.of(image));

        TourCardCatalog warm = tourContentCache.getCatalogContent(List.of(1L), true);
        assertThat(warm.items().getFirst().name()).isEqualTo("Old name");

        when(tourRepository.findAllById(List.of(1L)))
                .thenReturn(List.of(tour(1L, "New name", "Ngay 1: New")));
        TourCardCatalog fresh = tourContentCache.getCatalogContent(List.of(1L), false);

        assertThat(fresh.items().getFirst().name()).isEqualTo("New name");
        assertThat(warm.items().getFirst().name()).isEqualTo("Old name");
        assertThat(tourContentCache.getCatalogContent(List.of(1L), true).items())
                .isEqualTo(warm.items());
        verify(tourRepository, times(2)).findAllById(List.of(1L));
        verify(tourImageRepository, times(2)).findImageUrlsByTourIds(List.of(1L));
    }

    @Test
    void emptyIdsDoNotPopulateCache() {
        TourCardCatalog result = tourContentCache.getCatalogContent(List.of(), true);

        assertThat(result.items()).isEmpty();
        verifyNoInteractions(tourRepository, tourImageRepository);
        assertThat(cacheManager.getCache("tours").get("v2:catalog:[]")).isNull();
    }

    @Test
    void doesNotCacheMissingDetail() {
        when(tourRepository.findByTourIdAndTourStatus(99L, "OPEN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tourContentCache.getDetailContent(99L))
                .isInstanceOf(ApiException.class)
                .hasMessage("Tour not found")
                .satisfies(error -> assertThat(((ApiException) error).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> tourContentCache.getDetailContent(99L))
                .isInstanceOf(ApiException.class)
                .hasMessage("Tour not found");

        verify(tourRepository, times(2)).findByTourIdAndTourStatus(99L, "OPEN");
        assertThat(cacheManager.getCache("tours").get("v2:detail:99")).isNull();
    }

    @Test
    void mapperPreservesDetailFields() {
        TourResponse original = TourMapper.toDetail(tour(1L, "Da Nang", "Ngay 1: Da Nang|Ngay 2"), List.of());
        List<TourDepartureResponse> fresh = List.of(departure());

        TourResponse result = TourMapper.withDepartures(original, fresh);

        assertThat(result.departures()).isEqualTo(fresh);
        assertThat(result).usingRecursiveComparison().ignoringFields("departures").isEqualTo(original);
        assertThat(original.departures()).isEmpty();
    }

    @Test
    void catalogUsesTruncatedFirstSegmentAndHasNoLiveFields() {
        String longSegment = "X".repeat(200);
        when(tourRepository.findAllById(List.of(1L)))
                .thenReturn(List.of(tour(1L, "One", longSegment + "|Ngay 2: Hoi An")));
        when(tourImageRepository.findImageUrlsByTourIds(List.of(1L))).thenReturn(List.of());

        TourCardCatalog catalog = tourContentCache.getCatalogContent(List.of(1L), false);

        assertThat(catalog.items().getFirst().description()).isEqualTo("X".repeat(159) + "…");
        assertThat(java.util.Arrays.stream(TourCardContent.class.getRecordComponents())
                .map(RecordComponent::getName))
                .containsExactly("id", "name", "description", "status", "imageUrl");
        assertThat(java.util.Arrays.stream(TourCardCatalog.class.getRecordComponents())
                .map(RecordComponent::getName))
                .containsExactly("items");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestCaches {
        @Bean
        ConcurrentMapCacheManager cacheManager() {
            return new ConcurrentMapCacheManager("tours");
        }
    }

    private TourImageRepository.TourImageProjection image(Long tourId, String url) {
        TourImageRepository.TourImageProjection projection = mock(TourImageRepository.TourImageProjection.class);
        when(projection.getTourId()).thenReturn(tourId);
        when(projection.getImageUrl()).thenReturn(url);
        return projection;
    }

    private Tour tour(long id, String name, String description) {
        Tour tour = new Tour();
        tour.setTourId(id);
        tour.setTourName(name);
        tour.setTourDescription(description);
        tour.setTourStatus("OPEN");
        tour.setTourDurationDays(3);

        Accommodation accommodation = new Accommodation();
        accommodation.setAccommodationId(1L);
        accommodation.setAccommodationName("Beach Resort");
        accommodation.setRating(new BigDecimal("4.5"));
        accommodation.setAccommodationType("RESORT");
        tour.setAccommodation(accommodation);

        TourGuide guide = new TourGuide();
        guide.setTourGuideId(2L);
        guide.setTourGuideName("Nguyen Van A");
        guide.setTourGuidePhone("0900000000");
        tour.setTourGuide(guide);

        Promotion promotion = new Promotion();
        promotion.setPromotionId(3L);
        tour.setPromotion(promotion);

        TouristDestination destination = new TouristDestination();
        destination.setDestinationId(4L);
        destination.setDestinationName("Hoi An");
        tour.getDestinations().add(destination);

        TravelVehicle vehicle = new TravelVehicle();
        vehicle.setVehicleId(5L);
        vehicle.setVehicleType("BUS");
        tour.getVehicles().add(vehicle);

        TourImage tourImage = new TourImage();
        tourImage.setImageUrl("https://img/" + id + ".jpg");
        tourImage.setTour(tour);
        tour.getTourImages().add(tourImage);
        return tour;
    }

    private TourDepartureResponse departure() {
        return new TourDepartureResponse(
                9L,
                1L,
                LocalDateTime.of(2026, 11, 1, 8, 0),
                LocalDateTime.of(2026, 11, 3, 8, 0),
                30,
                2,
                28,
                new BigDecimal("4500000"),
                "Hoi An",
                "OPEN",
                LocalDateTime.of(2026, 10, 1, 8, 0));
    }
}
