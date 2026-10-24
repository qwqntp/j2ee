package com.j2ee.tour.dto.response;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        AccountResponse account
) {
}