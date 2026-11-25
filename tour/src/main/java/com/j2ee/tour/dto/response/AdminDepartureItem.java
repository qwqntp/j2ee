package com.j2ee.tour.dto.response;

import java.time.LocalDateTime;

public record AdminDepartureItem(Long id, Long tourId, String tourName,
                                 LocalDateTime startDate, LocalDateTime endDate, String status,
                                 int capacity, int booked, int remaining) {
}
