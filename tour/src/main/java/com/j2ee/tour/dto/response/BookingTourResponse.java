package com.j2ee.tour.dto.response;

import com.j2ee.tour.model.Tour;
import com.j2ee.tour.model.TourDeparture;
import com.j2ee.tour.model.TourImage;

import java.time.LocalDateTime;

public record BookingTourResponse(
        Long tourId,
        String tourName,
        String imageUrl,
        LocalDateTime startDate,
        LocalDateTime endDate,
        String meetingPoint,
        String departureStatus
) {
    public static BookingTourResponse from(TourDeparture departure) {
        if (departure == null || departure.getTour() == null) {
            return null;
        }
        Tour tour = departure.getTour();
        return new BookingTourResponse(
                tour.getTourId(),
                tour.getTourName(),
                firstImage(tour),
                departure.getDepartureStartDate(),
                departure.getDepartureEndDate(),
                departure.getDepartureMeetingPoint(),
                departure.getDepartureStatus()
        );
    }

    private static String firstImage(Tour tour) {
        if (tour.getTourImages() == null) {
            return null;
        }
        return tour.getTourImages().stream()
                .map(TourImage::getImageUrl)
                .findFirst()
                .orElse(null);
    }
}
