package com.j2ee.tour.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TourDepartureRequest(
        @NotNull Long tourId,
        @NotNull LocalDateTime startDate,
        @NotNull LocalDateTime endDate,
        @NotNull @Min(1) Integer capacity,
        @Min(0) Integer bookedSlots,
        @NotNull @DecimalMin("0") BigDecimal price,
        @Size(max = 255) String meetingPoint,
        @Pattern(regexp = "OPEN|FULL|CLOSED|CANCELLED") String status
) {
}
