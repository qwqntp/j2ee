package com.j2ee.tour.repository;

import com.j2ee.tour.dto.response.AdminPassengerManifest.Passenger;
import com.j2ee.tour.model.TourDeparture;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class AdminPassengerRepository {
    private final EntityManager entityManager;

    public AdminPassengerRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Optional<TourDeparture> findDeparture(Long id) {
        return entityManager.createQuery(
                "select d from TourDeparture d join fetch d.tour where d.departureId = :id",
                TourDeparture.class).setParameter("id", id).getResultStream().findFirst();
    }

    public List<Passenger> findPassengers(Long id) {
        return entityManager.createQuery("""
                select new com.j2ee.tour.dto.response.AdminPassengerManifest$Passenger(
                  t.travelerName, t.travelerPhone, t.travelerEmail, t.travelerDateOfBirth,
                  t.travelerGender, t.travelerIdDocument, b.bookingId, b.bookingCode,
                  b.bookingStatus, b.bookingNote)
                from Traveler t join t.booking b
                where b.departure.departureId = :id and b.bookingStatus <> 'CANCELLED'
                order by b.bookingCode, t.travelerId
                """, Passenger.class).setParameter("id", id).getResultList();
    }
}
