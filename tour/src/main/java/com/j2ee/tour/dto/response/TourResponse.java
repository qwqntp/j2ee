package com.j2ee.tour.dto.response;

import com.j2ee.tour.model.Tour;
import com.j2ee.tour.model.TourImage;

import java.util.List;

public record TourResponse(
        Long id,
        String name,
        String description,
        Integer durationDays,
        String status,
        Long accommodationId,
        Long tourGuideId,
        Long promotionId,
        List<String> imageUrls,
        List<TourDepartureResponse> departures,
        List<Long> destinationIds,
        List<Long> vehicleIds
) {
    public static TourResponse from(Tour tour) {
        List<String> imageUrls = tour.getTourImages() == null
                ? List.of()
                : tour.getTourImages().stream().map(TourImage::getImageUrl).toList();
        List<TourDepartureResponse> departures = tour.getDepartures() == null
                ? List.of()
                : tour.getDepartures().stream().map(TourDepartureResponse::from).toList();
        List<Long> destinationIds = tour.getDestinations() == null
                ? List.of()
                : tour.getDestinations().stream().map(destination -> destination.getDestinationId()).toList();
        List<Long> vehicleIds = tour.getVehicles() == null
                ? List.of()
                : tour.getVehicles().stream().map(vehicle -> vehicle.getVehicleId()).toList();

        return new TourResponse(
                tour.getTourId(),
                tour.getTourName(),
                tour.getTourDescription(),
                tour.getTourDurationDays(),
                tour.getTourStatus(),
                tour.getAccommodation() == null ? null : tour.getAccommodation().getAccommodationId(),
                tour.getTourGuide() == null ? null : tour.getTourGuide().getTourGuideId(),
                tour.getPromotion() == null ? null : tour.getPromotion().getPromotionId(),
                imageUrls,
                departures,
                destinationIds,
                vehicleIds
        );
    }
}
