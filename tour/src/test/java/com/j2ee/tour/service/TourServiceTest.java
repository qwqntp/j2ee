package com.j2ee.tour.service;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.response.TourDepartureResponse;
import com.j2ee.tour.dto.response.TourResponse;
import com.j2ee.tour.dto.response.TourSummaryResponse;
import com.j2ee.tour.model.Tour;
import com.j2ee.tour.model.TourDeparture;
import com.j2ee.tour.model.TourImage;
import com.j2ee.tour.repository.TourDepartureRepository;
import com.j2ee.tour.repository.TourRepository;
import com.j2ee.tour.repository.TourStatsProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TourServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-26T00:00:00Z"),
            ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 7, 0);

    private TourRepository tourRepository;
    private TourDepartureRepository departureRepository;
    private TourService tourService;

    @BeforeEach
    void setUp() {
        tourRepository = mock(TourRepository.class);
        departureRepository = mock(TourDepartureRepository.class);
        tourService = new TourService(tourRepository, departureRepository, CLOCK);
    }

    @Test
    void listToursMapsSummaryAndStats() {
        Tour tour = tour();
        Page<Tour> page = new PageImpl<>(List.of(tour), PageRequest.of(0, 12), 1);
        TourStatsProjection stats = stats();
        when(tourRepository.findByTourStatus(eq("OPEN"), any(Pageable.class))).thenReturn(page);
        when(departureRepository.findStatsByTourIds(anyCollection(), any(LocalDateTime.class)))
                .thenReturn(List.of(stats));

        PageResponse<TourSummaryResponse> result = tourService.listTours(0, 12, null);

        assertThat(result.content()).hasSize(1);
        TourSummaryResponse summary = result.content().getFirst();
        assertThat(summary.id()).isEqualTo(1L);
        assertThat(summary.name()).isEqualTo("Da Nang - Hoi An");
        assertThat(summary.description()).isEqualTo("Ngay 1: Da Nang");
        assertThat(summary.imageUrl()).isEqualTo("https://img/1.jpg");
        assertThat(summary.priceFrom()).isEqualByComparingTo("4500000");
        assertThat(summary.nextDepartureDate()).isEqualTo(LocalDateTime.of(2026, 11, 1, 8, 0));
        assertThat(summary.seatsAvailable()).isEqualTo(28);
        assertThat(summary.departureCount()).isEqualTo(1);
    }

    @Test
    void listToursSanitizesSearchWildcards() {
        Page<Tour> empty = new PageImpl<>(List.of(), PageRequest.of(0, 12), 0);
        when(tourRepository.findByTourStatusAndTourNameContainingIgnoreCase(
                eq("OPEN"), eq("DaNang"), any(Pageable.class))).thenReturn(empty);

        PageResponse<TourSummaryResponse> result = tourService.listTours(0, 12, "  Da%Nang_ ");

        assertThat(result.content()).isEmpty();
        verify(tourRepository).findByTourStatusAndTourNameContainingIgnoreCase(
                eq("OPEN"), eq("DaNang"), any(Pageable.class));
        verify(departureRepository, never()).findStatsByTourIds(anyCollection(), any(LocalDateTime.class));
    }

    @Test
    void listToursUsesDefaultsWhenNoUpcomingDeparturesExist() {
        Page<Tour> page = new PageImpl<>(List.of(tour()), PageRequest.of(0, 12), 1);
        when(tourRepository.findByTourStatus(eq("OPEN"), any(Pageable.class))).thenReturn(page);
        when(departureRepository.findStatsByTourIds(anyCollection(), any(LocalDateTime.class)))
                .thenReturn(List.of());

        PageResponse<TourSummaryResponse> result = tourService.listTours(0, 12, null);

        TourSummaryResponse summary = result.content().getFirst();
        assertThat(summary.priceFrom()).isNull();
        assertThat(summary.nextDepartureDate()).isNull();
        assertThat(summary.seatsAvailable()).isZero();
        assertThat(summary.departureCount()).isZero();
    }

    @Test
    void getTourThrowsNotFoundWhenTourIsNotPublic() {
        when(tourRepository.findByTourIdAndTourStatus(99L, "OPEN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tourService.getTour(99L))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getTourUsesUpcomingOpenAndFullDeparturesQuery() {
        Tour tour = tour();
        List<TourDeparture> departures = List.of(
                departure(1L, "OPEN", LocalDateTime.of(2026, 11, 1, 8, 0)),
                departure(2L, "FULL", LocalDateTime.of(2026, 11, 5, 8, 0)));
        when(tourRepository.findByTourIdAndTourStatus(1L, "OPEN")).thenReturn(Optional.of(tour));
        when(departureRepository
                .findByTourTourIdAndDepartureStatusInAndDepartureStartDateAfterOrderByDepartureStartDateAsc(
                        eq(1L), eq(List.of("OPEN", "FULL")), any(LocalDateTime.class)))
                .thenReturn(departures);

        TourResponse result = tourService.getTour(1L);

        assertThat(result.departures())
                .extracting(TourDepartureResponse::id)
                .containsExactly(1L, 2L);
        assertThat(result.description()).isEqualTo("Ngay 1: Da Nang");
        assertThat(result.itinerary()).containsExactly("Ngay 1: Da Nang", "Ngay 2: Hoi An");
        verify(departureRepository)
                .findByTourTourIdAndDepartureStatusInAndDepartureStartDateAfterOrderByDepartureStartDateAsc(
                        eq(1L), eq(List.of("OPEN", "FULL")), eq(NOW));
    }

    @Test
    void getDeparturesQueriesUpcomingOpenAndFullDepartures() {
        when(tourRepository.findByTourIdAndTourStatus(1L, "OPEN")).thenReturn(Optional.of(tour()));
        when(departureRepository
                .findByTourTourIdAndDepartureStatusInAndDepartureStartDateAfterOrderByDepartureStartDateAsc(
                        eq(1L), eq(List.of("OPEN", "FULL")), any(LocalDateTime.class)))
                .thenReturn(List.of(departure(1L, "OPEN", LocalDateTime.of(2026, 11, 1, 8, 0))));

        List<TourDepartureResponse> result = tourService.getDepartures(1L);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().seatsAvailable()).isEqualTo(28);
        verify(departureRepository)
                .findByTourTourIdAndDepartureStatusInAndDepartureStartDateAfterOrderByDepartureStartDateAsc(
                        eq(1L), eq(List.of("OPEN", "FULL")), eq(NOW));
    }

    @Test
    void getDeparturesThrowsNotFoundWhenTourIsNotPublic() {
        when(tourRepository.findByTourIdAndTourStatus(99L, "OPEN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tourService.getDepartures(99L))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
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

    private TourDeparture departure(Long id, String status, LocalDateTime startDate) {
        TourDeparture departure = new TourDeparture();
        departure.setDepartureId(id);
        departure.setDepartureStatus(status);
        departure.setDepartureStartDate(startDate);
        departure.setDepartureEndDate(startDate.plusDays(2));
        departure.setDepartureCapacity(30);
        departure.setDepartureBookedSlots(2);
        departure.setDeparturePrice(new BigDecimal("4500000"));
        departure.setDepartureCreatedAt(startDate.minusDays(1));
        return departure;
    }

    private TourStatsProjection stats() {
        TourStatsProjection stats = mock(TourStatsProjection.class);
        when(stats.getTourId()).thenReturn(1L);
        when(stats.getPriceFrom()).thenReturn(new BigDecimal("4500000"));
        when(stats.getNextDepartureDate()).thenReturn(LocalDateTime.of(2026, 11, 1, 8, 0));
        when(stats.getSeatsAvailable()).thenReturn(28L);
        when(stats.getDepartureCount()).thenReturn(1L);
        return stats;
    }
}
