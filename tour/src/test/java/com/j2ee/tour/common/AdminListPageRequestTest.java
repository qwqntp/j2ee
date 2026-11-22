package com.j2ee.tour.common;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminListPageRequestTest {
    private static final Map<String, String> SORTABLE = Map.of(
            "createdAt", "bookingCreatedAt",
            "totalAmount", "bookingTotalAmount",
            "id", "bookingId");

    @Test
    void defaultsToStableOrderAndClampsSize() {
        Pageable effective = AdminListPageRequest.normalize(
                PageRequest.of(1, 100), 50, SORTABLE, "createdAt", "id");

        assertEquals(1, effective.getPageNumber());
        assertEquals(50, effective.getPageSize());
        assertEquals(50, effective.getOffset());
        assertEquals(Sort.by(
                Sort.Order.desc("bookingCreatedAt"),
                Sort.Order.desc("bookingId")), effective.getSort());
    }

    @Test
    void mapsAllowedSortAndAppendsUniqueTieBreaker() {
        Pageable effective = AdminListPageRequest.normalize(
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "totalAmount")),
                50, SORTABLE, "createdAt", "id");

        assertEquals(Sort.by(
                Sort.Order.asc("bookingTotalAmount"),
                Sort.Order.desc("bookingId")), effective.getSort());
    }

    @Test
    void keepsExplicitIdSortWithoutDuplicatingIt() {
        Pageable effective = AdminListPageRequest.normalize(
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "id")),
                50, SORTABLE, "createdAt", "id");

        assertEquals(Sort.by(Sort.Order.asc("bookingId")), effective.getSort());
    }

    @Test
    void rejectsUnknownSort() {
        ApiException error = assertThrows(ApiException.class, () ->
                AdminListPageRequest.normalize(
                        PageRequest.of(0, 10, Sort.by("bookingNote")),
                        50, SORTABLE, "createdAt", "id"));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals("Invalid sort", error.getMessage());
    }

    @Test
    void rejectsNonPositiveMaxSize() {
        assertThrows(IllegalArgumentException.class, () ->
                AdminListPageRequest.normalize(
                        PageRequest.of(0, 10), 0, SORTABLE, "createdAt", "id"));
    }
}
