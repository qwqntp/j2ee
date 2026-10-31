package com.j2ee.tour.dto.response;

import com.j2ee.tour.model.TourImage;

public record TourImageResponse(
        Long id,
        String imageUrl,
        Long tourId
) {
    public static TourImageResponse from(TourImage image) {
        return new TourImageResponse(
                image.getImageId(),
                image.getImageUrl(),
                image.getTour() == null ? null : image.getTour().getTourId()
        );
    }
}
