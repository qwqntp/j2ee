package com.j2ee.tour;

import com.j2ee.tour.dto.request.BookingUpdateRequest;
import com.j2ee.tour.dto.request.CreateBookingRequest;
import com.j2ee.tour.dto.request.CreateRefundRequest;
import com.j2ee.tour.dto.request.PaymentCallbackRequest;
import com.j2ee.tour.model.Account;
import com.j2ee.tour.model.AccountRole;
import com.j2ee.tour.model.Accommodation;
import com.j2ee.tour.model.Tour;
import com.j2ee.tour.model.TourDeparture;
import com.j2ee.tour.model.TourGuide;
import com.j2ee.tour.repository.AccountRepository;
import com.j2ee.tour.repository.AccountRoleRepository;
import com.j2ee.tour.repository.AccommodationRepository;
import com.j2ee.tour.repository.TourDepartureRepository;
import com.j2ee.tour.repository.TourGuideRepository;
import com.j2ee.tour.repository.TourRepository;
import com.j2ee.tour.service.BookingService;
import com.j2ee.tour.service.PaymentService;
import com.j2ee.tour.service.RefundService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class BookingFlowIntegrationTests {

    @Autowired private BookingService bookingService;
    @Autowired private PaymentService paymentService;
    @Autowired private RefundService refundService;

    @Autowired private AccountRoleRepository accountRoleRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AccommodationRepository accommodationRepository;
    @Autowired private TourGuideRepository tourGuideRepository;
    @Autowired private TourRepository tourRepository;
    @Autowired private TourDepartureRepository tourDepartureRepository;

    private TourDeparture departure;

    @BeforeEach
    void setUp() {
        AccountRole role = new AccountRole();
        role.setRoleName("USER");
        role = accountRoleRepository.save(role);

        Account account = new Account();
        account.setUsername("tester");
        account.setPassword("secret");
        account.setAccountEmail("tester@example.com");
        account.setEmailVerified(1);
        account.setStatus(1);
        account.setAccountCreatedAt(LocalDateTime.now());
        account.setAccountUpdatedAt(LocalDateTime.now());
        account.setRole(role);
        accountRepository.save(account);

        Accommodation accommodation = new Accommodation();
        accommodation.setAccommodationName("Hotel");
        accommodation.setLocation("HCM");
        accommodation = accommodationRepository.save(accommodation);

        TourGuide guide = new TourGuide();
        guide.setTourGuideName("Guide");
        guide.setTourGuideExperienceYears(1);
        guide = tourGuideRepository.save(guide);

        Tour tour = new Tour();
        tour.setTourName("Tour");
        tour.setTourStatus("OPEN");
        tour.setAccommodation(accommodation);
        tour.setTourGuide(guide);
        tour = tourRepository.save(tour);

        departure = new TourDeparture();
        departure.setTour(tour);
        departure.setDepartureStartDate(LocalDateTime.now().plusDays(1));
        departure.setDepartureEndDate(LocalDateTime.now().plusDays(3));
        departure.setDepartureCapacity(2);
        departure.setDepartureBookedSlots(0);
        departure.setDeparturePrice(new BigDecimal("500.00"));
        departure.setDepartureStatus("OPEN");
        departure.setDepartureCreatedAt(LocalDateTime.now());
        departure = tourDepartureRepository.save(departure);
    }

    @Test
    void bookingReservesSeatsAndMarksDepartureFull() {
        bookingService.create(new CreateBookingRequest(departure.getDepartureId(), 2, "note"));

        TourDeparture updated = tourDepartureRepository.findById(departure.getDepartureId()).orElseThrow();
        assertEquals(2, updated.getDepartureBookedSlots());
        assertEquals("FULL", updated.getDepartureStatus());
    }

    @Test
    void bookingFailsWhenNotEnoughSeats() {
        assertThrows(IllegalStateException.class,
                () -> bookingService.create(new CreateBookingRequest(departure.getDepartureId(), 3, "note")));
    }

    @Test
    void cancellationReleasesSeats() {
        var booking = bookingService.create(new CreateBookingRequest(departure.getDepartureId(), 2, "note"));
        bookingService.update(booking.id(), new BookingUpdateRequest(null, null, null, "CANCELLED"));

        TourDeparture updated = tourDepartureRepository.findById(departure.getDepartureId()).orElseThrow();
        assertEquals(0, updated.getDepartureBookedSlots());
        assertEquals("OPEN", updated.getDepartureStatus());
    }

    @Test
    void paymentCompletesBookingWhenSumReachesTotal() {
        var booking = bookingService.create(new CreateBookingRequest(departure.getDepartureId(), 2, "note"));

        paymentService.handleCallback(booking.id(),
                new PaymentCallbackRequest("txn-1", new BigDecimal("1000.00"), "CARD", true));

        var updated = bookingService.get(booking.id());
        assertEquals("PAID", updated.status());
    }

    @Test
    void refundRejectsAmountExceedingPayment() {
        var booking = bookingService.create(new CreateBookingRequest(departure.getDepartureId(), 2, "note"));
        var payment = paymentService.handleCallback(booking.id(),
                new PaymentCallbackRequest("txn-2", new BigDecimal("1000.00"), "CARD", true));

        refundService.request(payment.id(), new CreateRefundRequest(new BigDecimal("600.00"), "r1"));

        assertThrows(IllegalStateException.class,
                () -> refundService.request(payment.id(), new CreateRefundRequest(new BigDecimal("500.00"), "r2")));
    }

    @Test
    void bookingDeleteRejectedWhenPaymentsExist() {
        var booking = bookingService.create(new CreateBookingRequest(departure.getDepartureId(), 1, "note"));
        paymentService.handleCallback(booking.id(),
                new PaymentCallbackRequest("txn-3", new BigDecimal("500.00"), "CASH", true));

        assertThrows(IllegalStateException.class, () -> bookingService.delete(booking.id()));
    }
}
