package com.j2ee.tour.repository;

import com.j2ee.tour.model.TouristDestination;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TouristDestinationRepository extends JpaRepository<TouristDestination, Long> {
}
