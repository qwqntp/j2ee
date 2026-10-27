package com.j2ee.tour.mapper;

import com.j2ee.tour.dto.response.TourDepartureResponse;
import com.j2ee.tour.dto.response.TourResponse;
import com.j2ee.tour.dto.response.TourSummaryResponse;
import com.j2ee.tour.model.Accommodation;
import com.j2ee.tour.model.Tour;
import com.j2ee.tour.model.TourDeparture;
import com.j2ee.tour.model.TourGuide;
import com.j2ee.tour.model.TourImage;
import com.j2ee.tour.model.TouristDestination;
import com.j2ee.tour.model.TravelVehicle;
import com.j2ee.tour.repository.TourStatsProjection;

import java.util.Arrays;
import java.util.List;

public final class TourMapper {

    private static final int SUMMARY_DESCRIPTION_LENGTH = 160;

    private TourMapper() {
    }

    public static TourSummaryResponse toSummary(Tour tour, TourStatsProjection stats) {
        return new TourSummaryResponse(
                tour.getTourId(),
                tour.getTourName(),
                truncate(firstSegment(tour.getTourDescription()), SUMMARY_DESCRIPTION_LENGTH),
                tour.getTourStatus(),
                firstImageUrl(tour),
                stats == null ? null : stats.getPriceFrom(),
                stats == null ? null : stats.getNextDepartureDate(),
                stats == null || stats.getSeatsAvailable() == null ? 0 : stats.getSeatsAvailable().intValue(),
                stats == null || stats.getDepartureCount() == null ? 0 : stats.getDepartureCount().intValue()
        );
    }

    public static TourResponse toDetail(Tour tour, List<TourDeparture> departures) {
        Accommodation accommodation = tour.getAccommodation();
        TourGuide guide = tour.getTourGuide();

        List<TourDepartureResponse> responses = departures.stream()
                .map(departure -> toDeparture(tour.getTourId(), departure))
                .toList();

        return new TourResponse(
                tour.getTourId(),
                tour.getTourName(),
                firstSegment(tour.getTourDescription()),
                tour.getTourStatus(),
                accommodation == null ? null : accommodation.getAccommodationName(),
                accommodation == null ? null : accommodation.getRating(),
                accommodation == null ? null : accommodation.getAccommodationType(),
                guide == null ? null : guide.getTourGuideName(),
                guide == null ? null : guide.getTourGuidePhone(),
                tour.getTourImages().stream().map(TourImage::getImageUrl).toList(),
                itinerary(tour.getTourDescription()),
                tour.getDestinations().stream().map(TouristDestination::getDestinationName).toList(),
                tour.getVehicles().stream().map(TravelVehicle::getVehicleType).toList(),
                responses
        );
    }

    public static TourDepartureResponse toDeparture(Long tourId, TourDeparture departure) {
        return new TourDepartureResponse(
                departure.getDepartureId(),
                tourId,
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

    private static String firstImageUrl(Tour tour) {
        return tour.getTourImages().stream()
                .map(TourImage::getImageUrl)
                .findFirst()
                .orElse(null);
    }

    private static List<String> itinerary(String description) {
        if (description == null || description.isBlank()) {
            return List.of();
        }
        return Arrays.stream(description.split("\\|"))
                .map(String::trim)
                .filter(segment -> !segment.isEmpty())
                .toList();
    }

    private static String firstSegment(String description) {
        return itinerary(description).stream().findFirst().orElse(null);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 1) + "…";
    }
}
