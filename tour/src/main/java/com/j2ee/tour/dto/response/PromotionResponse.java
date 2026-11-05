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
        String description,
        String code,
        Boolean active,
        Integer usageLimit,
        Integer usedCount,
        BigDecimal minOrderAmount
) {
    public static PromotionResponse from(Promotion promotion) {
        return new PromotionResponse(
                promotion.getPromotionId(),
                promotion.getPromotionName(),
                promotion.getDiscountPercentage(),
                promotion.getStartDate(),
                promotion.getEndDate(),
                promotion.getDescription(),
                promotion.getPromotionCode(),
                promotion.getPromotionActive(),
                promotion.getPromotionUsageLimit(),
                promotion.getPromotionUsedCount(),
                promotion.getPromotionMinOrderAmount()
        );
    }
}
