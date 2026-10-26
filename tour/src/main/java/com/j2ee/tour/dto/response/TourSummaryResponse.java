package com.j2ee.tour.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TourSummaryResponse(
        Long id,
        String name,
        String description,
        String status,
        String imageUrl,
        BigDecimal priceFrom,
        LocalDateTime nextDepartureDate,
        Integer seatsAvailable,
        Integer departureCount
) {
}
