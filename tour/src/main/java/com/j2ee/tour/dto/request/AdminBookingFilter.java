package com.j2ee.tour.dto.request;

import java.time.LocalDate;

public record AdminBookingFilter(String status, LocalDate from, LocalDate to, String query) {
}
