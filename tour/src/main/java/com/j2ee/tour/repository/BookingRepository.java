package com.j2ee.tour.repository;

import com.j2ee.tour.model.Booking;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByBookingCode(String bookingCode);

    Page<Booking> findByAccountAccountId(Long accountId, Pageable pageable);

    List<Booking> findByDepartureDepartureId(Long departureId);

    boolean existsByDepartureDepartureId(Long departureId);

    long countByBookingStatus(String status);

    @Query("select b.departure.tour.tourId, b.departure.tour.tourName, count(b), "
            + "coalesce(sum(b.bookingTotalAmount), 0) from Booking b "
            + "where b.bookingStatus <> 'CANCELLED' "
            + "group by b.departure.tour.tourId, b.departure.tour.tourName "
            + "order by count(b) desc")
    List<Object[]> findTopToursByBookingCount(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select booking from Booking booking where booking.bookingId = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);
}