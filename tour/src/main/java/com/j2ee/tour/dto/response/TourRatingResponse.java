package com.j2ee.tour.dto.response;

import com.j2ee.tour.model.TourRating;

import java.time.LocalDateTime;

public record TourRatingResponse(
        Long id,
        Long tourId,
        Long accountId,
        String accountUsername,
        Long bookingId,
        Integer rating,
        String comment,
        LocalDateTime createdAt
) {
    public static TourRatingResponse from(TourRating rating) {
        return new TourRatingResponse(
                rating.getRatingId(),
                rating.getTour().getTourId(),
                rating.getAccount().getAccountId(),
                rating.getAccount().getUsername(),
                rating.getBooking().getBookingId(),
                rating.getRatingValue(),
                rating.getRatingComment(),
                rating.getRatingCreatedAt()
        );
    }
}
