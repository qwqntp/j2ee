package com.j2ee.tour.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RefundResponse(
        Long id,
        Long paymentId,
        Long bookingId,
        BigDecimal amount,
        String reason,
        String status,
        LocalDateTime requestedAt,
        LocalDateTime processedAt
) {
}
