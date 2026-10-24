package com.j2ee.tour.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        Long bookingId,
        BigDecimal amount,
        String paymentMethod,
        String providerTxnId,
        String status,
        LocalDateTime paidAt,
        LocalDateTime createdAt
) {
}
