package com.j2ee.tour.repository;

import com.j2ee.tour.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByBookingCode(String bookingCode);

    boolean existsByDepartureDepartureId(Long departureId);
}