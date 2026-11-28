package com.j2ee.tour.service;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.cache.TourCardContent;
import com.j2ee.tour.dto.request.TourRequest;
import com.j2ee.tour.dto.request.TourSearchCriteria;
import com.j2ee.tour.dto.response.TourDepartureResponse;
import com.j2ee.tour.dto.response.TourDestinationOptionResponse;
import com.j2ee.tour.dto.response.TourResponse;
import com.j2ee.tour.dto.response.TourSummaryResponse;
import com.j2ee.tour.mapper.TourMapper;
import com.j2ee.tour.model.Tour;
import com.j2ee.tour.model.TourDeparture;
import com.j2ee.tour.model.TourGuide;
import com.j2ee.tour.model.TourImage;
import com.j2ee.tour.model.TouristDestination;
import com.j2ee.tour.model.TravelVehicle;
import com.j2ee.tour.repository.AccommodationRepository;
import com.j2ee.tour.repository.PromotionRepository;
import com.j2ee.tour.repository.TourDepartureRepository;
import com.j2ee.tour.repository.TourGuideRepository;
import com.j2ee.tour.repository.TourImageRepository;
import com.j2ee.tour.repository.TourRepository;
import com.j2ee.tour.repository.TourStatsProjection;
import com.j2ee.tour.repository.TouristDestinationRepository;
import com.j2ee.tour.repository.TravelVehicleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class TourService {

    private static final String PUBLIC_TOUR_STATUS = "OPEN";
    private static final List<String> PUBLIC_DEPARTURE_STATUSES = List.of("OPEN", "FULL");

    private final TourRepository tourRepository;
    private final TourDepartureRepository departureRepository;
    private final Clock clock;
    private final AccommodationRepository accommodationRepository;
    private final TourGuideRepository tourGuideRepository;
    private final GuideOverlapValidator overlapValidator;
    private final PromotionRepository promotionRepository;
    private final TouristDestinationRepository destinationRepository;
    private final TravelVehicleRepository vehicleRepository;
    private final TourImageRepository tourImageRepository;
    private final TourContentCache tourContentCache;
    private final TourCacheInvalidator cacheInvalidator;

    public TourService(TourRepository tourRepository,
                       TourDepartureRepository departureRepository,
                       Clock clock,
                       AccommodationRepository accommodationRepository,
                       TourGuideRepository tourGuideRepository,
                       PromotionRepository promotionRepository,
                       TouristDestinationRepository destinationRepository,
                       TravelVehicleRepository vehicleRepository,
                       TourImageRepository tourImageRepository,
                       GuideOverlapValidator overlapValidator,
                       TourContentCache tourContentCache,
                       TourCacheInvalidator cacheInvalidator) {
        this.tourRepository = tourRepository;
        this.departureRepository = departureRepository;
        this.clock = clock;
        this.accommodationRepository = accommodationRepository;
        this.tourGuideRepository = tourGuideRepository;
        this.promotionRepository = promotionRepository;
        this.destinationRepository = destinationRepository;
        this.vehicleRepository = vehicleRepository;
        this.tourImageRepository = tourImageRepository;
        this.overlapValidator = overlapValidator;
        this.tourContentCache = tourContentCache;
        this.cacheInvalidator = cacheInvalidator;
    }

    /**
     * Only stable catalogue cards are cached, and only for the plain query: filter
     * combinations are client-controlled and unbounded. Membership, ordering,
     * totals, stats and seats stay live on every request.
     */
    public PageResponse<TourSummaryResponse> listTours(int page, int size, TourSearchCriteria criteria) {
        TourSearchCriteria normalized = normalize(criteria);
        validate(normalized);

        Pageable pageable = PageRequest.of(page, size);
        // One request time for both the membership search and the aggregate query.
        LocalDateTime now = now();
        Page<Long> idPage = tourRepository.searchTourIds(normalized, now, pageable);
        List<Long> tourIds = idPage.getContent();
        if (tourIds.isEmpty()) {
            return new PageResponse<>(List.of(), idPage.getNumber(), idPage.getSize(),
                    idPage.getTotalElements(), idPage.getTotalPages(), idPage.isLast(),
                    idPage.hasNext(), idPage.hasPrevious());
        }

        List<TourCardContent> cards = tourContentCache
                .getCatalogContent(tourIds, normalized.isUnfiltered())
                .items();
        Map<Long, TourStatsProjection> stats = departureRepository
                .findStatsByTourIds(tourIds, now)
                .stream()
                .collect(Collectors.toMap(TourStatsProjection::getTourId, Function.identity()));

        // The cached cards and the stats are joined, never cached as a response.
        List<TourSummaryResponse> content = cards.stream()
                .map(card -> TourMapper.toSummary(card, stats.get(card.id())))
                .toList();

        return new PageResponse<>(content, idPage.getNumber(), idPage.getSize(),
                idPage.getTotalElements(), idPage.getTotalPages(), idPage.isLast(),
                idPage.hasNext(), idPage.hasPrevious());
    }

    private TourSearchCriteria normalize(TourSearchCriteria criteria) {
        return new TourSearchCriteria(
                sanitizeKeyword(criteria.keyword()),
                criteria.destinationId(),
                criteria.minPrice(),
                criteria.maxPrice(),
                criteria.startFrom(),
                criteria.startTo(),
                criteria.minDays(),
                criteria.maxDays(),
                criteria.sort());
    }

    private void validate(TourSearchCriteria criteria) {
        if (criteria.minPrice() != null && criteria.maxPrice() != null
                && criteria.minPrice().compareTo(criteria.maxPrice()) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "minPrice must be less than or equal to maxPrice");
        }
        if (criteria.startFrom() != null && criteria.startTo() != null
                && criteria.startFrom().isAfter(criteria.startTo())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "startFrom must be on or before startTo");
        }
        if (criteria.minDays() != null && criteria.maxDays() != null
                && criteria.minDays() > criteria.maxDays()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "minDays must be less than or equal to maxDays");
        }
    }

    /**
     * Options for the public catalogue filter. Not cached: destination rows are edited by
     * the admin CRUD path, which schedules content invalidation after commit.
     */
    public List<TourDestinationOptionResponse> listDestinations() {
        return destinationRepository.findAllByOrderByDestinationNameAsc().stream()
                .map(TourDestinationOptionResponse::from)
                .toList();
    }

    public TourResponse getTour(Long tourId) {
        assertPublic(tourId);
        // Static content may be warm; visibility and departures are never cached.
        TourResponse content = tourContentCache.getDetailContent(tourId);
        List<TourDepartureResponse> departures = upcomingDepartures(tourId).stream()
                .map(departure -> TourMapper.toDeparture(tourId, departure))
                .toList();
        return TourMapper.withDepartures(content, departures);
    }

    public List<TourDepartureResponse> getDepartures(Long tourId) {
        assertPublic(tourId);
        return upcomingDepartures(tourId).stream()
                .map(departure -> TourMapper.toDeparture(tourId, departure))
                .toList();
    }

    private void assertPublic(Long tourId) {
        if (!tourRepository.existsByTourIdAndTourStatus(tourId, PUBLIC_TOUR_STATUS)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Tour not found");
        }
    }

    public PageResponse<TourResponse> list(Pageable pageable) {
        return PageResponse.from(tourRepository.findAll(pageable),
                tour -> TourMapper.toAdminDetail(tour,
                        tour.getDepartures() == null ? List.of() : tour.getDepartures()));
    }

    public TourResponse get(Long id) {
        return TourResponse.from(find(id));
    }

    @Transactional
    public TourResponse create(TourRequest request) {
        Tour tour = new Tour();
        apply(tour, request);
        Tour saved = tourRepository.save(tour);
        syncImages(saved, request.imageUrls());
        TourResponse response = TourResponse.from(saved);
        cacheInvalidator.invalidateAfterCommit();
        return response;
    }

    @Transactional
    public TourResponse update(Long id, TourRequest request) {
        Tour tour = find(id);
        assertGuideChangeSafe(tour, request);
        apply(tour, request);
        Tour saved = tourRepository.save(tour);
        syncImages(saved, request.imageUrls());
        TourResponse response = TourResponse.from(saved);
        cacheInvalidator.invalidateAfterCommit();
        return response;
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
        cacheInvalidator.invalidateAfterCommit();
    }

    /**
     * Assigning/reassigning a guide affects every live departure of the tour.
     * Validates BEFORE mutating the tour: running it after apply() would let
     * the dirty-checked guide assignment flush early inside the JPQL query and
     * surface a raw trigger exception instead of a friendly 409. An unchanged
     * guide re-submission is a no-op.
     */
    private void assertGuideChangeSafe(Tour tour, TourRequest request) {
        Long candidateId = request.tourGuideId();
        if (candidateId == null) {
            return;
        }
        TourGuide current = tour.getTourGuide();
        if (current != null && candidateId.equals(current.getTourGuideId())) {
            return;
        }
        for (TourDeparture departure : departureRepository
                .findByTourTourIdAndDepartureStatusNot(tour.getTourId(), "CANCELLED")) {
            overlapValidator.assertGuideAvailable(
                    candidateId,
                    departure.getDepartureStartDate(),
                    departure.getDepartureEndDate(),
                    departure.getDepartureId());
        }
    }

    private List<TourDeparture> upcomingDepartures(Long tourId) {
        return departureRepository
                .findByTourTourIdAndDepartureStatusInAndDepartureStartDateAfterOrderByDepartureStartDateAsc(
                        tourId, PUBLIC_DEPARTURE_STATUSES, now());
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

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private String sanitizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String cleaned = keyword.trim().replace("%", "").replace("_", "");
        return cleaned.isEmpty() ? null : cleaned;
    }

    private Tour find(Long id) {
        return tourRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Tour not found"));
    }
}
