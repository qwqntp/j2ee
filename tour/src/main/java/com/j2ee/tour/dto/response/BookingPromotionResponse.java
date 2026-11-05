package com.j2ee.tour.dto.response;

import com.j2ee.tour.model.Promotion;

import java.math.BigDecimal;

public record BookingPromotionResponse(
        Long id,
        String code,
        String name,
        BigDecimal discountPercentage
) {
    public static BookingPromotionResponse from(Promotion promotion) {
        return new BookingPromotionResponse(
                promotion.getPromotionId(),
                promotion.getPromotionCode(),
                promotion.getPromotionName(),
                promotion.getDiscountPercentage()
        );
    }
}
