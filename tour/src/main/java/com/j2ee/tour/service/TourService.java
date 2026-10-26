package com.j2ee.tour.service;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.response.TourDepartureResponse;
import com.j2ee.tour.dto.response.TourResponse;
import com.j2ee.tour.dto.response.TourSummaryResponse;
import com.j2ee.tour.mapper.TourMapper;
import com.j2ee.tour.model.Tour;
import com.j2ee.tour.repository.TourDepartureRepository;
import com.j2ee.tour.repository.TourRepository;
import com.j2ee.tour.repository.TourStatsProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TourService {

    private static final String PUBLIC_TOUR_STATUS = "OPEN";
    private static final List<String> PUBLIC_DEPARTURE_STATUSES = List.of("OPEN", "FULL");

    private final TourRepository tourRepository;
    private final TourDepartureRepository departureRepository;
    private final Clock clock;

    public PageResponse<TourSummaryResponse> listTours(int page, int size, String keyword) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "tourId"));
        String search = sanitizeKeyword(keyword);

        Page<Tour> tours = search == null
                ? tourRepository.findByTourStatus(PUBLIC_TOUR_STATUS, pageable)
                : tourRepository.findByTourStatusAndTourNameContainingIgnoreCase(
                        PUBLIC_TOUR_STATUS, search, pageable);

        if (tours.isEmpty()) {
            return PageResponse.from(tours, tour -> TourMapper.toSummary(tour, null));
        }

        List<Long> tourIds = tours.getContent().stream().map(Tour::getTourId).toList();
        Map<Long, TourStatsProjection> stats = departureRepository
                .findStatsByTourIds(tourIds, now())
                .stream()
                .collect(Collectors.toMap(TourStatsProjection::getTourId, Function.identity()));

        return PageResponse.from(tours, tour -> TourMapper.toSummary(tour, stats.get(tour.getTourId())));
    }

    public TourResponse getTour(Long tourId) {
        Tour tour = tourRepository.findByTourIdAndTourStatus(tourId, PUBLIC_TOUR_STATUS)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Tour not found"));
        return TourMapper.toDetail(tour, now());
    }

    public List<TourDepartureResponse> getDepartures(Long tourId) {
        tourRepository.findByTourIdAndTourStatus(tourId, PUBLIC_TOUR_STATUS)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Tour not found"));

        return departureRepository
                .findByTourTourIdAndDepartureStatusInAndDepartureStartDateAfterOrderByDepartureStartDateAsc(
                        tourId, PUBLIC_DEPARTURE_STATUSES, now())
                .stream()
                .map(departure -> TourMapper.toDeparture(tourId, departure))
                .toList();
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
}
