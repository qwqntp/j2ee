package com.j2ee.tour.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record BookingTravelerRequest(
        @NotBlank @Size(max = 255) String fullName,
        @Size(max = 20) String phone,
        @Email @Size(max = 255) String email,
        LocalDate dateOfBirth,
        @Pattern(regexp = "MALE|FEMALE|OTHER") String gender,
        @Size(max = 64) String idDocument
) {
}
