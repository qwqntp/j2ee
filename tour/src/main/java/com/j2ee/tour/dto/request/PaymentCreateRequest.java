package com.j2ee.tour.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PaymentCreateRequest(
        @NotNull(message = "Booking ID is required")
        Long bookingId,

        @NotBlank(message = "Payment method is required")
        @Pattern(regexp = "CASH|VNPAY|MOMO|CARD", message = "Payment method must be CASH, VNPAY, MOMO, or CARD")
        String paymentMethod,

        @Size(max = 128)
        String providerTxnId
) {
}
