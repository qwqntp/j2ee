package com.j2ee.tour.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Scalar admin invoice row. Contains only what the admin table renders;
 * detail-only fields (promotions, phone, tax, service fee) stay on
 * {@link InvoiceResponse}.
 */
public record AdminInvoiceListItem(
        Long id,
        Long bookingId,
        LocalDateTime createdAt,
        String status,
        BigDecimal totalAmount,
        String paymentMethod,
        String customerName,
        String customerEmail
) {
}
