package com.j2ee.tour.dto.response;

import com.j2ee.tour.mapper.TourMapper;
import com.j2ee.tour.model.Tour;

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
        List<TourDepartureResponse> departures,
        Integer durationDays,
        Long accommodationId,
        Long tourGuideId,
        Long promotionId,
        List<Long> destinationIds,
        List<Long> vehicleIds
) {
    public TourResponse(
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
            List<TourDepartureResponse> departures) {
        this(id, name, description, status, accommodationName, accommodationRating, accommodationType,
                guideName, guidePhone, imageUrls, itinerary, destinationNames, vehicleTypes, departures,
                null, null, null, null, List.of(), List.of());
    }

    public static TourResponse from(Tour tour) {
        return TourMapper.toDetail(tour, tour.getDepartures() == null ? List.of() : tour.getDepartures());
    }
}
