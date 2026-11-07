package com.j2ee.tour.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyEmailRequest(
        @NotBlank(message = "Token is required")
        @Size(max = 512, message = "Token is too long")
        String token
) {
}
