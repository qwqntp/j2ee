package com.j2ee.tour.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record TourResponse(
        Long id,
        String name,
        String description,
        String status,
        String accommodationName,
        BigDecimal accommodationRating,
        String accommodationType,
        String guideName,
        String guidePhone,
        List<String> imageUrls,
        List<String> itinerary,
        List<String> destinationNames,
        List<String> vehicleTypes,
        List<TourDepartureResponse> departures
) {
}
