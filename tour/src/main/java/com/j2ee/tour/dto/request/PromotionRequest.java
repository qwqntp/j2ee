package com.j2ee.tour.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PromotionRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal discountPercentage,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        String description
) {
}
