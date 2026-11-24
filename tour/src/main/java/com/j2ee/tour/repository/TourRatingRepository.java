package com.j2ee.tour.repository;

import com.j2ee.tour.model.TourRating;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TourRatingRepository extends JpaRepository<TourRating, Long> {

    Page<TourRating> findByTourTourIdAndRatingStatus(Long tourId, String status, Pageable pageable);

    boolean existsByBookingBookingId(Long bookingId);

    @Query("select avg(rating.ratingValue) from TourRating rating "
            + "where rating.tour.tourId = :tourId and rating.ratingStatus = 'APPROVED'")
    Double averageRatingApproved(@Param("tourId") Long tourId);
}
