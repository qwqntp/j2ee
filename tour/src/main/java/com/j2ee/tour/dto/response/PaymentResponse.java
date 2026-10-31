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
    public static PaymentResponse from(com.j2ee.tour.model.Payment payment) {
        return new PaymentResponse(
                payment.getPaymentId(),
                payment.getBooking() == null ? null : payment.getBooking().getBookingId(),
                payment.getPaymentAmount(),
                payment.getPaymentMethod(),
                payment.getPaymentProviderTxnId(),
                payment.getPaymentStatus(),
                payment.getPaymentPaidAt(),
                payment.getPaymentCreatedAt()
        );
    }
}
