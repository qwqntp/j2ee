package com.j2ee.tour.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public record BookingUpdateRequest(
        Long accountId,
        @Min(1) Integer guestCount,
        String note,
        @Pattern(regexp = "PENDING|PAID|CONFIRMED|COMPLETED|CANCELLED") String status
) {
}
