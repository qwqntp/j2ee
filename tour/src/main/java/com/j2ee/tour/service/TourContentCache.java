package com.j2ee.tour.service;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.dto.cache.TourCardContent;
import com.j2ee.tour.dto.response.TourResponse;
import com.j2ee.tour.mapper.TourMapper;
import com.j2ee.tour.model.Tour;
import com.j2ee.tour.repository.TourImageRepository;
import com.j2ee.tour.repository.TourRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class TourContentCache {

    private static final String PUBLIC_TOUR_STATUS = "OPEN";

    private final TourRepository tourRepository;
    private final TourImageRepository tourImageRepository;

    public TourContentCache(TourRepository tourRepository, TourImageRepository tourImageRepository) {
        this.tourRepository = tourRepository;
        this.tourImageRepository = tourImageRepository;
    }

    @Cacheable(cacheNames = "tours", key = "'v2:catalog:' + #tourIds.toString()",
            condition = "#cacheable && !#tourIds.isEmpty()")
    public TourCardCatalog getCatalogContent(List<Long> tourIds, boolean cacheable) {
        if (tourIds.isEmpty()) {
            return new TourCardCatalog(List.of());
        }
        Map<Long, Tour> toursById = tourRepository.findAllById(tourIds).stream()
                .collect(Collectors.toMap(Tour::getTourId, Function.identity()));
        Map<Long, String> firstImages = tourImageRepository.findImageUrlsByTourIds(tourIds).stream()
                .collect(Collectors.toMap(
                        TourImageRepository.TourImageProjection::getTourId,
                        TourImageRepository.TourImageProjection::getImageUrl,
                        (first, second) -> first));

        // findAllById does not preserve order; the requested ID list is the source of truth.
        List<TourCardContent> items = tourIds.stream()
                .map(toursById::get)
                .filter(Objects::nonNull)
                .map(tour -> TourMapper.toCardContent(tour, firstImages.get(tour.getTourId())))
                .toList();
        return new TourCardCatalog(items);
    }

    @Cacheable(cacheNames = "tours", key = "'v2:detail:' + #tourId")
    public TourResponse getDetailContent(Long tourId) {
        Tour tour = tourRepository.findByTourIdAndTourStatus(tourId, PUBLIC_TOUR_STATUS)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Tour not found"));
        // Mapping inside the transaction realizes lazy relations before the value is cached.
        return TourMapper.toDetail(tour, List.of());
    }

    public record TourCardCatalog(List<TourCardContent> items) {
        public TourCardCatalog {
            // Deserialization supplies a mutable list; the cached contract is immutable.
            items = List.copyOf(items);
        }
    }
}
