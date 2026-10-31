package com.j2ee.tour.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateRefundRequest(
        @NotNull @Positive BigDecimal amount,
        String reason
) {
}