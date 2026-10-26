package com.j2ee.tour.security;

public record AuthPrincipal(
        Long id,
        String username,
        String role
) {
}
