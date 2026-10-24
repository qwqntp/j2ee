package com.j2ee.tour.dto.response;

import java.time.LocalDate;

public record TravelerResponse(
        Long id,
        String name,
        LocalDate dateOfBirth,
        String gender,
        String idDocument
) {
}
