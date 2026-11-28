package com.j2ee.tour.mapper;

import com.j2ee.tour.dto.cache.TourCardContent;
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

    public static TourSummaryResponse toSummary(Tour tour, TourStatsProjection stats, String imageUrl) {
        return toSummary(toCardContent(tour, imageUrl), stats);
    }

    public static TourCardContent toCardContent(Tour tour, String imageUrl) {
        return new TourCardContent(
                tour.getTourId(),
                tour.getTourName(),
                truncate(firstSegment(tour.getTourDescription()), SUMMARY_DESCRIPTION_LENGTH),
                tour.getTourStatus(),
                imageUrl);
    }

    public static TourSummaryResponse toSummary(TourCardContent content, TourStatsProjection stats) {
        return new TourSummaryResponse(
                content.id(),
                content.name(),
                content.description(),
                content.status(),
                content.imageUrl(),
                stats == null ? null : stats.getPriceFrom(),
                stats == null ? null : stats.getNextDepartureDate(),
                stats == null || stats.getSeatsAvailable() == null ? 0 : stats.getSeatsAvailable().intValue(),
                stats == null || stats.getDepartureCount() == null ? 0 : stats.getDepartureCount().intValue()
        );
    }

    public static TourResponse toDetail(Tour tour, List<TourDeparture> departures) {
        return toResponse(tour, departures, firstSegment(tour.getTourDescription()));
    }

    /** Admin edit view: keeps the full description instead of the first segment. */
    public static TourResponse toAdminDetail(Tour tour, List<TourDeparture> departures) {
        return toResponse(tour, departures, tour.getTourDescription());
    }

    private static TourResponse toResponse(Tour tour, List<TourDeparture> departures,
                                           String description) {
        Accommodation accommodation = tour.getAccommodation();
        TourGuide guide = tour.getTourGuide();

        List<TourDepartureResponse> responses = departures.stream()
                .map(departure -> toDeparture(tour.getTourId(), departure))
                .toList();

        return new TourResponse(
                tour.getTourId(),
                tour.getTourName(),
                description,
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
                responses,
                tour.getTourDurationDays(),
                accommodation == null ? null : accommodation.getAccommodationId(),
                guide == null ? null : guide.getTourGuideId(),
                tour.getPromotion() == null ? null : tour.getPromotion().getPromotionId(),
                tour.getDestinations().stream().map(TouristDestination::getDestinationId).toList(),
                tour.getVehicles().stream().map(TravelVehicle::getVehicleId).toList()
        );
    }

    public static TourResponse withDepartures(TourResponse content, List<TourDepartureResponse> departures) {
        return new TourResponse(
                content.id(),
                content.name(),
                content.description(),
                content.status(),
                content.accommodationName(),
                content.accommodationRating(),
                content.accommodationType(),
                content.guideName(),
                content.guidePhone(),
                content.imageUrls(),
                content.itinerary(),
                content.destinationNames(),
                content.vehicleTypes(),
                departures,
                content.durationDays(),
                content.accommodationId(),
                content.tourGuideId(),
                content.promotionId(),
                content.destinationIds(),
                content.vehicleIds());
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
