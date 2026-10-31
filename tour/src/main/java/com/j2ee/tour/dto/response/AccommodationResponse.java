package com.j2ee.tour.dto.response;

import com.j2ee.tour.model.Accommodation;

import java.math.BigDecimal;

public record AccommodationResponse(
        Long id,
        String name,
        String location,
        BigDecimal rating,
        BigDecimal pricePerNight,
        String type
) {
    public static AccommodationResponse from(Accommodation accommodation) {
        return new AccommodationResponse(
                accommodation.getAccommodationId(),
                accommodation.getAccommodationName(),
                accommodation.getLocation(),
                accommodation.getRating(),
                accommodation.getPricePerNight(),
                accommodation.getAccommodationType()
        );
    }
}
