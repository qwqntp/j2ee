package com.j2ee.tour.service;

import com.j2ee.tour.model.TourGuide;
import com.j2ee.tour.repository.TourDepartureRepository;
import com.j2ee.tour.repository.TourGuideRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Guards a tour guide's schedule: rejects any write that would put one guide
 * on two overlapping, non-CANCELLED departures. Called from the service layer
 * so admins get a friendly 409; the V8 database triggers are the race-proof
 * backstop for concurrent writes.
 */
@Service
public class GuideOverlapValidator {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final TourDepartureRepository departureRepository;
    private final TourGuideRepository guideRepository;

    public GuideOverlapValidator(TourDepartureRepository departureRepository,
                                 TourGuideRepository guideRepository) {
        this.departureRepository = departureRepository;
        this.guideRepository = guideRepository;
    }

    public void assertGuideAvailable(Long guideId, LocalDateTime start, LocalDateTime end,
                                     Long excludeDepartureId) {
        long conflicts = departureRepository.countGuideOverlap(guideId, start, end, excludeDepartureId);
        if (conflicts > 0) {
            String name = guideRepository.findById(guideId)
                    .map(TourGuide::getTourGuideName)
                    .orElse("#" + guideId);
            throw new IllegalStateException(
                    "Guide " + name + " already has an overlapping departure during "
                            + start.format(FORMAT) + " – " + end.format(FORMAT));
        }
    }
}
