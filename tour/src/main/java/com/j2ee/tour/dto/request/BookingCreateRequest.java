package com.j2ee.tour.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record BookingCreateRequest(
        @NotNull(message = "Departure ID is required")
        Long departureId,

        @NotNull(message = "Guest count is required")
        @Min(value = 1, message = "Guest count must be at least 1")
        Integer guestCount,

        @NotEmpty(message = "At least one traveler is required")
        @Valid
        List<TravelerRequest> travelers,

        String note
) {
}
