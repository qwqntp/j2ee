package com.j2ee.tour.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PaymentCallbackRequest(
        @NotBlank String providerTransactionId,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String method,
        @NotNull Boolean successful
) {
}