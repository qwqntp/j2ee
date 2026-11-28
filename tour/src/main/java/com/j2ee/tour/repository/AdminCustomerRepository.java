package com.j2ee.tour.repository;

import com.j2ee.tour.dto.response.AdminCustomerBooking;
import com.j2ee.tour.dto.response.CustomerResponse;
import com.j2ee.tour.model.Booking;
import com.j2ee.tour.model.Customer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Locale;

@Repository
public class AdminCustomerRepository {
    private final EntityManager em;
    public AdminCustomerRepository(EntityManager em) { this.em = em; }

    public Page<CustomerResponse> search(String q, Pageable pageable) {
        var cb = em.getCriteriaBuilder();
        var query = cb.createQuery(CustomerResponse.class);
        var c = query.from(Customer.class);
        var account = c.join("account", JoinType.LEFT);
        query.select(cb.construct(CustomerResponse.class, c.get("customerId"), c.get("customerName"),
                c.get("customerEmail"), c.get("customerPhone"), c.get("customerAddress"),
                c.get("customerDateOfBirth"), account.get("accountId")));
        String pattern = "%" + q.toLowerCase(Locale.ROOT) + "%";
        query.where(cb.or(cb.like(cb.lower(c.get("customerName")), pattern),
                cb.like(cb.lower(c.get("customerEmail")), pattern),
                cb.like(cb.lower(c.get("customerPhone")), pattern)));
        query.orderBy(cb.asc(c.get("customerName")), cb.asc(c.get("customerId")));
        var items = em.createQuery(query).setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize()).getResultList();
        var count = cb.createQuery(Long.class);
        var root = count.from(Customer.class);
        count.select(cb.count(root)).where(cb.or(cb.like(cb.lower(root.get("customerName")), pattern),
                cb.like(cb.lower(root.get("customerEmail")), pattern),
                cb.like(cb.lower(root.get("customerPhone")), pattern)));
        return new PageImpl<>(items, pageable, em.createQuery(count).getSingleResult());
    }

    public Page<AdminCustomerBooking> bookings(Long customerId, Pageable pageable) {
        var cb = em.getCriteriaBuilder();
        var query = cb.createQuery(AdminCustomerBooking.class);
        var b = query.from(Booking.class);
        var c = query.from(Customer.class);
        query.select(cb.construct(AdminCustomerBooking.class, b.get("bookingId"), b.get("bookingCode"),
                b.get("departure").get("tour").get("tourName"), b.get("bookingStatus"), b.get("bookingTotalAmount")));
        query.where(cb.equal(c.get("customerId"), customerId), cb.equal(b.get("account"), c.get("account")));
        query.orderBy(cb.desc(b.get("bookingCreatedAt")), cb.desc(b.get("bookingId")));
        var items = em.createQuery(query).setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize()).getResultList();
        var count = cb.createQuery(Long.class);
        var book = count.from(Booking.class);
        var customer = count.from(Customer.class);
        count.select(cb.count(book)).where(cb.equal(customer.get("customerId"), customerId),
                cb.equal(book.get("account"), customer.get("account")));
        return new PageImpl<>(items, pageable, em.createQuery(count).getSingleResult());
    }
}
