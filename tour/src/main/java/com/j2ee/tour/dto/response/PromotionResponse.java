package com.j2ee.tour.dto.response;

import com.j2ee.tour.model.Promotion;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PromotionResponse(
        Long id,
        String name,
        BigDecimal discountPercentage,
        LocalDate startDate,
        LocalDate endDate,
        String description
) {
    public static PromotionResponse from(Promotion promotion) {
        return new PromotionResponse(
                promotion.getPromotionId(),
                promotion.getPromotionName(),
                promotion.getDiscountPercentage(),
                promotion.getStartDate(),
                promotion.getEndDate(),
                promotion.getDescription()
        );
    }
}
