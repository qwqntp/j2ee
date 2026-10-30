package com.j2ee.tour.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TravelVehicleRequest(
        @NotBlank @Size(max = 255) String type,
        @NotNull @Min(1) Integer capacity,
        @NotNull @DecimalMin("0") BigDecimal rentalPricePerDay
) {
}
