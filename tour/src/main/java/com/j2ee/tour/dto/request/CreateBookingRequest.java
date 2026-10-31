package com.j2ee.tour.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateBookingRequest(
        @NotNull Long departureId,
        @NotNull @Min(1) Integer guestCount,
        String note
) {
}