package com.j2ee.tour.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TourImageRequest(
        @NotBlank @Size(max = 512) String imageUrl,
        @NotNull Long tourId
) {
}
