package com.j2ee.tour.service;

import com.j2ee.tour.dto.response.AdminPassengerManifest;
import com.j2ee.tour.repository.AdminPassengerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class AdminPassengerService {
    private final AdminPassengerRepository repository;

    public AdminPassengerService(AdminPassengerRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public AdminPassengerManifest get(Long id) {
        var departure = repository.findDeparture(id)
                .orElseThrow(() -> new NoSuchElementException("Departure not found"));
        return new AdminPassengerManifest(id, departure.getTour().getTourName(),
                departure.getDepartureStartDate(), departure.getDepartureCapacity(),
                departure.getDepartureBookedSlots(), departure.getSeatsAvailable(),
                repository.findPassengers(id));
    }
}
