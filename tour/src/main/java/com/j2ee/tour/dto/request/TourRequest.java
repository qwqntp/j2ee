package com.j2ee.tour.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TourRequest(
        @NotBlank @Size(max = 255) String name,
        String description,
        @Min(0) Integer durationDays,
        @NotBlank @Pattern(regexp = "DRAFT|OPEN|CLOSED|CANCELLED") String status,
        Long accommodationId,
        Long tourGuideId,
        Long promotionId,
        List<@NotBlank @Size(max = 512) String> imageUrls,
        List<Long> destinationIds,
        List<Long> vehicleIds
) {
}
