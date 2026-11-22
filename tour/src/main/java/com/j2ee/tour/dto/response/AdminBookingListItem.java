package com.j2ee.tour.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Admin booking table row. Detail-only fields (notes, travelers, promotion,
 * payments) stay on the existing booking detail endpoint.
 */
public record AdminBookingListItem(
        Long id,
        String bookingCode,
        String tourName,
        LocalDateTime startDate,
        LocalDateTime endDate,
        Integer guestCount,
        BigDecimal totalAmount,
        String status
) {
}
