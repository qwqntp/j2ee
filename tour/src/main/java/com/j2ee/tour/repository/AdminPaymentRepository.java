package com.j2ee.tour.repository;

import com.j2ee.tour.dto.response.AdminPaymentItem;
import com.j2ee.tour.model.Payment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class AdminPaymentRepository {
    private final EntityManager em;

    public AdminPaymentRepository(EntityManager em) { this.em = em; }

    public Page<AdminPaymentItem> search(String status, String method, LocalDateTime from,
                                        LocalDateTime until, String keyword, Pageable pageable) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        var query = cb.createQuery(AdminPaymentItem.class);
        Root<Payment> p = query.from(Payment.class);
        var b = p.join("booking");
        query.select(cb.construct(AdminPaymentItem.class, p.get("paymentId"), b.get("bookingId"),
                b.get("bookingCode"), p.get("paymentCreatedAt"), p.get("paymentAmount"),
                p.get("paymentMethod"), p.get("paymentStatus"), p.get("paymentProviderTxnId")));
        query.where(filters(cb, p, status, method, from, until, keyword));
        query.orderBy(cb.desc(p.get("paymentCreatedAt")), cb.desc(p.get("paymentId")));
        var items = em.createQuery(query).setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize()).getResultList();
        var count = cb.createQuery(Long.class);
        Root<Payment> c = count.from(Payment.class);
        count.select(cb.count(c)).where(filters(cb, c, status, method, from, until, keyword));
        return new PageImpl<>(items, pageable, em.createQuery(count).getSingleResult());
    }

    private Predicate[] filters(CriteriaBuilder cb, Root<Payment> p, String status, String method,
                                LocalDateTime from, LocalDateTime until, String keyword) {
        List<Predicate> conditions = new ArrayList<>();
        if (status != null) conditions.add(cb.equal(p.get("paymentStatus"), status));
        if (method != null) conditions.add(cb.equal(p.get("paymentMethod"), method));
        if (from != null) conditions.add(cb.greaterThanOrEqualTo(p.get("paymentCreatedAt"), from));
        if (until != null) conditions.add(cb.lessThan(p.get("paymentCreatedAt"), until));
        if (keyword != null) {
            String pattern = "%" + keyword.toLowerCase(java.util.Locale.ROOT) + "%";
            conditions.add(cb.or(cb.like(cb.lower(p.get("booking").get("bookingCode")), pattern),
                    cb.like(cb.lower(p.get("paymentProviderTxnId")), pattern)));
        }
        return conditions.toArray(Predicate[]::new);
    }
}
