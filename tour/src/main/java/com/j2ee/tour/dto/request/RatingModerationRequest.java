package com.j2ee.tour.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RatingModerationRequest(@NotBlank String status) {
}
