package com.j2ee.tour;

import com.j2ee.tour.dto.request.CreateBookingRequest;
import com.j2ee.tour.dto.request.TourRatingRequest;
import com.j2ee.tour.model.Account;
import com.j2ee.tour.model.AccountRole;
import com.j2ee.tour.model.Accommodation;
import com.j2ee.tour.model.Booking;
import com.j2ee.tour.model.Payment;
import com.j2ee.tour.model.Tour;
import com.j2ee.tour.model.TourDeparture;
import com.j2ee.tour.model.TourGuide;
import com.j2ee.tour.repository.AccountRepository;
import com.j2ee.tour.repository.AccountRoleRepository;
import com.j2ee.tour.repository.AccommodationRepository;
import com.j2ee.tour.repository.BookingRepository;
import com.j2ee.tour.repository.PaymentRepository;
import com.j2ee.tour.repository.TourDepartureRepository;
import com.j2ee.tour.repository.TourGuideRepository;
import com.j2ee.tour.repository.TourRepository;
import com.j2ee.tour.security.AuthPrincipal;
import com.j2ee.tour.service.BookingService;
import com.j2ee.tour.service.TourRatingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RatingModerationIntegrationTests {

    @Autowired private BookingService bookingService;
    @Autowired private TourRatingService ratingService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private PaymentRepository paymentRepository;

    @Autowired private AccountRoleRepository accountRoleRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AccommodationRepository accommodationRepository;
    @Autowired private TourGuideRepository tourGuideRepository;
    @Autowired private TourRepository tourRepository;
    @Autowired private TourDepartureRepository tourDepartureRepository;

    private Tour tour;
    private Booking booking;
    private AuthPrincipal principal;

    @BeforeEach
    void setUp() {
        AccountRole role = new AccountRole();
        role.setRoleName("USER");
        role = accountRoleRepository.save(role);

        Account account = new Account();
        account.setUsername("rating-tester");
        account.setPassword("secret");
        account.setAccountEmail("rating-tester@example.com");
        account.setEmailVerified(1);
        account.setStatus(1);
        account.setAccountCreatedAt(LocalDateTime.now());
        account.setAccountUpdatedAt(LocalDateTime.now());
        account.setRole(role);
        account = accountRepository.save(account);
        principal = new AuthPrincipal(account.getAccountId(), account.getUsername(), role.getRoleName());

        Accommodation accommodation = new Accommodation();
        accommodation.setAccommodationName("Hotel");
        accommodation.setLocation("HCM");
        accommodation = accommodationRepository.save(accommodation);

        TourGuide guide = new TourGuide();
        guide.setTourGuideName("Guide");
        guide.setTourGuideExperienceYears(1);
        guide = tourGuideRepository.save(guide);

        tour = new Tour();
        tour.setTourName("Rated tour");
        tour.setTourStatus("OPEN");
        tour.setAccommodation(accommodation);
        tour.setTourGuide(guide);
        tour = tourRepository.save(tour);

        TourDeparture departure = new TourDeparture();
        departure.setTour(tour);
        departure.setDepartureStartDate(LocalDateTime.now().plusDays(1));
        departure.setDepartureEndDate(LocalDateTime.now().plusDays(3));
        departure.setDepartureCapacity(2);
        departure.setDepartureBookedSlots(0);
        departure.setDeparturePrice(new BigDecimal("500.00"));
        departure.setDepartureStatus("OPEN");
        departure.setDepartureCreatedAt(LocalDateTime.now());
        departure = tourDepartureRepository.save(departure);

        var created = bookingService.create(
                new CreateBookingRequest(departure.getDepartureId(), 1, "note"), principal);

        Payment payment = new Payment();
        payment.setBooking(bookingRepository.findById(created.id()).orElseThrow());
        payment.setPaymentAmount(new BigDecimal("500.00"));
        payment.setPaymentMethod("CARD");
        payment.setPaymentStatus("SUCCESS");
        payment.setPaymentPaidAt(LocalDateTime.now());
        payment.setPaymentCreatedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        booking = bookingRepository.findById(created.id()).orElseThrow();
        booking.setBookingStatus("PAID");
        booking = bookingRepository.save(booking);
    }

    @Test
    void pendingRatingHiddenUntilApproved() {
        var created = ratingService.create(tour.getTourId(),
                new TourRatingRequest(booking.getBookingId(), 5, "Great"), principal);

        assertEquals("PENDING", created.status());
        assertEquals(0, ratingService.list(tour.getTourId(), PageRequest.of(0, 20)).ratingCount());

        ratingService.moderate(created.id(), "APPROVED");

        var publicPage = ratingService.list(tour.getTourId(), PageRequest.of(0, 20));
        assertEquals(1, publicPage.ratingCount());
        assertEquals(new BigDecimal("5.00"), publicPage.averageRating());
    }
}
