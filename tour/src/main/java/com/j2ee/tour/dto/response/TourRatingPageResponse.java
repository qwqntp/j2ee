package com.j2ee.tour.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record TourRatingPageResponse(
        List<TourRatingResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last,
        boolean hasNext,
        boolean hasPrevious,
        BigDecimal averageRating,
        long ratingCount
) {
}
