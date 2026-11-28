package com.j2ee.tour.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AdminPassengerManifest(Long departureId, String tourName, LocalDateTime startDate,
                                     Integer capacity, Integer booked, Integer remaining,
                                     List<Passenger> passengers) {
    public record Passenger(String fullName, String phone, String email, LocalDate dateOfBirth,
                            String gender, String idDocument, Long bookingId, String bookingCode,
                            String bookingStatus, String contactNote) {}
}
