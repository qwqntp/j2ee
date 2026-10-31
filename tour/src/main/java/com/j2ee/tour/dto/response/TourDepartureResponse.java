package com.j2ee.tour.dto.response;

import com.j2ee.tour.model.TourDeparture;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TourDepartureResponse(
        Long id,
        Long tourId,
        LocalDateTime startDate,
        LocalDateTime endDate,
        Integer capacity,
        Integer bookedSlots,
        Integer seatsAvailable,
        BigDecimal price,
        String meetingPoint,
        String status,
        LocalDateTime createdAt
) {
    public static TourDepartureResponse from(TourDeparture departure) {
        return new TourDepartureResponse(
                departure.getDepartureId(),
                departure.getTour() == null ? null : departure.getTour().getTourId(),
                departure.getDepartureStartDate(),
                departure.getDepartureEndDate(),
                departure.getDepartureCapacity(),
                departure.getDepartureBookedSlots(),
                departure.getSeatsAvailable(),
                departure.getDeparturePrice(),
                departure.getDepartureMeetingPoint(),
                departure.getDepartureStatus(),
                departure.getDepartureCreatedAt()
        );
    }
}
