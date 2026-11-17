package com.j2ee.tour.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record AdminStatsResponse(
        LocalDateTime generatedAt,
        long totalTours,
        long openTours,
        Map<String, Long> bookingsByStatus,
        BigDecimal successfulRevenue,
        long successfulPayments,
        BigDecimal averageSuccessfulPayment,
        long upcomingDepartures,
        long lowCapacityDepartures,
        long soldOutDepartures,
        List<TourPerformance> topTours,
        List<CapacityRisk> capacityRisks
) {
    public record TourPerformance(Long tourId, String tourName, long bookingCount, BigDecimal revenue) {}

    public record CapacityRisk(Long departureId, Long tourId, String tourName,
                               LocalDateTime startDate, int capacity,
                               int remainingSeats, String status) {}
}
