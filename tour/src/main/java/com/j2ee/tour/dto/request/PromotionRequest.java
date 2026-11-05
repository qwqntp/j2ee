package com.j2ee.tour.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
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
        String description,
        @Size(max = 64) String code,
        Boolean active,
        @Min(0) Integer usageLimit,
        @DecimalMin("0") BigDecimal minOrderAmount
) {
    public PromotionRequest(String name, BigDecimal discountPercentage, LocalDate startDate,
                            LocalDate endDate, String description) {
        this(name, discountPercentage, startDate, endDate, description, null, null, null, null);
    }
}
