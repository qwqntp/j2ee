package com.j2ee.tour.repository;

import com.j2ee.tour.model.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Optional<Invoice> findByBookingBookingId(Long bookingId);

    boolean existsByBookingBookingId(Long bookingId);
}
