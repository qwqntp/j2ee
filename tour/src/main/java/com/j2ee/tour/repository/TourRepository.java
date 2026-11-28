package com.j2ee.tour.repository;

import com.j2ee.tour.model.Tour;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TourRepository extends JpaRepository<Tour, Long>, TourSearchRepository {

    Page<Tour> findByTourStatus(String tourStatus, org.springframework.data.domain.Pageable pageable);

    Optional<Tour> findByTourIdAndTourStatus(Long tourId, String tourStatus);

    boolean existsByTourIdAndTourStatus(Long tourId, String tourStatus);

    long countByTourStatus(String tourStatus);
}
