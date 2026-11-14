package com.j2ee.tour;

import com.j2ee.tour.dto.request.TourDepartureRequest;
import com.j2ee.tour.dto.request.TourRequest;
import com.j2ee.tour.model.Accommodation;
import com.j2ee.tour.model.Tour;
import com.j2ee.tour.model.TourDeparture;
import com.j2ee.tour.model.TourGuide;
import com.j2ee.tour.repository.AccommodationRepository;
import com.j2ee.tour.repository.TourDepartureRepository;
import com.j2ee.tour.repository.TourGuideRepository;
import com.j2ee.tour.repository.TourRepository;
import com.j2ee.tour.service.TourDepartureService;
import com.j2ee.tour.service.TourService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GuideOverlapIntegrationTests {

    private static final LocalDateTime MAR_10 = LocalDateTime.of(2026, 3, 10, 0, 0);
    private static final LocalDateTime MAR_11 = LocalDateTime.of(2026, 3, 11, 0, 0);
    private static final LocalDateTime MAR_12 = LocalDateTime.of(2026, 3, 12, 0, 0);
    private static final LocalDateTime MAR_13 = LocalDateTime.of(2026, 3, 13, 0, 0);
    private static final LocalDateTime MAR_14 = LocalDateTime.of(2026, 3, 14, 0, 0);
    private static final LocalDateTime MAR_20 = LocalDateTime.of(2026, 3, 20, 0, 0);
    private static final LocalDateTime MAR_21 = LocalDateTime.of(2026, 3, 21, 0, 0);
    private static final LocalDateTime MAR_22 = LocalDateTime.of(2026, 3, 22, 0, 0);
    private static final LocalDateTime MAR_23 = LocalDateTime.of(2026, 3, 23, 0, 0);

    @Autowired private TourService tourService;
    @Autowired private TourDepartureService tourDepartureService;

    @Autowired private AccommodationRepository accommodationRepository;
    @Autowired private TourGuideRepository tourGuideRepository;
    @Autowired private TourRepository tourRepository;
    @Autowired private TourDepartureRepository tourDepartureRepository;

    private Accommodation accommodation;
    private TourGuide guide;
    private Tour tourA;   // tour led by `guide`
    private Tour tourA2;  // second tour also led by `guide`
    private Tour tourB;   // tour with no guide (assign one later)

    @BeforeEach
    void setUp() {
        accommodation = new Accommodation();
        accommodation.setAccommodationName("Hotel");
        accommodation.setLocation("HCM");
        accommodation = accommodationRepository.save(accommodation);

        guide = new TourGuide();
        guide.setTourGuideName("Guide");
        guide.setTourGuideExperienceYears(1);
        guide = tourGuideRepository.save(guide);

        tourA = tourRepository.save(makeTour("TourA", guide));
        tourA2 = tourRepository.save(makeTour("TourA2", guide));
        tourB = tourRepository.save(makeTour("TourB", null));
    }

    private Tour makeTour(String name, TourGuide tourGuide) {
        Tour tour = new Tour();
        tour.setTourName(name);
        tour.setTourStatus("OPEN");
        tour.setAccommodation(accommodation);
        tour.setTourGuide(tourGuide);
        return tour;
    }

    /** Arrange a departure directly in the DB, bypassing service validation/trigger semantics. */
    private TourDeparture givenDeparture(Tour tour, LocalDateTime start, LocalDateTime end, String status) {
        TourDeparture departure = new TourDeparture();
        departure.setTour(tour);
        departure.setDepartureStartDate(start);
        departure.setDepartureEndDate(end);
        departure.setDepartureCapacity(10);
        departure.setDepartureBookedSlots(0);
        departure.setDeparturePrice(new BigDecimal("500.00"));
        departure.setDepartureStatus(status);
        departure.setDepartureCreatedAt(LocalDateTime.now());
        return tourDepartureRepository.save(departure);
    }

    private TourDepartureRequest departureRequest(Long tourId, LocalDateTime start, LocalDateTime end, String status) {
        return new TourDepartureRequest(tourId, start, end, 10, 0, new BigDecimal("500.00"), "meeting point", status);
    }

    private TourRequest tourRequest(String name, Long guideId) {
        return new TourRequest(name, "desc", 3, "OPEN", accommodation.getAccommodationId(), guideId,
                null, List.of(), List.of(), List.of());
    }

    @Test
    void createRejectsOverlappingDepartureForSameGuideOnAnotherTour() {
        givenDeparture(tourA, MAR_10, MAR_12, "OPEN");

        assertThrows(IllegalStateException.class,
                () -> tourDepartureService.create(departureRequest(tourA2.getTourId(), MAR_11, MAR_13, "OPEN")));
    }

    @Test
    void createRejectsOverlappingDepartureForSameGuideOnSameTour() {
        givenDeparture(tourA, MAR_10, MAR_12, "OPEN");

        assertThrows(IllegalStateException.class,
                () -> tourDepartureService.create(departureRequest(tourA.getTourId(), MAR_11, MAR_13, "OPEN")));
    }

    @Test
    void createAllowsBackToBackDeparturesForSameGuide() {
        givenDeparture(tourA, MAR_10, MAR_12, "OPEN");

        assertDoesNotThrow(
                () -> tourDepartureService.create(departureRequest(tourA2.getTourId(), MAR_12, MAR_14, "OPEN")));
    }

    @Test
    void createAllowsOverlapWithCancelledExistingDeparture() {
        givenDeparture(tourA, MAR_10, MAR_12, "CANCELLED");

        assertDoesNotThrow(
                () -> tourDepartureService.create(departureRequest(tourA2.getTourId(), MAR_11, MAR_13, "OPEN")));
    }

    @Test
    void closedDepartureBlocksGuide() {
        givenDeparture(tourA, MAR_10, MAR_12, "CLOSED");

        assertThrows(IllegalStateException.class,
                () -> tourDepartureService.create(departureRequest(tourA2.getTourId(), MAR_11, MAR_13, "OPEN")));
    }

    @Test
    void updateRejectsDatesMovedIntoOverlap() {
        givenDeparture(tourA, MAR_10, MAR_12, "OPEN");
        TourDeparture other = givenDeparture(tourA2, MAR_20, MAR_22, "OPEN");

        assertThrows(IllegalStateException.class,
                () -> tourDepartureService.update(other.getDepartureId(),
                        departureRequest(tourA2.getTourId(), MAR_11, MAR_13, "OPEN")));
    }

    @Test
    void updateAllowsDatesMovedWithoutOverlap() {
        givenDeparture(tourA, MAR_10, MAR_12, "OPEN");
        TourDeparture other = givenDeparture(tourA2, MAR_20, MAR_22, "OPEN");

        assertDoesNotThrow(() -> tourDepartureService.update(other.getDepartureId(),
                departureRequest(tourA2.getTourId(), MAR_21, MAR_23, "OPEN")));
    }

    @Test
    void assigningFreeGuideSucceeds() {
        givenDeparture(tourA, MAR_20, MAR_22, "OPEN");
        givenDeparture(tourB, MAR_11, MAR_13, "OPEN");

        assertDoesNotThrow(() -> tourService.update(tourB.getTourId(),
                tourRequest("TourB", guide.getTourGuideId())));
    }

    @Test
    void assigningGuideRejectedWhenTourDeparturesOverlapGuidedTrips() {
        givenDeparture(tourA, MAR_10, MAR_12, "OPEN");
        givenDeparture(tourB, MAR_11, MAR_13, "OPEN");

        assertThrows(IllegalStateException.class, () -> tourService.update(tourB.getTourId(),
                tourRequest("TourB", guide.getTourGuideId())));
    }

    @Test
    void assigningGuideSucceedsWhenExistingDeparturesAreCancelled() {
        givenDeparture(tourA, MAR_10, MAR_12, "CANCELLED");
        givenDeparture(tourB, MAR_11, MAR_13, "OPEN");

        assertDoesNotThrow(() -> tourService.update(tourB.getTourId(),
                tourRequest("TourB", guide.getTourGuideId())));
    }

    @Test
    void triggerRejectsOverlappingInsertBypassingService() {
        givenDeparture(tourA, MAR_10, MAR_12, "OPEN");

        assertThrows(DataIntegrityViolationException.class,
                () -> givenDeparture(tourA2, MAR_11, MAR_13, "OPEN"));
    }
}
