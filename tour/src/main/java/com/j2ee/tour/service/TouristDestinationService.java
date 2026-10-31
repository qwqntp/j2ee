package com.j2ee.tour.service;

import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.TouristDestinationRequest;
import com.j2ee.tour.dto.response.TouristDestinationResponse;
import com.j2ee.tour.model.TouristDestination;
import com.j2ee.tour.repository.TouristDestinationRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class TouristDestinationService {
    private final TouristDestinationRepository destinationRepository;

    public TouristDestinationService(TouristDestinationRepository destinationRepository) {
        this.destinationRepository = destinationRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<TouristDestinationResponse> list(Pageable pageable) {
        return PageResponse.from(destinationRepository.findAll(pageable), TouristDestinationResponse::from);
    }

    @Transactional(readOnly = true)
    public TouristDestinationResponse get(Long id) {
        return TouristDestinationResponse.from(find(id));
    }

    @Transactional
    public TouristDestinationResponse create(TouristDestinationRequest request) {
        TouristDestination destination = new TouristDestination();
        apply(destination, request);
        return TouristDestinationResponse.from(destinationRepository.save(destination));
    }

    @Transactional
    public TouristDestinationResponse update(Long id, TouristDestinationRequest request) {
        TouristDestination destination = find(id);
        apply(destination, request);
        return TouristDestinationResponse.from(destinationRepository.save(destination));
    }

    @Transactional
    public void delete(Long id) {
        destinationRepository.delete(find(id));
    }

    private void apply(TouristDestination destination, TouristDestinationRequest request) {
        destination.setDestinationName(request.name());
        destination.setLocation(request.location());
        destination.setDescription(request.description());
        destination.setEntryFee(request.entryFee());
    }

    private TouristDestination find(Long id) {
        return destinationRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Tourist destination not found"));
    }
}
