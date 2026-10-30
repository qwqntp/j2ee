package com.j2ee.tour.dto.response;

import com.j2ee.tour.model.Invoice;
import com.j2ee.tour.model.Promotion;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record InvoiceResponse(
        Long id,
        Long bookingId,
        LocalDateTime createdAt,
        String status,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal serviceFee,
        BigDecimal totalAmount,
        String paymentMethod,
        String customerName,
        String customerPhone,
        String customerEmail,
        List<Long> promotionIds
) {
    public static InvoiceResponse from(Invoice invoice) {
        List<Long> promotionIds = invoice.getPromotions() == null
                ? List.of()
                : invoice.getPromotions().stream().map(Promotion::getPromotionId).toList();
        return new InvoiceResponse(
                invoice.getInvoiceId(),
                invoice.getBooking() == null ? null : invoice.getBooking().getBookingId(),
                invoice.getInvoiceCreatedAt(),
                invoice.getStatus(),
                invoice.getDiscountAmount(),
                invoice.getTaxAmount(),
                invoice.getServiceFee(),
                invoice.getTotalAmount(),
                invoice.getPaymentMethod(),
                invoice.getCustomerName(),
                invoice.getCustomerPhone(),
                invoice.getCustomerEmail(),
                promotionIds
        );
    }
}
