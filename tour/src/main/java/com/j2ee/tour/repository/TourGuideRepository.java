package com.j2ee.tour.repository;

import com.j2ee.tour.model.TourGuide;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TourGuideRepository extends JpaRepository<TourGuide, Long> {
}
