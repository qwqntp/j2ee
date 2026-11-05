package com.j2ee.tour.service;

import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.PromotionRequest;
import com.j2ee.tour.dto.response.PromotionResponse;
import com.j2ee.tour.model.Promotion;
import com.j2ee.tour.repository.PromotionRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.NoSuchElementException;

@Service
public class PromotionService {
    private final PromotionRepository promotionRepository;

    public PromotionService(PromotionRepository promotionRepository) {
        this.promotionRepository = promotionRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<PromotionResponse> list(Pageable pageable) {
        return PageResponse.from(promotionRepository.findAll(pageable), PromotionResponse::from);
    }

    @Transactional(readOnly = true)
    public PromotionResponse get(Long id) {
        return PromotionResponse.from(find(id));
    }

    @Transactional
    public PromotionResponse create(PromotionRequest request) {
        Promotion promotion = new Promotion();
        apply(promotion, request);
        return PromotionResponse.from(promotionRepository.save(promotion));
    }

    @Transactional
    public PromotionResponse update(Long id, PromotionRequest request) {
        Promotion promotion = find(id);
        apply(promotion, request);
        return PromotionResponse.from(promotionRepository.save(promotion));
    }

    @Transactional
    public void delete(Long id) {
        promotionRepository.delete(find(id));
    }

    private void apply(Promotion promotion, PromotionRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new IllegalStateException("Promotion end date must not be before start date");
        }
        String code = normalizeCode(request.code());
        if (code != null) {
            promotionRepository.findByPromotionCodeIgnoreCase(code)
                    .filter(existing -> !existing.getPromotionId().equals(promotion.getPromotionId()))
                    .ifPresent(existing -> {
                        throw new IllegalStateException("Promotion code is already in use");
                    });
        }
        int usedCount = promotion.getPromotionUsedCount() == null
                ? 0
                : promotion.getPromotionUsedCount();
        if (request.usageLimit() != null && request.usageLimit() < usedCount) {
            throw new IllegalStateException(
                    "Usage limit cannot be lower than the current used count");
        }
        promotion.setPromotionName(request.name());
        promotion.setDiscountPercentage(request.discountPercentage());
        promotion.setStartDate(request.startDate());
        promotion.setEndDate(request.endDate());
        promotion.setDescription(request.description());
        promotion.setPromotionCode(code);
        promotion.setPromotionActive(request.active() == null ? Boolean.TRUE : request.active());
        promotion.setPromotionUsageLimit(request.usageLimit());
        promotion.setPromotionMinOrderAmount(request.minOrderAmount());
        if (promotion.getPromotionUsedCount() == null) {
            promotion.setPromotionUsedCount(0);
        }
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private Promotion find(Long id) {
        return promotionRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Promotion not found"));
    }
}
