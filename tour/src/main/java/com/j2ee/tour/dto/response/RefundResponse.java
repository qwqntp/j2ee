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
    public static RefundResponse from(com.j2ee.tour.model.Refund refund) {
        return new RefundResponse(
                refund.getRefundId(),
                refund.getPayment() == null ? null : refund.getPayment().getPaymentId(),
                refund.getBooking() == null ? null : refund.getBooking().getBookingId(),
                refund.getRefundAmount(),
                refund.getRefundReason(),
                refund.getRefundStatus(),
                refund.getRefundRequestedAt(),
                refund.getRefundProcessedAt()
        );
    }
}
