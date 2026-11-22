package com.j2ee.tour.repository;

import com.j2ee.tour.dto.response.AdminInvoiceListItem;
import com.j2ee.tour.model.Invoice;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Optional<Invoice> findByBookingBookingId(Long bookingId);

    /**
     * Admin list projection. Promotions and other detail-only fields stay out
     * of this query; the pattern is always bound, never concatenated.
     */
    @Query(value = """
            select new com.j2ee.tour.dto.response.AdminInvoiceListItem(
                i.invoiceId, i.booking.bookingId, i.invoiceCreatedAt, i.status,
                i.totalAmount, i.paymentMethod, i.customerName, i.customerEmail)
            from Invoice i
            where (:status is null or i.status = :status)
              and (:pattern is null
                   or lower(i.customerName) like :pattern escape '\\'
                   or lower(i.customerEmail) like :pattern escape '\\'
                   or lower(i.paymentMethod) like :pattern escape '\\'
                   or cast(i.booking.bookingId as string) like :pattern escape '\\')
            """,
            countQuery = """
            select count(i)
            from Invoice i
            where (:status is null or i.status = :status)
              and (:pattern is null
                   or lower(i.customerName) like :pattern escape '\\'
                   or lower(i.customerEmail) like :pattern escape '\\'
                   or lower(i.paymentMethod) like :pattern escape '\\'
                   or cast(i.booking.bookingId as string) like :pattern escape '\\')
            """)
    Page<AdminInvoiceListItem> findAdminPage(@Param("status") String status,
                                             @Param("pattern") String pattern,
                                             Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.booking.bookingId = :bookingId")
    Optional<Invoice> findByBookingIdForUpdate(@Param("bookingId") Long bookingId);

    Page<Invoice> findByBookingAccountAccountId(Long accountId, Pageable pageable);

    boolean existsByBookingBookingId(Long bookingId);
}
