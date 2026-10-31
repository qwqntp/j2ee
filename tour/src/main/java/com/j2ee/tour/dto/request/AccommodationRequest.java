package com.j2ee.tour.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AccommodationRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 255) String location,
        @DecimalMin("0") @DecimalMax("5") BigDecimal rating,
        @DecimalMin("0") BigDecimal pricePerNight,
        @Pattern(regexp = "HOTEL|RESORT|HOMESTAY") String type
) {
}
