package com.j2ee.tour.repository;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.booking.bookingId = :bookingId")
    Optional<Invoice> findByBookingIdForUpdate(@Param("bookingId") Long bookingId);

    Page<Invoice> findByBookingAccountAccountId(Long accountId, Pageable pageable);

    boolean existsByBookingBookingId(Long bookingId);
}
