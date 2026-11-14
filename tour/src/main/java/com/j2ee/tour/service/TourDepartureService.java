package com.j2ee.tour.service;

import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.TourDepartureRequest;
import com.j2ee.tour.dto.response.TourDepartureResponse;
import com.j2ee.tour.model.Booking;
import com.j2ee.tour.model.Payment;
import com.j2ee.tour.model.Tour;
import com.j2ee.tour.model.TourDeparture;
import com.j2ee.tour.model.TourGuide;
import com.j2ee.tour.repository.BookingRepository;
import com.j2ee.tour.repository.PaymentRepository;
import com.j2ee.tour.repository.TourDepartureRepository;
import com.j2ee.tour.repository.TourRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
public class TourDepartureService {
    private static final Set<String> CANCELLABLE_BOOKING_STATUSES =
            Set.of("PENDING", "PAID", "CONFIRMED");

    private final TourDepartureRepository departureRepository;
    private final TourRepository tourRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final RefundService refundService;
    private final EmailService emailService;
    private final GuideOverlapValidator overlapValidator;

    public TourDepartureService(TourDepartureRepository departureRepository,
                                TourRepository tourRepository,
                                BookingRepository bookingRepository,
                                PaymentRepository paymentRepository,
                                RefundService refundService,
                                EmailService emailService,
                                GuideOverlapValidator overlapValidator) {
        this.departureRepository = departureRepository;
        this.tourRepository = tourRepository;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.refundService = refundService;
        this.emailService = emailService;
        this.overlapValidator = overlapValidator;
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
    @CacheEvict(cacheNames = "tours", allEntries = true)
    public TourDepartureResponse create(TourDepartureRequest request) {
        TourDeparture departure = new TourDeparture();
        departure.setDepartureCreatedAt(LocalDateTime.now());
        apply(departure, request, 0);
        return TourDepartureResponse.from(departureRepository.save(departure));
    }

    @Transactional
    @CacheEvict(cacheNames = "tours", allEntries = true)
    public TourDepartureResponse update(Long id, TourDepartureRequest request) {
        TourDeparture departure = departureRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NoSuchElementException("Tour departure not found"));
        String previousStatus = departure.getDepartureStatus();
        apply(departure, request, departure.getDepartureBookedSlots());
        TourDeparture saved = departureRepository.save(departure);
        if (!"CANCELLED".equals(previousStatus) && "CANCELLED".equals(saved.getDepartureStatus())) {
            cascadeCancel(saved);
        }
        return TourDepartureResponse.from(saved);
    }

    /**
     * Cancelling a departure cancels its live bookings, frees the seats and
     * reserves a refund for every successful payment that is not fully
     * refunded yet. Completed trips and already-cancelled bookings are kept.
     */
    private void cascadeCancel(TourDeparture departure) {
        List<Booking> bookings = bookingRepository
                .findByDepartureDepartureId(departure.getDepartureId());
        for (Booking booking : bookings) {
            if (!CANCELLABLE_BOOKING_STATUSES.contains(booking.getBookingStatus())) {
                continue;
            }
            booking.setBookingStatus("CANCELLED");
            bookingRepository.save(booking);
            for (Payment payment : paymentRepository
                    .findByBookingBookingId(booking.getBookingId())) {
                if ("SUCCESS".equals(payment.getPaymentStatus())) {
                    refundService.requestRemaining(payment.getPaymentId(),
                            "Departure cancelled");
                }
            }
            if (booking.getAccount() != null
                    && booking.getAccount().getAccountEmail() != null) {
                emailService.sendBookingCancellation(
                        booking.getAccount().getAccountEmail(),
                        booking.getBookingCode());
            }
        }
        departure.setDepartureBookedSlots(0);
        departureRepository.save(departure);
    }

    @Transactional
    @CacheEvict(cacheNames = "tours", allEntries = true)
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

        TourGuide guide = tour.getTourGuide();
        if (guide != null && !"CANCELLED".equals(status)) {
            overlapValidator.assertGuideAvailable(guide.getTourGuideId(),
                    request.startDate(), request.endDate(), departure.getDepartureId());
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
