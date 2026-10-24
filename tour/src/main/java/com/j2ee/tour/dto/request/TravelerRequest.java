package com.j2ee.tour.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record TravelerRequest(
        @NotBlank(message = "Traveler name is required")
        String name,

        LocalDate dateOfBirth,

        @Pattern(regexp = "MALE|FEMALE|OTHER", message = "Gender must be MALE, FEMALE, or OTHER")
        String gender,

        String idDocument
) {
}
