package com.j2ee.tour.dto.request;

import com.j2ee.tour.common.ApiException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Query filters for the public tour catalogue. Built by the controller from request parameters;
 * cross-field validation lives in {@code TourService}.
 */
public record TourSearchCriteria(
        String keyword,
        Long destinationId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        LocalDate startFrom,
        LocalDate startTo,
        Integer minDays,
        Integer maxDays,
        Sort sort
) {

    public enum Sort {
        DEFAULT, NEWEST, PRICE_ASC, PRICE_DESC, DATE_ASC
    }

    public TourSearchCriteria {
        if (sort == null) {
            sort = Sort.DEFAULT;
        }
    }

    /**
     * Maps the public wire value to a sort. Unknown values are rejected with 400.
     */
    public static Sort parseSort(String value) {
        if (value == null) {
            return Sort.DEFAULT;
        }
        return switch (value) {
            case "newest" -> Sort.NEWEST;
            case "priceAsc" -> Sort.PRICE_ASC;
            case "priceDesc" -> Sort.PRICE_DESC;
            case "dateAsc" -> Sort.DATE_ASC;
            default -> throw new ApiException(HttpStatus.BAD_REQUEST,
                    "sort must be one of: newest, priceAsc, priceDesc, dateAsc");
        };
    }

    /**
     * Price/date filters only match tours that have a matching upcoming departure.
     */
    public boolean hasDepartureFilter() {
        return minPrice != null || maxPrice != null || startFrom != null || startTo != null;
    }

    /**
     * True only for the plain, default-ordered catalogue query. Filtered or explicitly sorted
     * variants are not cached, so arbitrary filter combinations cannot grow the Redis cache.
     */
    public boolean isUnfiltered() {
        return keyword == null && destinationId == null && !hasDepartureFilter()
                && minDays == null && maxDays == null && sort == Sort.DEFAULT;
    }
}
