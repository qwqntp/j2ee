package com.j2ee.tour.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateBookingRequest(
        @NotNull Long departureId,
        @NotNull @Min(1) Integer guestCount,
        String note,
        List<@Valid BookingTravelerRequest> travelers,
        @Size(max = 64) String promotionCode
) {
    public CreateBookingRequest(Long departureId, Integer guestCount, String note) {
        this(departureId, guestCount, note, null, null);
    }
}
