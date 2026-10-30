package com.j2ee.tour.dto.response;

import java.util.List;

public record TourResponse(
        Long id,
        String name,
        String description,
        Integer durationDays,
        String status,
        Long accommodationId,
        Long tourGuideId,
        Long promotionId,
        List<String> imageUrls,
        List<TourDepartureResponse> departures,
        List<Long> destinationIds,
        List<Long> vehicleIds
) {
}