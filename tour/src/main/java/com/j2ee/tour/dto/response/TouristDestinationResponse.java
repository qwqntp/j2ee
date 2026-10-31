package com.j2ee.tour.dto.response;

import com.j2ee.tour.model.TouristDestination;

import java.math.BigDecimal;

public record TouristDestinationResponse(
        Long id,
        String name,
        String location,
        String description,
        BigDecimal entryFee
) {
    public static TouristDestinationResponse from(TouristDestination destination) {
        return new TouristDestinationResponse(
                destination.getDestinationId(),
                destination.getDestinationName(),
                destination.getLocation(),
                destination.getDescription(),
                destination.getEntryFee()
        );
    }
}
