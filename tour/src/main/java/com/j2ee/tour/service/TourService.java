package com.j2ee.tour.service;

import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.TourRequest;
import com.j2ee.tour.dto.response.TourResponse;
import com.j2ee.tour.model.Tour;
import com.j2ee.tour.model.TourImage;
import com.j2ee.tour.model.TouristDestination;
import com.j2ee.tour.model.TravelVehicle;
import com.j2ee.tour.repository.AccommodationRepository;
import com.j2ee.tour.repository.PromotionRepository;
import com.j2ee.tour.repository.TourDepartureRepository;
import com.j2ee.tour.repository.TourGuideRepository;
import com.j2ee.tour.repository.TourImageRepository;
import com.j2ee.tour.repository.TourRepository;
import com.j2ee.tour.repository.TouristDestinationRepository;
import com.j2ee.tour.repository.TravelVehicleRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class TourService {
    private final TourRepository tourRepository;
    private final AccommodationRepository accommodationRepository;
    private final TourGuideRepository tourGuideRepository;
    private final PromotionRepository promotionRepository;
    private final TouristDestinationRepository destinationRepository;
    private final TravelVehicleRepository vehicleRepository;
    private final TourImageRepository tourImageRepository;
    private final TourDepartureRepository departureRepository;

    public TourService(TourRepository tourRepository,
                       AccommodationRepository accommodationRepository,
                       TourGuideRepository tourGuideRepository,
                       PromotionRepository promotionRepository,
                       TouristDestinationRepository destinationRepository,
                       TravelVehicleRepository vehicleRepository,
                       TourImageRepository tourImageRepository,
                       TourDepartureRepository departureRepository) {
        this.tourRepository = tourRepository;
        this.accommodationRepository = accommodationRepository;
        this.tourGuideRepository = tourGuideRepository;
        this.promotionRepository = promotionRepository;
        this.destinationRepository = destinationRepository;
        this.vehicleRepository = vehicleRepository;
        this.tourImageRepository = tourImageRepository;
        this.departureRepository = departureRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<TourResponse> list(Pageable pageable) {
        return PageResponse.from(tourRepository.findAll(pageable), TourResponse::from);
    }

    @Transactional(readOnly = true)
    public TourResponse get(Long id) {
        return TourResponse.from(find(id));
    }

    @Transactional
    public TourResponse create(TourRequest request) {
        Tour tour = new Tour();
        apply(tour, request);
        Tour saved = tourRepository.save(tour);
        syncImages(saved, request.imageUrls());
        return TourResponse.from(saved);
    }

    @Transactional
    public TourResponse update(Long id, TourRequest request) {
        Tour tour = find(id);
        apply(tour, request);
        Tour saved = tourRepository.save(tour);
        syncImages(saved, request.imageUrls());
        return TourResponse.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        Tour tour = find(id);
        if (departureRepository.existsByTourTourId(id)) {
            throw new IllegalStateException("Cannot delete a tour that still has departures");
        }
        tourImageRepository.deleteAll(tour.getTourImages());
        tour.getTourImages().clear();
        tour.getDestinations().clear();
        tour.getVehicles().clear();
        tourRepository.save(tour);
        tourRepository.delete(tour);
    }

    private void apply(Tour tour, TourRequest request) {
        tour.setTourName(request.name());
        tour.setTourDescription(request.description());
        tour.setTourDurationDays(request.durationDays());
        tour.setTourStatus(request.status());
        tour.setAccommodation(request.accommodationId() == null ? null
                : accommodationRepository.findById(request.accommodationId())
                .orElseThrow(() -> new NoSuchElementException("Accommodation not found")));
        tour.setTourGuide(request.tourGuideId() == null ? null
                : tourGuideRepository.findById(request.tourGuideId())
                .orElseThrow(() -> new NoSuchElementException("Tour guide not found")));
        tour.setPromotion(request.promotionId() == null ? null
                : promotionRepository.findById(request.promotionId())
                .orElseThrow(() -> new NoSuchElementException("Promotion not found")));
        tour.setDestinations(resolveDestinations(request.destinationIds()));
        tour.setVehicles(resolveVehicles(request.vehicleIds()));
    }

    private List<TouristDestination> resolveDestinations(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        List<TouristDestination> destinations = destinationRepository.findAllById(ids);
        if (destinations.size() != ids.stream().distinct().count()) {
            throw new NoSuchElementException("One or more tourist destinations were not found");
        }
        return new ArrayList<>(destinations);
    }

    private List<TravelVehicle> resolveVehicles(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        List<TravelVehicle> vehicles = vehicleRepository.findAllById(ids);
        if (vehicles.size() != ids.stream().distinct().count()) {
            throw new NoSuchElementException("One or more travel vehicles were not found");
        }
        return new ArrayList<>(vehicles);
    }

    private void syncImages(Tour tour, List<String> imageUrls) {
        tourImageRepository.deleteAll(tour.getTourImages());
        tour.getTourImages().clear();
        if (imageUrls == null) {
            return;
        }
        for (String imageUrl : imageUrls) {
            TourImage image = new TourImage();
            image.setImageUrl(imageUrl);
            image.setTour(tour);
            tour.getTourImages().add(tourImageRepository.save(image));
        }
    }

    private Tour find(Long id) {
        return tourRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Tour not found"));
    }
}
