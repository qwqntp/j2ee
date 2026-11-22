package com.j2ee.tour.repository;

import com.j2ee.tour.dto.response.AdminBookingListItem;
import com.j2ee.tour.model.Booking;
import com.j2ee.tour.model.Tour;
import com.j2ee.tour.model.TourDeparture;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Scalar admin booking reads. Selects only the fields the admin table renders
 * and keeps collection associations (travelers, tour images) out of the query.
 */
@Repository
public class AdminBookingListRepository {
    private final EntityManager entityManager;

    public AdminBookingListRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Page<AdminBookingListItem> findPage(Pageable pageable,
                                               Specification<Booking> specification) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<AdminBookingListItem> query =
                cb.createQuery(AdminBookingListItem.class);
        Root<Booking> root = query.from(Booking.class);
        Path<Long> id = root.get("bookingId");
        Path<String> code = root.get("bookingCode");
        Path<TourDeparture> departure = root.get("departure");
        Path<Tour> tour = departure.get("tour");
        Path<LocalDateTime> startDate = departure.get("departureStartDate");
        Path<LocalDateTime> endDate = departure.get("departureEndDate");
        Path<Integer> guestCount = root.get("bookingGuestCount");
        Path<BigDecimal> totalAmount = root.get("bookingTotalAmount");
        Path<String> status = root.get("bookingStatus");

        query.select(cb.construct(AdminBookingListItem.class,
                id, code, tour.get("tourName"), startDate, endDate,
                guestCount, totalAmount, status));
        Predicate predicate = specification.toPredicate(root, query, cb);
        if (predicate != null) {
            query.where(predicate);
        }
        query.orderBy(toOrders(cb, root, pageable));

        TypedQuery<AdminBookingListItem> dataQuery = entityManager.createQuery(query)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize());
        List<AdminBookingListItem> content = dataQuery.getResultList();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Booking> countRoot = countQuery.from(Booking.class);
        Predicate countPredicate = specification.toPredicate(countRoot, countQuery, cb);
        countQuery.select(cb.count(countRoot));
        if (countPredicate != null) {
            countQuery.where(countPredicate);
        }
        Long total = entityManager.createQuery(countQuery).getSingleResult();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private List<Order> toOrders(CriteriaBuilder cb, Root<Booking> root, Pageable pageable) {
        return pageable.getSort().stream()
                .map(order -> order.isAscending()
                        ? cb.asc(root.get(order.getProperty()))
                        : cb.desc(root.get(order.getProperty())))
                .toList();
    }
}
