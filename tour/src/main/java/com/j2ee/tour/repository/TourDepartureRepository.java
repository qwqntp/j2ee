package com.j2ee.tour.repository;

import com.j2ee.tour.model.TourDeparture;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TourDepartureRepository extends JpaRepository<TourDeparture, Long> {
    boolean existsByTourTourId(Long tourId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select departure from TourDeparture departure where departure.departureId = :id")
    Optional<TourDeparture> findByIdForUpdate(@Param("id") Long id);
}