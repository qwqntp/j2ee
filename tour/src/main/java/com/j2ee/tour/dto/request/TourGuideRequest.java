package com.j2ee.tour.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TourGuideRequest(
        @NotBlank @Size(max = 255) String name,
        @Email @Size(max = 255) String email,
        @Size(max = 20) String phone,
        @NotNull @Min(0) Integer experienceYears
) {
}
