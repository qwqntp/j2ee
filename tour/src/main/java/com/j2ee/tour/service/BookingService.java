package com.j2ee.tour.service;

import com.j2ee.tour.dto.request.CreateBookingRequest;
import com.j2ee.tour.dto.response.BookingResponse;
import com.j2ee.tour.model.Booking;
import com.j2ee.tour.model.TourDeparture;
import com.j2ee.tour.repository.BookingRepository;
import com.j2ee.tour.repository.TourDepartureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class BookingService {
    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final BookingRepository bookingRepository;
    private final TourDepartureRepository departureRepository;

    public BookingService(BookingRepository bookingRepository, TourDepartureRepository departureRepository) {
        this.bookingRepository = bookingRepository;
        this.departureRepository = departureRepository;
    }

    @Transactional
    public BookingResponse create(CreateBookingRequest request) {
        TourDeparture departure = departureRepository.findByIdForUpdate(request.departureId())
                .orElseThrow(() -> new NoSuchElementException("Departure not found"));

        if (!"OPEN".equals(departure.getDepartureStatus())) {
            throw new IllegalStateException("Departure is not open for booking");
        }
        int available = departure.getSeatsAvailable();
        if (request.guestCount() > available) {
            throw new IllegalStateException("Not enough seats available");
        }

        BigDecimal subtotal = departure.getDeparturePrice().multiply(BigDecimal.valueOf(request.guestCount()));
        Booking booking = new Booking();
        booking.setBookingCode("BK-" + LocalDateTime.now().format(CODE_DATE) + "-" + UUID.randomUUID().toString().substring(0, 4));
        booking.setBookingCreatedAt(LocalDateTime.now());
        booking.setBookingGuestCount(request.guestCount());
        booking.setBookingUnitPrice(departure.getDeparturePrice());
        booking.setBookingSubtotal(subtotal);
        booking.setBookingDiscountAmount(BigDecimal.ZERO);
        booking.setBookingTotalAmount(subtotal);
        booking.setBookingStatus("PENDING");
        booking.setBookingNote(request.note());

        departure.setDepartureBookedSlots(departure.getDepartureBookedSlots() + request.guestCount());
        if (departure.getDepartureBookedSlots().equals(departure.getDepartureCapacity())) {
            departure.setDepartureStatus("FULL");
        }
        departureRepository.save(departure);
        return BookingResponse.from(bookingRepository.save(booking));
    }
}