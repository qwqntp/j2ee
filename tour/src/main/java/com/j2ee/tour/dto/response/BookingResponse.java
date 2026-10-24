package com.j2ee.tour.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record BookingResponse(
        Long id,
        String bookingCode,
        Long departureId,
        LocalDateTime createdAt,
        Integer guestCount,
        BigDecimal unitPrice,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        String status,
        String note,
        List<TravelerResponse> travelers
) {
}
