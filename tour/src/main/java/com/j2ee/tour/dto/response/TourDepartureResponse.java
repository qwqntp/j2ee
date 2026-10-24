package com.j2ee.tour.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TourDepartureResponse(
        Long id,
        Long tourId,
        LocalDateTime startDate,
        LocalDateTime endDate,
        Integer capacity,
        Integer bookedSlots,
        Integer seatsAvailable,
        BigDecimal price,
        String meetingPoint,
        String status,
        LocalDateTime createdAt
) {
}
