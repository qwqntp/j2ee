package com.j2ee.tour.service;

import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.TourImageRequest;
import com.j2ee.tour.dto.response.TourImageResponse;
import com.j2ee.tour.model.Tour;
import com.j2ee.tour.model.TourImage;
import com.j2ee.tour.repository.TourImageRepository;
import com.j2ee.tour.repository.TourRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class TourImageService {
    private final TourImageRepository tourImageRepository;
    private final TourRepository tourRepository;
    private final TourCacheInvalidator cacheInvalidator;

    public TourImageService(TourImageRepository tourImageRepository, TourRepository tourRepository,
                            TourCacheInvalidator cacheInvalidator) {
        this.tourImageRepository = tourImageRepository;
        this.tourRepository = tourRepository;
        this.cacheInvalidator = cacheInvalidator;
    }

    @Transactional(readOnly = true)
    public PageResponse<TourImageResponse> list(Pageable pageable) {
        return PageResponse.from(tourImageRepository.findAll(pageable), TourImageResponse::from);
    }

    @Transactional(readOnly = true)
    public TourImageResponse get(Long id) {
        return TourImageResponse.from(find(id));
    }

    @Transactional
    public TourImageResponse create(TourImageRequest request) {
        TourImage image = new TourImage();
        apply(image, request);
        TourImageResponse response = TourImageResponse.from(tourImageRepository.save(image));
        cacheInvalidator.invalidateAfterCommit();
        return response;
    }

    @Transactional
    public TourImageResponse update(Long id, TourImageRequest request) {
        TourImage image = find(id);
        apply(image, request);
        TourImageResponse response = TourImageResponse.from(tourImageRepository.save(image));
        cacheInvalidator.invalidateAfterCommit();
        return response;
    }

    @Transactional
    public void delete(Long id) {
        tourImageRepository.delete(find(id));
        cacheInvalidator.invalidateAfterCommit();
    }

    private void apply(TourImage image, TourImageRequest request) {
        Tour tour = tourRepository.findById(request.tourId())
                .orElseThrow(() -> new NoSuchElementException("Tour not found"));
        image.setImageUrl(request.imageUrl());
        image.setTour(tour);
    }

    private TourImage find(Long id) {
        return tourImageRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Tour image not found"));
    }
}
