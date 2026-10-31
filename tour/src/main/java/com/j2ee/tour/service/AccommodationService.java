package com.j2ee.tour.service;

import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.AccommodationRequest;
import com.j2ee.tour.dto.response.AccommodationResponse;
import com.j2ee.tour.model.Accommodation;
import com.j2ee.tour.repository.AccommodationRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class AccommodationService {
    private final AccommodationRepository accommodationRepository;

    public AccommodationService(AccommodationRepository accommodationRepository) {
        this.accommodationRepository = accommodationRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AccommodationResponse> list(Pageable pageable) {
        return PageResponse.from(accommodationRepository.findAll(pageable), AccommodationResponse::from);
    }

    @Transactional(readOnly = true)
    public AccommodationResponse get(Long id) {
        return AccommodationResponse.from(find(id));
    }

    @Transactional
    public AccommodationResponse create(AccommodationRequest request) {
        Accommodation accommodation = new Accommodation();
        apply(accommodation, request);
        return AccommodationResponse.from(accommodationRepository.save(accommodation));
    }

    @Transactional
    public AccommodationResponse update(Long id, AccommodationRequest request) {
        Accommodation accommodation = find(id);
        apply(accommodation, request);
        return AccommodationResponse.from(accommodationRepository.save(accommodation));
    }

    @Transactional
    public void delete(Long id) {
        accommodationRepository.delete(find(id));
    }

    private void apply(Accommodation accommodation, AccommodationRequest request) {
        accommodation.setAccommodationName(request.name());
        accommodation.setLocation(request.location());
        accommodation.setRating(request.rating());
        accommodation.setPricePerNight(request.pricePerNight());
        accommodation.setAccommodationType(request.type());
    }

    private Accommodation find(Long id) {
        return accommodationRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Accommodation not found"));
    }
}
