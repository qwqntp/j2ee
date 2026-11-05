package com.j2ee.tour.dto.response;

import com.j2ee.tour.model.Traveler;

import java.time.LocalDate;

public record TravelerResponse(
        Long id,
        String name,
        LocalDate dateOfBirth,
        String gender,
        String idDocument,
        String phone,
        String email
) {
    public static TravelerResponse from(Traveler traveler) {
        return new TravelerResponse(
                traveler.getTravelerId(),
                traveler.getTravelerName(),
                traveler.getTravelerDateOfBirth(),
                traveler.getTravelerGender(),
                traveler.getTravelerIdDocument(),
                traveler.getTravelerPhone(),
                traveler.getTravelerEmail()
        );
    }
}