package com.j2ee.tour.dto.response;

import com.j2ee.tour.model.TourGuide;

public record TourGuideResponse(
        Long id,
        String name,
        String email,
        String phone,
        Integer experienceYears
) {
    public static TourGuideResponse from(TourGuide guide) {
        return new TourGuideResponse(
                guide.getTourGuideId(),
                guide.getTourGuideName(),
                guide.getTourGuideEmail(),
                guide.getTourGuidePhone(),
                guide.getTourGuideExperienceYears()
        );
    }
}
