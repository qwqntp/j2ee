package com.j2ee.tour.service;

import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.AccommodationRequest;
import com.j2ee.tour.dto.request.TourGuideRequest;
import com.j2ee.tour.dto.request.TourImageRequest;
import com.j2ee.tour.dto.request.TourRequest;
import com.j2ee.tour.dto.request.TourSearchCriteria;
import com.j2ee.tour.dto.request.TouristDestinationRequest;
import com.j2ee.tour.dto.request.TravelVehicleRequest;
import com.j2ee.tour.dto.response.TourSummaryResponse;
import com.j2ee.tour.model.Accommodation;
import com.j2ee.tour.model.Tour;
import com.j2ee.tour.model.TourGuide;
import com.j2ee.tour.model.TourImage;
import com.j2ee.tour.model.TouristDestination;
import com.j2ee.tour.model.TravelVehicle;
import com.j2ee.tour.repository.AccommodationRepository;
import com.j2ee.tour.repository.PromotionRepository;
import com.j2ee.tour.repository.TourDepartureRepository;
import com.j2ee.tour.repository.TourGuideRepository;
import com.j2ee.tour.repository.TourImageRepository;
import com.j2ee.tour.repository.TourRepository;
import com.j2ee.tour.repository.TouristDestinationRepository;
import com.j2ee.tour.repository.TravelVehicleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(TourContentInvalidationTest.Config.class)
class TourContentInvalidationTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-28T00:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private TourContentCache tourContentCache;
    @Autowired
    private TourCacheInvalidator invalidator;
    @Autowired
    private TourRepository tourRepository;
    @Autowired
    private TourImageRepository tourImageRepository;
    @Autowired
    private AccommodationRepository accommodationRepository;
    @Autowired
    private TourGuideRepository tourGuideRepository;
    @Autowired
    private TouristDestinationRepository destinationRepository;
    @Autowired
    private TravelVehicleRepository vehicleRepository;
    @Autowired
    private TourDepartureRepository departureRepository;
    @Autowired
    private PromotionRepository promotionRepository;

    private TourService tourService;
    private TourImageService imageService;
    private AccommodationService accommodationService;
    private TourGuideService guideService;
    private TouristDestinationService destinationService;
    private TravelVehicleService vehicleService;

    @BeforeEach
    void setUp() {
        cacheManager.getCache("tours").clear();
        reset(tourRepository, tourImageRepository, accommodationRepository, tourGuideRepository,
                destinationRepository, vehicleRepository, departureRepository, promotionRepository,
                invalidator);
        stubRepositories();

        tourService = new TourService(tourRepository, departureRepository, CLOCK,
                accommodationRepository, tourGuideRepository, promotionRepository,
                destinationRepository, vehicleRepository, tourImageRepository,
                new GuideOverlapValidator(departureRepository, tourGuideRepository),
                tourContentCache, invalidator);
        imageService = new TourImageService(tourImageRepository, tourRepository, invalidator);
        accommodationService = new AccommodationService(accommodationRepository, invalidator);
        guideService = new TourGuideService(tourGuideRepository, invalidator);
        destinationService = new TouristDestinationService(destinationRepository, invalidator);
        vehicleService = new TravelVehicleService(vehicleRepository, invalidator);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("successfulMutations")
    void contentMutationsScheduleInvalidation(String label,
                                              Consumer<TourContentInvalidationTest> mutation) {
        mutation.accept(this);

        verify(invalidator, times(1)).invalidateAfterCommit();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("failedMutations")
    void failedContentMutationDoesNotInvalidate(String label, String message,
                                                Consumer<TourContentInvalidationTest> mutation) {
        assertThatThrownBy(() -> mutation.accept(this))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage(message);

        verify(invalidator, never()).invalidateAfterCommit();
    }

    @Test
    void imageReassignmentInvalidatesBothViews() {
        String[] images = {"img/a", "img/b"};
        when(tourRepository.existsByTourIdAndTourStatus(1L, "OPEN")).thenReturn(true);
        when(tourRepository.existsByTourIdAndTourStatus(2L, "OPEN")).thenReturn(true);
        when(tourRepository.findByTourIdAndTourStatus(1L, "OPEN"))
                .thenAnswer(invocation -> Optional.of(tour(1L, "Tour One", images[0])));
        when(tourRepository.findByTourIdAndTourStatus(2L, "OPEN"))
                .thenAnswer(invocation -> Optional.of(tour(2L, "Tour Two", images[1])));
        when(tourRepository.findAllById(anyList())).thenAnswer(invocation -> {
            List<Long> ids = invocation.getArgument(0);
            return ids.stream()
                    .map(id -> tour(id, id.equals(1L) ? "Tour One" : "Tour Two",
                            id.equals(1L) ? images[0] : images[1]))
                    .toList();
        });
        when(tourImageRepository.findImageUrlsByTourIds(anyList())).thenAnswer(invocation -> {
            List<TourImageRepository.TourImageProjection> projections = new ArrayList<>();
            if (images[0] != null) {
                projections.add(imageProjection(1L, images[0]));
            }
            if (images[1] != null) {
                projections.add(imageProjection(2L, images[1]));
            }
            return projections;
        });
        when(departureRepository.findStatsByTourIds(anyCollection(), any())).thenReturn(List.of());
        when(tourRepository.searchTourIds(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(1L, 2L), PageRequest.of(0, 12), 2));

        PageResponse<TourSummaryResponse> warmCatalog = tourService.listTours(0, 12, plainCriteria());
        assertThat(warmCatalog.content()).extracting(TourSummaryResponse::imageUrl)
                .containsExactly("img/a", "img/b");
        assertThat(tourService.getTour(1L).imageUrls()).containsExactly("img/a");
        assertThat(tourService.getTour(2L).imageUrls()).containsExactly("img/b");
        assertThat(cacheManager.getCache("tours").get("v2:catalog:[1, 2]")).isNotNull();
        assertThat(cacheManager.getCache("tours").get("v2:detail:1")).isNotNull();
        assertThat(cacheManager.getCache("tours").get("v2:detail:2")).isNotNull();

        TourImage moved = image(1L, "img/a", tour(1L, "Tour One"));
        when(tourImageRepository.findById(1L)).thenReturn(Optional.of(moved));
        when(tourRepository.findById(2L)).thenReturn(Optional.of(tour(2L, "Tour Two")));

        TourImageService committingImageService = new TourImageService(
                tourImageRepository, tourRepository, new TourCacheInvalidator(cacheManager));
        TransactionSynchronizationManager.initSynchronization();
        committingImageService.update(1L, new TourImageRequest("img/moved", 2L));

        assertThat(cacheManager.getCache("tours").get("v2:detail:1")).isNotNull();
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        assertThat(cacheManager.getCache("tours").get("v2:catalog:[1, 2]")).isNull();
        assertThat(cacheManager.getCache("tours").get("v2:detail:1")).isNull();
        assertThat(cacheManager.getCache("tours").get("v2:detail:2")).isNull();

        images[0] = null;
        images[1] = "img/moved";

        assertThat(tourService.getTour(1L).imageUrls()).isEmpty();
        assertThat(tourService.getTour(2L).imageUrls()).containsExactly("img/moved");
        assertThat(tourService.listTours(0, 12, plainCriteria()).content())
                .extracting(TourSummaryResponse::imageUrl)
                .containsExactly(null, "img/moved");
    }

    private static Stream<Arguments> successfulMutations() {
        return Stream.of(
                arguments("tour create", (Consumer<TourContentInvalidationTest>) t ->
                        t.tourService.create(tourRequest())),
                arguments("tour update", (Consumer<TourContentInvalidationTest>) t ->
                        t.tourService.update(1L, tourRequest())),
                arguments("tour delete", (Consumer<TourContentInvalidationTest>) t ->
                        t.tourService.delete(1L)),
                arguments("image create", (Consumer<TourContentInvalidationTest>) t ->
                        t.imageService.create(imageRequest())),
                arguments("image update", (Consumer<TourContentInvalidationTest>) t ->
                        t.imageService.update(1L, imageRequest())),
                arguments("image delete", (Consumer<TourContentInvalidationTest>) t ->
                        t.imageService.delete(1L)),
                arguments("accommodation create", (Consumer<TourContentInvalidationTest>) t ->
                        t.accommodationService.create(accommodationRequest())),
                arguments("accommodation update", (Consumer<TourContentInvalidationTest>) t ->
                        t.accommodationService.update(1L, accommodationRequest())),
                arguments("accommodation delete", (Consumer<TourContentInvalidationTest>) t ->
                        t.accommodationService.delete(1L)),
                arguments("guide create", (Consumer<TourContentInvalidationTest>) t ->
                        t.guideService.create(guideRequest())),
                arguments("guide update", (Consumer<TourContentInvalidationTest>) t ->
                        t.guideService.update(1L, guideRequest())),
                arguments("guide delete", (Consumer<TourContentInvalidationTest>) t ->
                        t.guideService.delete(1L)),
                arguments("destination create", (Consumer<TourContentInvalidationTest>) t ->
                        t.destinationService.create(destinationRequest())),
                arguments("destination update", (Consumer<TourContentInvalidationTest>) t ->
                        t.destinationService.update(1L, destinationRequest())),
                arguments("destination delete", (Consumer<TourContentInvalidationTest>) t ->
                        t.destinationService.delete(1L)),
                arguments("vehicle create", (Consumer<TourContentInvalidationTest>) t ->
                        t.vehicleService.create(vehicleRequest())),
                arguments("vehicle update", (Consumer<TourContentInvalidationTest>) t ->
                        t.vehicleService.update(1L, vehicleRequest())),
                arguments("vehicle delete", (Consumer<TourContentInvalidationTest>) t ->
                        t.vehicleService.delete(1L))
        );
    }

    private static Stream<Arguments> failedMutations() {
        return Stream.of(
                arguments("tour update missing id", "Tour not found",
                        (Consumer<TourContentInvalidationTest>) t ->
                                t.tourService.update(99L, tourRequest())),
                arguments("image update missing id", "Tour image not found",
                        (Consumer<TourContentInvalidationTest>) t ->
                                t.imageService.update(99L, imageRequest())),
                arguments("accommodation update missing id", "Accommodation not found",
                        (Consumer<TourContentInvalidationTest>) t ->
                                t.accommodationService.update(99L, accommodationRequest())),
                arguments("guide update missing id", "Tour guide not found",
                        (Consumer<TourContentInvalidationTest>) t ->
                                t.guideService.update(99L, guideRequest())),
                arguments("destination update missing id", "Tourist destination not found",
                        (Consumer<TourContentInvalidationTest>) t ->
                                t.destinationService.update(99L, destinationRequest())),
                arguments("vehicle update missing id", "Travel vehicle not found",
                        (Consumer<TourContentInvalidationTest>) t ->
                                t.vehicleService.update(99L, vehicleRequest()))
        );
    }

    private void stubRepositories() {
        when(tourRepository.save(any(Tour.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tourRepository.findById(1L)).thenReturn(Optional.of(tour()));
        when(tourImageRepository.save(any(TourImage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(tourImageRepository.findById(1L))
                .thenReturn(Optional.of(image(1L, "https://img/1.jpg", tour())));
        when(accommodationRepository.save(any(Accommodation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(accommodationRepository.findById(1L)).thenReturn(Optional.of(accommodation()));
        when(tourGuideRepository.save(any(TourGuide.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(tourGuideRepository.findById(1L)).thenReturn(Optional.of(guide()));
        when(destinationRepository.save(any(TouristDestination.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(destinationRepository.findById(1L)).thenReturn(Optional.of(destination()));
        when(vehicleRepository.save(any(TravelVehicle.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(vehicle()));
    }

    private TourSearchCriteria plainCriteria() {
        return new TourSearchCriteria(null, null, null, null, null, null, null, null,
                TourSearchCriteria.Sort.DEFAULT);
    }

    private static TourRequest tourRequest() {
        return new TourRequest("Tour", "Day 1|Day 2", 3, "OPEN", null, null, null, null, null, null);
    }

    private static TourImageRequest imageRequest() {
        return new TourImageRequest("https://img/1.jpg", 1L);
    }

    private static AccommodationRequest accommodationRequest() {
        return new AccommodationRequest("Hotel", "Da Nang", BigDecimal.ONE, BigDecimal.TEN, "HOTEL");
    }

    private static TourGuideRequest guideRequest() {
        return new TourGuideRequest("Guide", "guide@example.com", "0900000000", 3);
    }

    private static TouristDestinationRequest destinationRequest() {
        return new TouristDestinationRequest("Destination", "Da Nang", "Beach", BigDecimal.ONE);
    }

    private static TravelVehicleRequest vehicleRequest() {
        return new TravelVehicleRequest("Car", 4, BigDecimal.TEN);
    }

    private static Tour tour() {
        return tour(1L, "Tour", "https://img/1.jpg");
    }

    private static Tour tour(long id, String name, String... imageUrls) {
        Tour tour = new Tour();
        tour.setTourId(id);
        tour.setTourName(name);
        tour.setTourDescription("Day 1|Day 2");
        tour.setTourStatus("OPEN");
        tour.setTourDurationDays(3);
        for (String url : imageUrls) {
            if (url == null) {
                continue;
            }
            TourImage image = new TourImage();
            image.setImageUrl(url);
            image.setTour(tour);
            tour.getTourImages().add(image);
        }
        return tour;
    }

    private static TourImage image(long id, String url, Tour tour) {
        TourImage image = new TourImage();
        image.setImageId(id);
        image.setImageUrl(url);
        image.setTour(tour);
        return image;
    }

    private static Accommodation accommodation() {
        Accommodation accommodation = new Accommodation();
        accommodation.setAccommodationId(1L);
        accommodation.setAccommodationName("Hotel");
        return accommodation;
    }

    private static TourGuide guide() {
        TourGuide guide = new TourGuide();
        guide.setTourGuideId(1L);
        guide.setTourGuideName("Guide");
        return guide;
    }

    private static TouristDestination destination() {
        TouristDestination destination = new TouristDestination();
        destination.setDestinationId(1L);
        destination.setDestinationName("Destination");
        return destination;
    }

    private static TravelVehicle vehicle() {
        TravelVehicle vehicle = new TravelVehicle();
        vehicle.setVehicleId(1L);
        vehicle.setVehicleType("Car");
        return vehicle;
    }

    private static TourImageRepository.TourImageProjection imageProjection(Long tourId, String url) {
        TourImageRepository.TourImageProjection projection =
                mock(TourImageRepository.TourImageProjection.class);
        when(projection.getTourId()).thenReturn(tourId);
        when(projection.getImageUrl()).thenReturn(url);
        return projection;
    }

    @Configuration
    @EnableCaching
    static class Config {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("tours");
        }

        @Bean
        TourContentCache tourContentCache(TourRepository tourRepository,
                                          TourImageRepository tourImageRepository) {
            return new TourContentCache(tourRepository, tourImageRepository);
        }

        @Bean
        TourCacheInvalidator invalidator() {
            return mock(TourCacheInvalidator.class);
        }

        @Bean
        TourRepository tourRepository() {
            return mock(TourRepository.class);
        }

        @Bean
        TourImageRepository tourImageRepository() {
            return mock(TourImageRepository.class);
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
        TouristDestinationRepository destinationRepository() {
            return mock(TouristDestinationRepository.class);
        }

        @Bean
        TravelVehicleRepository vehicleRepository() {
            return mock(TravelVehicleRepository.class);
        }

        @Bean
        TourDepartureRepository departureRepository() {
            return mock(TourDepartureRepository.class);
        }

        @Bean
        PromotionRepository promotionRepository() {
            return mock(PromotionRepository.class);
        }
    }
}
