package com.j2ee.tour.repository;

import com.j2ee.tour.model.TourImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TourImageRepository extends JpaRepository<TourImage, Long> {
    List<TourImage> findByTourTourId(Long tourId);
}
