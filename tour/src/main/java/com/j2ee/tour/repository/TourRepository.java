package com.j2ee.tour.repository;

import com.j2ee.tour.model.Tour;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TourRepository extends JpaRepository<Tour, Long> {

    Page<Tour> findByTourStatus(String tourStatus, Pageable pageable);

    Page<Tour> findByTourStatusAndTourNameContainingIgnoreCase(
            String tourStatus, String tourName, Pageable pageable);

    Optional<Tour> findByTourIdAndTourStatus(Long tourId, String tourStatus);
}
