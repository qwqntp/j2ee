package com.j2ee.tour.service;

import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.TourDepartureRequest;
import com.j2ee.tour.dto.response.TourDepartureResponse;
import com.j2ee.tour.model.Tour;
import com.j2ee.tour.model.TourDeparture;
import com.j2ee.tour.repository.BookingRepository;
import com.j2ee.tour.repository.TourDepartureRepository;
import com.j2ee.tour.repository.TourRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

@Service
public class TourDepartureService {
    private final TourDepartureRepository departureRepository;
    private final TourRepository tourRepository;
    private final BookingRepository bookingRepository;

    public TourDepartureService(TourDepartureRepository departureRepository,
                                TourRepository tourRepository,
                                BookingRepository bookingRepository) {
        this.departureRepository = departureRepository;
        this.tourRepository = tourRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<TourDepartureResponse> list(Pageable pageable) {
        return PageResponse.from(departureRepository.findAll(pageable), TourDepartureResponse::from);
    }

    @Transactional(readOnly = true)
    public TourDepartureResponse get(Long id) {
        return TourDepartureResponse.from(find(id));
    }

    @Transactional
    public TourDepartureResponse create(TourDepartureRequest request) {
        TourDeparture departure = new TourDeparture();
        departure.setDepartureCreatedAt(LocalDateTime.now());
        apply(departure, request, 0);
        return TourDepartureResponse.from(departureRepository.save(departure));
    }

    @Transactional
    public TourDepartureResponse update(Long id, TourDepartureRequest request) {
        TourDeparture departure = departureRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NoSuchElementException("Tour departure not found"));
        apply(departure, request, departure.getDepartureBookedSlots());
        return TourDepartureResponse.from(departureRepository.save(departure));
    }

    @Transactional
    public void delete(Long id) {
        TourDeparture departure = departureRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NoSuchElementException("Tour departure not found"));
        if (bookingRepository.existsByDepartureDepartureId(id)) {
            throw new IllegalStateException("Cannot delete a departure that still has bookings");
        }
        departureRepository.delete(departure);
    }

    private void apply(TourDeparture departure, TourDepartureRequest request, int currentBookedSlots) {
        if (!request.endDate().isAfter(request.startDate())) {
            throw new IllegalStateException("Departure end date must be after start date");
        }
        int bookedSlots = request.bookedSlots() == null ? currentBookedSlots : request.bookedSlots();
        if (bookedSlots > request.capacity()) {
            throw new IllegalStateException("Booked slots cannot exceed capacity");
        }

        Tour tour = tourRepository.findById(request.tourId())
                .orElseThrow(() -> new NoSuchElementException("Tour not found"));
        String status = request.status() == null ? "OPEN" : request.status();
        if (bookedSlots == request.capacity() && "OPEN".equals(status)) {
            status = "FULL";
        }
        if (bookedSlots < request.capacity() && "FULL".equals(status)) {
            throw new IllegalStateException("A departure with available seats cannot be FULL");
        }

        departure.setTour(tour);
        departure.setDepartureStartDate(request.startDate());
        departure.setDepartureEndDate(request.endDate());
        departure.setDepartureCapacity(request.capacity());
        departure.setDepartureBookedSlots(bookedSlots);
        departure.setDeparturePrice(request.price());
        departure.setDepartureMeetingPoint(request.meetingPoint());
        departure.setDepartureStatus(status);
    }

    private TourDeparture find(Long id) {
        return departureRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Tour departure not found"));
    }
}
