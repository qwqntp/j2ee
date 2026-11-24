package com.j2ee.tour.service;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.dto.response.AdminStatsResponse;
import com.j2ee.tour.repository.BookingRepository;
import com.j2ee.tour.repository.PaymentRepository;
import com.j2ee.tour.repository.TourDepartureRepository;
import com.j2ee.tour.repository.TourRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminStatsService {

    private static final List<String> BOOKING_STATUSES =
            List.of("PENDING", "PAID", "CONFIRMED", "COMPLETED", "CANCELLED");
    private static final int LOW_CAPACITY_REMAINING = 1;
    private static final int TOP_LIMIT = 5;
    private static final int WINDOW_DAYS = 90;

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final TourRepository tourRepository;
    private final TourDepartureRepository departureRepository;
    private final Clock clock;

    public AdminStatsResponse stats() {
        return build(null, null);
    }

    public AdminStatsResponse stats(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "from must be on or before to");
        }
        return build(from, to);
    }

    private AdminStatsResponse build(LocalDate fromDate, LocalDate toDate) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime from = now;
        LocalDateTime to = now.plusDays(WINDOW_DAYS);
        LocalDateTime paymentFrom = fromDate == null ? from : fromDate.atStartOfDay();
        LocalDateTime paymentTo = toDate == null ? to
                : toDate.plusDays(1).atStartOfDay().minusNanos(1);

        Map<String, Long> bookingsByStatus = new HashMap<>();
        for (String status : BOOKING_STATUSES) {
            bookingsByStatus.put(status, bookingRepository.countByBookingStatus(status));
        }

        long successfulPayments;
        BigDecimal successfulRevenue;
        if (fromDate == null) {
            successfulPayments = paymentRepository.countByPaymentStatus("SUCCESS");
            successfulRevenue = decimal(paymentRepository.sumByPaymentStatus("SUCCESS"));
        } else {
            successfulPayments = paymentRepository.countSuccessfulPaymentsBetween(paymentFrom, paymentTo);
            successfulRevenue = decimal(paymentRepository.sumSuccessfulPaymentsBetween(paymentFrom, paymentTo));
        }
        BigDecimal averageSuccessfulPayment = successfulPayments == 0
                ? BigDecimal.ZERO
                : successfulRevenue.divide(BigDecimal.valueOf(successfulPayments), 2,
                        RoundingMode.HALF_UP);

        List<AdminStatsResponse.TourPerformance> topTours =
                bookingRepository.findTopToursByBookingCount(PageRequest.of(0, TOP_LIMIT)).stream()
                        .limit(TOP_LIMIT)
                        .map(this::toTourPerformance)
                        .toList();

        List<AdminStatsResponse.CapacityRisk> capacityRisks =
                departureRepository.findCapacityRisks(from, LOW_CAPACITY_REMAINING, to).stream()
                        .limit(TOP_LIMIT)
                        .map(this::toCapacityRisk)
                        .toList();

        return new AdminStatsResponse(
                now,
                tourRepository.count(),
                tourRepository.countByTourStatus("OPEN"),
                bookingsByStatus,
                successfulRevenue,
                successfulPayments,
                averageSuccessfulPayment,
                departureRepository.countUpcomingOpen(from, to),
                departureRepository.countUpcomingCapacityAtMost(LOW_CAPACITY_REMAINING, from, to),
                departureRepository.countUpcomingSoldOut(from, to),
                topTours,
                capacityRisks);
    }

    private static BigDecimal decimal(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }

    private AdminStatsResponse.TourPerformance toTourPerformance(Object[] row) {
        return new AdminStatsResponse.TourPerformance(
                (Long) row[0],
                (String) row[1],
                ((Number) row[2]).longValue(),
                decimal(row[3]));
    }

    private AdminStatsResponse.CapacityRisk toCapacityRisk(Object[] row) {
        return new AdminStatsResponse.CapacityRisk(
                ((Number) row[0]).longValue(),
                ((Number) row[1]).longValue(),
                (String) row[2],
                (LocalDateTime) row[3],
                ((Number) row[4]).intValue(),
                ((Number) row[5]).intValue(),
                (String) row[6]);
    }
}
