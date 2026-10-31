package com.j2ee.tour.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentCreateRequest(
        @NotNull(message = "Booking ID is required")
        Long bookingId,

        @NotNull(message = "Payment amount is required")
        @DecimalMin(value = "0.01", message = "Payment amount must be greater than 0")
        BigDecimal amount,

        @NotNull(message = "Payment method is required")
        @Pattern(regexp = "CASH|VNPAY|MOMO|CARD", message = "Payment method must be CASH, VNPAY, MOMO, or CARD")
        String paymentMethod,

        @Size(max = 128)
        String providerTxnId,

        @Pattern(regexp = "PENDING|SUCCESS|FAILED")
        String status,

        LocalDateTime paidAt
) {
}
