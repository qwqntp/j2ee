package com.j2ee.tour.service;

import com.j2ee.tour.model.Tour;
import com.j2ee.tour.model.TourDeparture;
import com.j2ee.tour.repository.AdminPassengerRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminPassengerServiceTest {
    @Test
    void returnsDepartureCapacityAndPassengers() {
        var repository = mock(AdminPassengerRepository.class);
        var tour = new Tour();
        tour.setTourName("Bay");
        var departure = new TourDeparture();
        departure.setTour(tour);
        departure.setDepartureStartDate(LocalDateTime.of(2026, 12, 1, 8, 0));
        departure.setDepartureCapacity(20);
        departure.setDepartureBookedSlots(7);
        when(repository.findDeparture(5L)).thenReturn(Optional.of(departure));
        when(repository.findPassengers(5L)).thenReturn(List.of());

        var result = new AdminPassengerService(repository).get(5L);

        assertEquals("Bay", result.tourName());
        assertEquals(20, result.capacity());
        assertEquals(7, result.booked());
        assertEquals(13, result.remaining());
        assertTrue(result.passengers().isEmpty());
    }

    @Test
    void missingDepartureIsNotFound() {
        var repository = mock(AdminPassengerRepository.class);
        when(repository.findDeparture(99L)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> new AdminPassengerService(repository).get(99L));
        verify(repository, never()).findPassengers(anyLong());
    }
}
