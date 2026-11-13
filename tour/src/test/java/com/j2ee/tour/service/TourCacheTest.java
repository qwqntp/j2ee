package com.j2ee.tour.service;

import com.j2ee.tour.model.Tour;
import com.j2ee.tour.model.TourImage;
import com.j2ee.tour.repository.AccommodationRepository;
import com.j2ee.tour.repository.PromotionRepository;
import com.j2ee.tour.repository.TourDepartureRepository;
import com.j2ee.tour.repository.TourGuideRepository;
import com.j2ee.tour.repository.TourImageRepository;
import com.j2ee.tour.repository.TourRepository;
import com.j2ee.tour.repository.TouristDestinationRepository;
import com.j2ee.tour.repository.TravelVehicleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(TourCacheTest.Config.class)
class TourCacheTest {

    @Autowired
    private TourService tourService;

    @Autowired
    private TourRepository tourRepository;

    @Autowired
    private TourDepartureRepository departureRepository;

    @Test
    void cachesTourDetailUntilDeleteEvictsIt() {
        when(tourRepository.findByTourIdAndTourStatus(1L, "OPEN"))
                .thenReturn(Optional.of(tour()));
        when(departureRepository
                .findByTourTourIdAndDepartureStatusInAndDepartureStartDateAfterOrderByDepartureStartDateAsc(
                        eq(1L), any(), any()))
                .thenReturn(List.of());
        when(tourRepository.findById(1L)).thenReturn(Optional.of(tour()));
        when(departureRepository.existsByTourTourId(1L)).thenReturn(false);

        tourService.getTour(1L);
        tourService.getTour(1L);
        verify(tourRepository, times(1)).findByTourIdAndTourStatus(1L, "OPEN");

        tourService.delete(1L);
        tourService.getTour(1L);
        verify(tourRepository, times(2)).findByTourIdAndTourStatus(1L, "OPEN");
    }

    private Tour tour() {
        Tour tour = new Tour();
        tour.setTourId(1L);
        tour.setTourName("Da Nang - Hoi An");
        tour.setTourDescription("Ngay 1: Da Nang|Ngay 2: Hoi An");
        tour.setTourStatus("OPEN");
        TourImage image = new TourImage();
        image.setImageUrl("https://img/1.jpg");
        image.setTour(tour);
        tour.getTourImages().add(image);
        return tour;
    }

    @Configuration
    @EnableCaching
    static class Config {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("tours");
        }

        @Bean
        TourService tourService(TourRepository tourRepository,
                                TourDepartureRepository departureRepository,
                                AccommodationRepository accommodationRepository,
                                TourGuideRepository tourGuideRepository,
                                PromotionRepository promotionRepository,
                                TouristDestinationRepository destinationRepository,
                                TravelVehicleRepository vehicleRepository,
                                TourImageRepository tourImageRepository) {
            return new TourService(tourRepository, departureRepository,
                    Clock.fixed(Instant.parse("2026-10-26T00:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")),
                    accommodationRepository, tourGuideRepository, promotionRepository,
                    destinationRepository, vehicleRepository, tourImageRepository);
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
