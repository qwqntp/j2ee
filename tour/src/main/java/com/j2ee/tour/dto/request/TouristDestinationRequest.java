package com.j2ee.tour.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TouristDestinationRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String location,
        String description,
        @DecimalMin("0") BigDecimal entryFee
) {
}
