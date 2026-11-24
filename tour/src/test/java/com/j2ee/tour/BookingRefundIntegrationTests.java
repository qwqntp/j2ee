package com.j2ee.tour;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.dto.request.CreateBookingRequest;
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
import com.j2ee.tour.repository.RefundRepository;
import com.j2ee.tour.repository.TourDepartureRepository;
import com.j2ee.tour.repository.TourGuideRepository;
import com.j2ee.tour.repository.TourRepository;
import com.j2ee.tour.security.AuthPrincipal;
import com.j2ee.tour.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BookingRefundIntegrationTests {

    private static final int GUEST_COUNT = 2;
    private static final BigDecimal TOTAL = new BigDecimal("1000.00");

    @Autowired private BookingService bookingService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private RefundRepository refundRepository;
    @Autowired private TourDepartureRepository tourDepartureRepository;

    @Autowired private AccountRoleRepository accountRoleRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AccommodationRepository accommodationRepository;
    @Autowired private TourGuideRepository tourGuideRepository;
    @Autowired private TourRepository tourRepository;

    private Booking booking;
    private AuthPrincipal principal;
    private Long departureId;

    @BeforeEach
    void setUp() {
        AccountRole role = new AccountRole();
        role.setRoleName("USER");
        role = accountRoleRepository.save(role);

        Account account = new Account();
        account.setUsername("refund-tester");
        account.setPassword("secret");
        account.setAccountEmail("refund-tester@example.com");
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

        Tour tour = new Tour();
        tour.setTourName("Tour");
        tour.setTourStatus("OPEN");
        tour.setAccommodation(accommodation);
        tour.setTourGuide(guide);
        tour = tourRepository.save(tour);

        TourDeparture departure = new TourDeparture();
        departure.setTour(tour);
        departure.setDepartureStartDate(LocalDateTime.now().plusDays(1));
        departure.setDepartureEndDate(LocalDateTime.now().plusDays(3));
        departure.setDepartureCapacity(4);
        departure.setDepartureBookedSlots(0);
        departure.setDeparturePrice(new BigDecimal("500.00"));
        departure.setDepartureStatus("OPEN");
        departure.setDepartureCreatedAt(LocalDateTime.now());
        departure = tourDepartureRepository.save(departure);
        departureId = departure.getDepartureId();

        var created = bookingService.create(
                new CreateBookingRequest(departureId, GUEST_COUNT, "note"), principal);

        Payment payment = new Payment();
        payment.setBooking(bookingRepository.findById(created.id()).orElseThrow());
        payment.setPaymentAmount(TOTAL);
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
    void refundRequestReservesFullAmountOnce() {
        var result = bookingService.requestRefund(booking.getBookingId(), principal);

        assertEquals("CANCELLED", result.status());
        var refunds = refundsForThisBooking();
        assertEquals(1, refunds.size());
        assertEquals("REQUESTED", refunds.get(0).getRefundStatus());
        assertEquals(0, TOTAL.compareTo(refunds.get(0).getRefundAmount()));
        assertEquals(0,
                tourDepartureRepository.findById(departureId).orElseThrow().getDepartureBookedSlots());
        assertThrows(ApiException.class,
                () -> bookingService.requestRefund(booking.getBookingId(), principal));
    }

    @Test
    void refundRequestRejectsPendingVnpayAttempt() {
        Payment pending = new Payment();
        pending.setBooking(booking);
        pending.setPaymentAmount(TOTAL);
        pending.setPaymentMethod("VNPAY");
        pending.setPaymentProviderTxnId("VNP" + "A".repeat(32));
        pending.setPaymentStatus("PENDING");
        pending.setPaymentCreatedAt(LocalDateTime.now());
        paymentRepository.save(pending);

        assertThrows(ApiException.class,
                () -> bookingService.requestRefund(booking.getBookingId(), principal));
        assertTrue(refundsForThisBooking().isEmpty());
    }

    @Test
    void refundRequestRejectsBookingWithoutSuccessfulPayment() {
        booking.setBookingStatus("CONFIRMED");
        bookingRepository.save(booking);
        paymentRepository.deleteAll(paymentRepository.findByBookingBookingId(booking.getBookingId()));

        assertThrows(ApiException.class,
                () -> bookingService.requestRefund(booking.getBookingId(), principal));
        assertTrue(refundsForThisBooking().isEmpty());
    }

    @Test
    void refundRequestRejectsCompletedBooking() {
        booking.setBookingStatus("COMPLETED");
        bookingRepository.save(booking);

        assertThrows(ApiException.class,
                () -> bookingService.requestRefund(booking.getBookingId(), principal));
        assertTrue(refundsForThisBooking().isEmpty());
    }

    private java.util.List<com.j2ee.tour.model.Refund> refundsForThisBooking() {
        return refundRepository.findAll().stream()
                .filter(refund -> refund.getBooking() != null
                        && booking.getBookingId().equals(refund.getBooking().getBookingId()))
                .toList();
    }
}
