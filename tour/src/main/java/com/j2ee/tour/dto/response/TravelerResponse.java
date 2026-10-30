package com.j2ee.tour.dto.response;

import com.j2ee.tour.model.Traveler;

import java.time.LocalDate;

public record TravelerResponse(
        Long id,
        String name,
        LocalDate dateOfBirth,
        String gender,
        String idDocument
) {
    public static TravelerResponse from(Traveler traveler) {
        return new TravelerResponse(
                traveler.getTravelerId(),
                traveler.getTravelerName(),
                traveler.getTravelerDateOfBirth(),
                traveler.getTravelerGender(),
                traveler.getTravelerIdDocument()
        );
    }
}