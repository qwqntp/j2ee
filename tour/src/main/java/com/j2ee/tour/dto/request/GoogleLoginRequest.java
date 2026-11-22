package com.j2ee.tour.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoogleLoginRequest(
        @NotBlank(message = "Google ID token is required")
        @Size(max = 4096, message = "Google ID token is too long")
        String idToken
) {
}
