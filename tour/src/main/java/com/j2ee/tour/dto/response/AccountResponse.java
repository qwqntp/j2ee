package com.j2ee.tour.dto.response;

public record AccountResponse(
        Long id,
        String username,
        String email,
        Integer status,
        String roleName
) {
}