package com.j2ee.tour;

import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.TourSearchCriteria;
import com.j2ee.tour.dto.response.TourSummaryResponse;
import com.j2ee.tour.model.Tour;
import com.j2ee.tour.model.TourDeparture;
import com.j2ee.tour.model.TouristDestination;
import com.j2ee.tour.repository.TourDepartureRepository;
import com.j2ee.tour.repository.TourRepository;
import com.j2ee.tour.repository.TouristDestinationRepository;
import com.j2ee.tour.service.TourService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Database-backed coverage for the public catalogue filters. Every query is scoped by a unique
 * keyword prefix so the shared database's existing tours cannot affect ordering assertions.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TourSearchIntegrationTests {

    @Autowired private TourService tourService;
    @Autowired private TourRepository tourRepository;
    @Autowired private TourDepartureRepository departureRepository;
    @Autowired private TouristDestinationRepository destinationRepository;

    private String prefix;

    @BeforeEach
    void setUp() {
        prefix = "B2SEARCH-" + UUID.randomUUID();
    }

    @Test
    void priceRangeKeepsOnlyToursWithAMatchingUpcomingDeparture() {
        Tour cheap = tour("cheap", 3);
        departure(cheap, now().plusDays(10), "100.00");
        Tour expensive = tour("expensive", 3);
        departure(expensive, now().plusDays(10), "500.00");
        tour("no-departures", 3);

        PageResponse<TourSummaryResponse> result = search(new TourSearchCriteria(
                prefix, null, new BigDecimal("400"), null,
                null, null, null, null, TourSearchCriteria.Sort.DEFAULT));

        assertThat(ids(result)).containsExactly(expensive.getTourId());
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void priceAndDateFiltersMustMatchTheSameDeparture() {
        Tour mixed = tour("mixed-departures", 3);
        departure(mixed, now().plusDays(5), "100.00");
        departure(mixed, now().plusDays(200), "500.00");

        PageResponse<TourSummaryResponse> combined = search(new TourSearchCriteria(
                prefix, null, new BigDecimal("400"), null,
                null, LocalDate.now().plusDays(30), null, null, TourSearchCriteria.Sort.DEFAULT));
        PageResponse<TourSummaryResponse> priceOnly = search(new TourSearchCriteria(
                prefix, null, new BigDecimal("400"), null,
                null, null, null, null, TourSearchCriteria.Sort.DEFAULT));

        assertThat(combined.content()).isEmpty();
        assertThat(priceOnly.content()).hasSize(1);
    }

    @Test
    void startToIsInclusiveWholeDay() {
        LocalDate day = LocalDate.now().plusDays(10);
        Tour tour = tour("on-the-day", 3);
        departure(tour, day.atTime(8, 0), "300.00");

        PageResponse<TourSummaryResponse> result = search(new TourSearchCriteria(
                prefix, null, null, null, day, day, null, null, TourSearchCriteria.Sort.DEFAULT));

        assertThat(ids(result)).containsExactly(tour.getTourId());
    }

    @Test
    void destinationFilterReturnsMatchingToursAndCorrectTotal() {
        TouristDestination destination = new TouristDestination();
        destination.setDestinationName("B2 destination " + UUID.randomUUID());
        destination = destinationRepository.save(destination);

        Tour first = tour("destination-one", 3);
        first.getDestinations().add(destination);
        tourRepository.save(first);
        Tour second = tour("destination-two", 3);
        second.getDestinations().add(destination);
        tourRepository.save(second);
        tour("destination-missing", 3);

        PageResponse<TourSummaryResponse> result = search(new TourSearchCriteria(
                prefix, destination.getDestinationId(), null, null,
                null, null, null, null, TourSearchCriteria.Sort.DEFAULT));

        assertThat(ids(result)).containsExactly(first.getTourId(), second.getTourId());
        assertThat(result.totalElements()).isEqualTo(2);
    }

    @Test
    void unknownDestinationReturnsEmptyPage() {
        PageResponse<TourSummaryResponse> result = search(new TourSearchCriteria(
                prefix, 999_999_999L, null, null,
                null, null, null, null, TourSearchCriteria.Sort.DEFAULT));

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    void durationFilterUsesTourDurationDays() {
        Tour shortTour = tour("short", 2);
        Tour longTour = tour("long", 9);

        PageResponse<TourSummaryResponse> result = search(new TourSearchCriteria(
                prefix, null, null, null, null, null, 5, null, TourSearchCriteria.Sort.DEFAULT));

        assertThat(ids(result)).containsExactly(longTour.getTourId());
        assertThat(shortTour.getTourId()).isNotIn(ids(result));
    }

    @Test
    void minDaysAloneDoesNotHideToursWithoutDepartures() {
        Tour noDepartures = tour("no-departures-duration", 3);

        PageResponse<TourSummaryResponse> result = search(new TourSearchCriteria(
                prefix, null, null, null, null, null, 1, null, TourSearchCriteria.Sort.DEFAULT));

        assertThat(ids(result)).containsExactly(noDepartures.getTourId());
    }

    @Test
    void priceAscSortsByDisplayedPriceWithToursWithoutPriceLast() {
        Tour expensive = tour("sort-expensive", 3);
        departure(expensive, now().plusDays(10), "900.00");
        Tour noDepartures = tour("sort-no-departures", 3);
        Tour cheap = tour("sort-cheap", 3);
        departure(cheap, now().plusDays(10), "100.00");

        PageResponse<TourSummaryResponse> result = search(new TourSearchCriteria(
                prefix, null, null, null, null, null, null, null,
                TourSearchCriteria.Sort.PRICE_ASC));

        assertThat(ids(result)).containsExactly(
                cheap.getTourId(), expensive.getTourId(), noDepartures.getTourId());
        assertThat(result.content().getLast().priceFrom()).isNull();
    }

    @Test
    void priceDescSortsByDisplayedPriceWithToursWithoutPriceLast() {
        Tour cheap = tour("desc-cheap", 3);
        departure(cheap, now().plusDays(10), "100.00");
        Tour expensive = tour("desc-expensive", 3);
        departure(expensive, now().plusDays(10), "900.00");
        Tour noDepartures = tour("desc-no-departures", 3);

        PageResponse<TourSummaryResponse> result = search(new TourSearchCriteria(
                prefix, null, null, null, null, null, null, null,
                TourSearchCriteria.Sort.PRICE_DESC));

        assertThat(ids(result)).containsExactly(
                expensive.getTourId(), cheap.getTourId(), noDepartures.getTourId());
    }

    @Test
    void dateAscSortsByNextDepartureWithToursWithoutDepartureLast() {
        Tour later = tour("later", 3);
        departure(later, now().plusDays(60), "300.00");
        Tour sooner = tour("sooner", 3);
        departure(sooner, now().plusDays(20), "300.00");
        Tour noDepartures = tour("date-no-departures", 3);

        PageResponse<TourSummaryResponse> result = search(new TourSearchCriteria(
                prefix, null, null, null, null, null, null, null,
                TourSearchCriteria.Sort.DATE_ASC));

        assertThat(ids(result)).containsExactly(
                sooner.getTourId(), later.getTourId(), noDepartures.getTourId());
    }

    @Test
    void newestSortIsTourIdDescendingAndDefaultIsAscending() {
        Tour first = tour("newest-one", 3);
        Tour second = tour("newest-two", 3);

        PageResponse<TourSummaryResponse> newest = search(new TourSearchCriteria(
                prefix, null, null, null, null, null, null, null,
                TourSearchCriteria.Sort.NEWEST));
        PageResponse<TourSummaryResponse> defaultOrder = search(new TourSearchCriteria(
                prefix, null, null, null, null, null, null, null,
                TourSearchCriteria.Sort.DEFAULT));

        assertThat(ids(newest)).containsExactly(second.getTourId(), first.getTourId());
        assertThat(ids(defaultOrder)).containsExactly(first.getTourId(), second.getTourId());
    }

    @Test
    void departureFiltersOnlyConsiderUpcomingOpenAndFullDepartures() {
        Tour past = tour("past-only", 3);
        departure(past, now().minusDays(5), "100.00");
        Tour cancelled = tour("cancelled-only", 3);
        departure(cancelled, now().plusDays(10), "100.00", "CANCELLED");

        PageResponse<TourSummaryResponse> result = search(new TourSearchCriteria(
                prefix, null, null, new BigDecimal("50"), null, null, null, null,
                TourSearchCriteria.Sort.DEFAULT));

        assertThat(result.content()).isEmpty();
    }

    private PageResponse<TourSummaryResponse> search(TourSearchCriteria criteria) {
        return tourService.listTours(0, 50, criteria);
    }

    private List<Long> ids(PageResponse<TourSummaryResponse> page) {
        return page.content().stream().map(TourSummaryResponse::id).toList();
    }

    private LocalDateTime now() {
        return LocalDateTime.now();
    }

    private Tour tour(String label, Integer durationDays) {
        Tour tour = new Tour();
        tour.setTourName(prefix + " " + label);
        tour.setTourStatus("OPEN");
        tour.setTourDurationDays(durationDays);
        return tourRepository.save(tour);
    }

    private TourDeparture departure(Tour tour, LocalDateTime start, String price) {
        return departure(tour, start, price, "OPEN");
    }

    private TourDeparture departure(Tour tour, LocalDateTime start, String price, String status) {
        TourDeparture departure = new TourDeparture();
        departure.setTour(tour);
        departure.setDepartureStartDate(start);
        departure.setDepartureEndDate(start.plusDays(2));
        departure.setDepartureCapacity(10);
        departure.setDepartureBookedSlots(0);
        departure.setDeparturePrice(new BigDecimal(price));
        departure.setDepartureStatus(status);
        departure.setDepartureCreatedAt(LocalDateTime.now());
        return departureRepository.save(departure);
    }
}
