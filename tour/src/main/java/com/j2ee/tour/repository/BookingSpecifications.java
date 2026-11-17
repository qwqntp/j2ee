package com.j2ee.tour.repository;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.dto.request.AdminBookingFilter;
import com.j2ee.tour.model.Booking;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class BookingSpecifications {
    private static final Set<String> ALLOWED_STATUSES = Set.of(
            "PENDING", "PAID", "CONFIRMED", "COMPLETED", "CANCELLED");

    private BookingSpecifications() {
    }

    /**
     * Validates eagerly so bad filters fail before a repository call.
     * Filter values are bound as criteria parameters, never concatenated into JPQL.
     */
    public static Specification<Booking> from(AdminBookingFilter filter) {
        String status = filter.status() == null ? null : filter.status().trim();
        if (status != null && !status.isEmpty() && !ALLOWED_STATUSES.contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid booking status");
        }

        LocalDate fromDate = filter.from();
        LocalDate toDate = filter.to();
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "from must be on or before to");
        }

        String pattern = filter.query() == null || filter.query().isBlank()
                ? null
                : "%" + filter.query().trim().toLowerCase(Locale.ROOT) + "%";

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isEmpty()) {
                predicates.add(cb.equal(root.get("bookingStatus"), status));
            }
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("bookingCreatedAt"),
                        fromDate.atStartOfDay()));
            }
            if (toDate != null) {
                predicates.add(cb.lessThan(root.get("bookingCreatedAt"),
                        toDate.plusDays(1).atStartOfDay()));
            }
            if (pattern != null) {
                Join<Object, Object> account = root.join("account", JoinType.LEFT);
                Join<Object, Object> departure = root.join("departure", JoinType.LEFT);
                Join<Object, Object> tour = departure.join("tour", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("bookingCode")), pattern),
                        cb.like(cb.lower(account.get("username")), pattern),
                        cb.like(cb.lower(account.get("accountEmail")), pattern),
                        cb.like(cb.lower(tour.get("tourName")), pattern)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Booking> ownedBy(Long accountId) {
        return (root, query, cb) ->
                cb.equal(root.get("account").get("accountId"), accountId);
    }
}
