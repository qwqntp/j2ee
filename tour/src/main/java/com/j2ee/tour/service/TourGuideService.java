package com.j2ee.tour.service;

import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.TourGuideRequest;
import com.j2ee.tour.dto.response.TourGuideResponse;
import com.j2ee.tour.model.TourGuide;
import com.j2ee.tour.repository.TourGuideRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class TourGuideService {
    private final TourGuideRepository tourGuideRepository;

    public TourGuideService(TourGuideRepository tourGuideRepository) {
        this.tourGuideRepository = tourGuideRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<TourGuideResponse> list(Pageable pageable) {
        return PageResponse.from(tourGuideRepository.findAll(pageable), TourGuideResponse::from);
    }

    @Transactional(readOnly = true)
    public TourGuideResponse get(Long id) {
        return TourGuideResponse.from(find(id));
    }

    @Transactional
    public TourGuideResponse create(TourGuideRequest request) {
        TourGuide guide = new TourGuide();
        apply(guide, request);
        return TourGuideResponse.from(tourGuideRepository.save(guide));
    }

    @Transactional
    public TourGuideResponse update(Long id, TourGuideRequest request) {
        TourGuide guide = find(id);
        apply(guide, request);
        return TourGuideResponse.from(tourGuideRepository.save(guide));
    }

    @Transactional
    public void delete(Long id) {
        tourGuideRepository.delete(find(id));
    }

    private void apply(TourGuide guide, TourGuideRequest request) {
        guide.setTourGuideName(request.name());
        guide.setTourGuideEmail(request.email());
        guide.setTourGuidePhone(request.phone());
        guide.setTourGuideExperienceYears(request.experienceYears());
    }

    private TourGuide find(Long id) {
        return tourGuideRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Tour guide not found"));
    }
}
