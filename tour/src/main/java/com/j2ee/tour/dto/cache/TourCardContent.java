package com.j2ee.tour.dto.cache;

public record TourCardContent(
        Long id,
        String name,
        String description,
        String status,
        String imageUrl) {
}
