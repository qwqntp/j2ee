package com.j2ee.tour.repository;

import com.j2ee.tour.model.Traveler;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TravelerRepository extends JpaRepository<Traveler, Long> {
    List<Traveler> findByBookingBookingId(Long bookingId);
}
