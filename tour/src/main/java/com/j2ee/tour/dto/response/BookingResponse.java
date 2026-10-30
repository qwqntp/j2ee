package com.j2ee.tour.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record BookingResponse(
        Long id,
        String bookingCode,
        Long departureId,
        Long accountId,
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
    public static BookingResponse from(com.j2ee.tour.model.Booking booking) {
        List<TravelerResponse> travelers = booking.getTravelers() == null
                ? java.util.List.of()
                : booking.getTravelers().stream().map(TravelerResponse::from).toList();
        return new BookingResponse(
                booking.getBookingId(),
                booking.getBookingCode(),
                booking.getDeparture() == null ? null : booking.getDeparture().getDepartureId(),
                booking.getAccount() == null ? null : booking.getAccount().getAccountId(),
                booking.getBookingCreatedAt(),
                booking.getBookingGuestCount(),
                booking.getBookingUnitPrice(),
                booking.getBookingSubtotal(),
                booking.getBookingDiscountAmount(),
                booking.getBookingTotalAmount(),
                booking.getBookingStatus(),
                booking.getBookingNote(),
                travelers
        );
    }
}
