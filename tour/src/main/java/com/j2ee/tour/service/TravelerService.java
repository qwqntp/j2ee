package com.j2ee.tour.service;

import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.TravelerRequest;
import com.j2ee.tour.dto.response.TravelerResponse;
import com.j2ee.tour.model.Booking;
import com.j2ee.tour.model.Traveler;
import com.j2ee.tour.repository.BookingRepository;
import com.j2ee.tour.repository.TravelerRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class TravelerService {
    private final TravelerRepository travelerRepository;
    private final BookingRepository bookingRepository;

    public TravelerService(TravelerRepository travelerRepository, BookingRepository bookingRepository) {
        this.travelerRepository = travelerRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<TravelerResponse> list(Pageable pageable) {
        return PageResponse.from(travelerRepository.findAll(pageable), TravelerResponse::from);
    }

    @Transactional(readOnly = true)
    public TravelerResponse get(Long id) {
        return TravelerResponse.from(find(id));
    }

    @Transactional
    public TravelerResponse create(TravelerRequest request) {
        Traveler traveler = new Traveler();
        apply(traveler, request);
        return TravelerResponse.from(travelerRepository.save(traveler));
    }

    @Transactional
    public TravelerResponse update(Long id, TravelerRequest request) {
        Traveler traveler = find(id);
        apply(traveler, request);
        return TravelerResponse.from(travelerRepository.save(traveler));
    }

    @Transactional
    public void delete(Long id) {
        travelerRepository.delete(find(id));
    }

    private void apply(Traveler traveler, TravelerRequest request) {
        Booking booking = bookingRepository.findById(request.bookingId())
                .orElseThrow(() -> new NoSuchElementException("Booking not found"));
        traveler.setBooking(booking);
        traveler.setTravelerName(request.name());
        traveler.setTravelerDateOfBirth(request.dateOfBirth());
        traveler.setTravelerGender(request.gender());
        traveler.setTravelerIdDocument(request.idDocument());
    }

    private Traveler find(Long id) {
        return travelerRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Traveler not found"));
    }
}
