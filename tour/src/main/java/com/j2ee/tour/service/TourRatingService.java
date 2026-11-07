package com.j2ee.tour.service;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.dto.request.TourRatingRequest;
import com.j2ee.tour.dto.response.TourRatingPageResponse;
import com.j2ee.tour.dto.response.TourRatingResponse;
import com.j2ee.tour.model.Booking;
import com.j2ee.tour.model.TourRating;
import com.j2ee.tour.repository.BookingRepository;
import com.j2ee.tour.repository.TourRatingRepository;
import com.j2ee.tour.repository.TourRepository;
import com.j2ee.tour.security.AuthPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;

@Service
public class TourRatingService {

    private final TourRatingRepository ratingRepository;
    private final BookingRepository bookingRepository;
    private final TourRepository tourRepository;
    private final Clock clock;

    public TourRatingService(TourRatingRepository ratingRepository,
                             BookingRepository bookingRepository,
                             TourRepository tourRepository,
                             Clock clock) {
        this.ratingRepository = ratingRepository;
        this.bookingRepository = bookingRepository;
        this.tourRepository = tourRepository;
        this.clock = clock;
    }

    @Transactional
    public TourRatingResponse create(Long tourId, TourRatingRequest request,
                                     AuthPrincipal principal) {
        tourRepository.findById(tourId)
                .orElseThrow(() -> new NoSuchElementException("Tour not found"));
        Booking booking = bookingRepository.findById(request.bookingId())
                .orElseThrow(() -> new NoSuchElementException("Booking not found"));

        if (booking.getAccount() == null
                || !principal.id().equals(booking.getAccount().getAccountId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Booking not found");
        }
        if (!"PAID".equals(booking.getBookingStatus())
                && !"COMPLETED".equals(booking.getBookingStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Only paid or completed bookings can be rated");
        }
        if (booking.getDeparture() == null
                || booking.getDeparture().getTour() == null
                || !tourId.equals(booking.getDeparture().getTour().getTourId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Booking does not belong to this tour");
        }
        if (ratingRepository.existsByBookingBookingId(booking.getBookingId())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Booking has already been rated");
        }

        TourRating rating = new TourRating();
        rating.setTour(booking.getDeparture().getTour());
        rating.setAccount(booking.getAccount());
        rating.setBooking(booking);
        rating.setRatingValue(request.rating());
        rating.setRatingComment(request.comment());
        rating.setRatingCreatedAt(LocalDateTime.now(clock));
        return TourRatingResponse.from(ratingRepository.save(rating));
    }

    @Transactional(readOnly = true)
    public TourRatingPageResponse list(Long tourId, Pageable pageable) {
        tourRepository.findById(tourId)
                .orElseThrow(() -> new NoSuchElementException("Tour not found"));

        Pageable effective = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "ratingCreatedAt")
                                .and(Sort.by(Sort.Direction.DESC, "ratingId")));
        Page<TourRating> page = ratingRepository.findByTourTourId(tourId, effective);
        Double averageValue = ratingRepository.averageRating(tourId);
        BigDecimal average = BigDecimal.valueOf(averageValue == null ? 0.0 : averageValue)
                .setScale(2, RoundingMode.HALF_UP);

        return new TourRatingPageResponse(
                page.getContent().stream().map(TourRatingResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast(),
                page.hasNext(),
                page.hasPrevious(),
                average,
                page.getTotalElements());
    }
}
