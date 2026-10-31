package com.j2ee.tour.repository;

import com.j2ee.tour.model.TravelVehicle;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelVehicleRepository extends JpaRepository<TravelVehicle, Long> {
}
