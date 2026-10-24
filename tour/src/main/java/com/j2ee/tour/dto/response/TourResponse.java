package com.j2ee.tour.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record TourResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String status,
        LocalDateTime startDate,
        LocalDateTime endDate,
        String meetingPoint,
        Integer remainingSlots,
        Long accommodationId,
        Long tourGuideId,
        Long promotionId,
        List<String> imageUrls,
        List<TourDepartureResponse> departures,
        List<Long> destinationIds,
        List<Long> vehicleIds
) {
}
