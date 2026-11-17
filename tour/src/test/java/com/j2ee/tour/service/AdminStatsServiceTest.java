package com.j2ee.tour.service;

import com.j2ee.tour.dto.response.AdminStatsResponse;
import com.j2ee.tour.repository.BookingRepository;
import com.j2ee.tour.repository.PaymentRepository;
import com.j2ee.tour.repository.TourDepartureRepository;
import com.j2ee.tour.repository.TourRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminStatsServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-17T00:00:00Z"),
            ZoneId.of("Asia/Ho_Chi_Minh"));

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private TourRepository tourRepository;

    @Mock
    private TourDepartureRepository departureRepository;

    private AdminStatsService service;

    @BeforeEach
    void setUp() {
        service = new AdminStatsService(bookingRepository, paymentRepository,
                tourRepository, departureRepository, CLOCK);
    }

    @Test
    void statsReturnsExplicitZeroState() {
        when(bookingRepository.count()).thenReturn(0L);
        when(bookingRepository.countByBookingStatus("PENDING")).thenReturn(0L);
        when(bookingRepository.countByBookingStatus("PAID")).thenReturn(0L);
        when(bookingRepository.countByBookingStatus("CONFIRMED")).thenReturn(0L);
        when(bookingRepository.countByBookingStatus("COMPLETED")).thenReturn(0L);
        when(bookingRepository.countByBookingStatus("CANCELLED")).thenReturn(0L);
        when(paymentRepository.countByPaymentStatus("SUCCESS")).thenReturn(0L);
        when(paymentRepository.sumByPaymentStatus("SUCCESS")).thenReturn(null);
        when(tourRepository.count()).thenReturn(0L);
        when(tourRepository.countByTourStatus("OPEN")).thenReturn(0L);
        when(departureRepository.countUpcomingOpen(any(), any())).thenReturn(0L);
        when(departureRepository.countUpcomingCapacityAtMost(eq(1), any(), any())).thenReturn(0L);
        when(departureRepository.countUpcomingSoldOut(any(), any())).thenReturn(0L);
        when(bookingRepository.findTopToursByBookingCount(any())).thenReturn(List.of());
        when(departureRepository.findCapacityRisks(any(), anyInt(), any())).thenReturn(List.of());

        AdminStatsResponse stats = service.stats();

        assertThat(stats.successfulRevenue()).isEqualByComparingTo("0");
        assertThat(stats.averageSuccessfulPayment()).isEqualByComparingTo("0");
        assertThat(stats.bookingsByStatus()).containsEntry("PENDING", 0L);
        assertThat(stats.bookingsByStatus()).containsEntry("PAID", 0L);
        assertThat(stats.bookingsByStatus()).containsEntry("CONFIRMED", 0L);
        assertThat(stats.bookingsByStatus()).containsEntry("COMPLETED", 0L);
        assertThat(stats.bookingsByStatus()).containsEntry("CANCELLED", 0L);
        assertThat(stats.topTours()).isEmpty();
        assertThat(stats.capacityRisks()).isEmpty();
    }

    @Test
    void statsReturnsPopulatedValues() {
        when(bookingRepository.countByBookingStatus("PENDING")).thenReturn(2L);
        when(bookingRepository.countByBookingStatus("PAID")).thenReturn(1L);
        when(bookingRepository.countByBookingStatus("CONFIRMED")).thenReturn(0L);
        when(bookingRepository.countByBookingStatus("COMPLETED")).thenReturn(1L);
        when(bookingRepository.countByBookingStatus("CANCELLED")).thenReturn(0L);
        when(paymentRepository.countByPaymentStatus("SUCCESS")).thenReturn(2L);
        when(paymentRepository.sumByPaymentStatus("SUCCESS")).thenReturn(new BigDecimal("150.00"));
        when(tourRepository.count()).thenReturn(4L);
        when(tourRepository.countByTourStatus("OPEN")).thenReturn(1L);
        when(departureRepository.countUpcomingOpen(any(), any())).thenReturn(7L);
        when(departureRepository.countUpcomingCapacityAtMost(eq(1), any(), any())).thenReturn(3L);
        when(departureRepository.countUpcomingSoldOut(any(), any())).thenReturn(1L);
        when(bookingRepository.findTopToursByBookingCount(any())).thenReturn(List.<Object[]>of(
                new Object[]{1L, "Da Nang", 3L, new BigDecimal("300.00")}));
        when(departureRepository.findCapacityRisks(any(), anyInt(), any())).thenReturn(List.<Object[]>of(
                new Object[]{10L, 1L, "Da Nang",
                        LocalDateTime.of(2026, 11, 20, 8, 0), 20, -2, "FULL"}));

        AdminStatsResponse stats = service.stats();

        assertThat(stats.generatedAt())
                .isEqualTo(LocalDateTime.of(2026, 11, 17, 7, 0));
        assertThat(stats.totalTours()).isEqualTo(4L);
        assertThat(stats.openTours()).isEqualTo(1L);
        assertThat(stats.bookingsByStatus()).containsEntry("PENDING", 2L)
                .containsEntry("PAID", 1L)
                .containsEntry("CONFIRMED", 0L)
                .containsEntry("COMPLETED", 1L)
                .containsEntry("CANCELLED", 0L);
        assertThat(stats.successfulPayments()).isEqualTo(2L);
        assertThat(stats.successfulRevenue()).isEqualByComparingTo("150");
        assertThat(stats.averageSuccessfulPayment()).isEqualByComparingTo("75");
        assertThat(stats.upcomingDepartures()).isEqualTo(7L);
        assertThat(stats.lowCapacityDepartures()).isEqualTo(3L);
        assertThat(stats.soldOutDepartures()).isEqualTo(1L);

        assertThat(stats.topTours()).hasSize(1);
        AdminStatsResponse.TourPerformance top = stats.topTours().get(0);
        assertThat(top.tourId()).isEqualTo(1L);
        assertThat(top.tourName()).isEqualTo("Da Nang");
        assertThat(top.bookingCount()).isEqualTo(3L);
        assertThat(top.revenue()).isEqualByComparingTo("300");

        assertThat(stats.capacityRisks()).hasSize(1);
        AdminStatsResponse.CapacityRisk risk = stats.capacityRisks().get(0);
        assertThat(risk.departureId()).isEqualTo(10L);
        assertThat(risk.tourId()).isEqualTo(1L);
        assertThat(risk.tourName()).isEqualTo("Da Nang");
        assertThat(risk.startDate()).isEqualTo(LocalDateTime.of(2026, 11, 20, 8, 0));
        assertThat(risk.capacity()).isEqualTo(20);
        assertThat(risk.remainingSeats()).isEqualTo(-2);
        assertThat(risk.status()).isEqualTo("FULL");
    }
}
