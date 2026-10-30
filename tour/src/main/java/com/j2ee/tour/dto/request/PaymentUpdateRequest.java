package com.j2ee.tour.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record PaymentUpdateRequest(
        @Pattern(regexp = "CASH|VNPAY|MOMO|CARD") String paymentMethod,
        @Size(max = 128) String providerTxnId,
        @Pattern(regexp = "PENDING|SUCCESS|FAILED") String status,
        LocalDateTime paidAt
) {
}
