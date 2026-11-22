package com.j2ee.tour.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Normalizes admin list paging before it reaches the database.
 *
 * <p>Keeps the page size inside the configured cap, validates public sort keys
 * against an allowlist, and always appends a unique tie-breaker so pages stay
 * stable when the primary sort key has duplicates.</p>
 */
public final class AdminListPageRequest {
    public static final int DEFAULT_PAGE_SIZE = 10;

    private AdminListPageRequest() {
    }

    public static Pageable normalize(Pageable pageable, int maxSize,
                                     Map<String, String> allowedProperties,
                                     String defaultProperty, String idProperty) {
        if (maxSize < 1) {
            throw new IllegalArgumentException("maxPageSize must be positive");
        }

        int page = Math.max(0, pageable.getPageNumber());
        int size = pageable.getPageSize();
        if (size < 1) {
            size = DEFAULT_PAGE_SIZE;
        }
        if (size > maxSize) {
            size = maxSize;
        }

        Sort sort = normalizeSort(pageable.getSort(), allowedProperties, defaultProperty, idProperty);
        return PageRequest.of(page, size, sort);
    }

    private static Sort normalizeSort(Sort sort, Map<String, String> allowedProperties,
                                      String defaultProperty, String idProperty) {
        String mappedId = allowedProperties.get(idProperty);
        if (mappedId == null) {
            throw new IllegalArgumentException("id sort property must be allowlisted");
        }

        List<Sort.Order> orders = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Sort.Order order : sort) {
            String mapped = allowedProperties.get(order.getProperty());
            if (mapped == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid sort");
            }
            if (seen.add(mapped)) {
                orders.add(new Sort.Order(order.getDirection(), mapped));
            }
        }

        if (orders.isEmpty()) {
            String mappedDefault = allowedProperties.get(defaultProperty);
            if (mappedDefault == null) {
                throw new IllegalArgumentException("default sort property must be allowlisted");
            }
            orders.add(new Sort.Order(Sort.Direction.DESC, mappedDefault));
            seen.add(mappedDefault);
        }

        if (seen.add(mappedId)) {
            orders.add(new Sort.Order(Sort.Direction.DESC, mappedId));
        }
        return Sort.by(orders);
    }
}
