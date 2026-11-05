package com.j2ee.tour.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PaymentCallbackRequest(
        @NotBlank @Size(max = 128) String providerTransactionId,
        @NotNull @Positive BigDecimal amount,
        @NotBlank @Pattern(regexp = "CASH|VNPAY|MOMO|CARD") String method,
        @NotNull Boolean successful,
        @NotBlank @Size(max = 128) String signature
) {
}
