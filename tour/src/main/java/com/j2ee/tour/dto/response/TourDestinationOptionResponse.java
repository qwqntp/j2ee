package com.j2ee.tour.dto.response;

import com.j2ee.tour.model.TouristDestination;

public record TourDestinationOptionResponse(Long id, String name) {
    public static TourDestinationOptionResponse from(TouristDestination destination) {
        return new TourDestinationOptionResponse(
                destination.getDestinationId(),
                destination.getDestinationName()
        );
    }
}
