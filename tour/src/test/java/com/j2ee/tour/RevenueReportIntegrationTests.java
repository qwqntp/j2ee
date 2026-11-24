package com.j2ee.tour;

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
import com.j2ee.tour.service.AdminStatsService;
import com.j2ee.tour.service.RevenueReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RevenueReportIntegrationTests {

    @Autowired private RevenueReportService revenueReportService;
    @Autowired private AdminStatsService adminStatsService;
    @Autowired private PaymentRepository paymentRepository;

    @Autowired private AccountRoleRepository accountRoleRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AccommodationRepository accommodationRepository;
    @Autowired private TourGuideRepository tourGuideRepository;
    @Autowired private TourRepository tourRepository;
    @Autowired private TourDepartureRepository tourDepartureRepository;
    @Autowired private BookingRepository bookingRepository;

    // Far-future window keeps exact assertions stable against shared seeded data.
    private static final LocalDate FROM = LocalDate.of(2099, 1, 1);
    private static final LocalDate TO = LocalDate.of(2099, 12, 31);

    @Test
    void revenueCsvRespectsDateWindow() {
        Booking booking = newBooking();
        addPayment(booking, "50.00", LocalDateTime.of(2099, 6, 15, 10, 0));
        addPayment(booking, "99.00", LocalDateTime.of(2098, 10, 2, 10, 0));

        String csv = revenueReportService.csv(FROM, TO);
        assertTrue(csv.contains("2099-06-15,50.00"));
        assertFalse(csv.contains("2098-10-02"));
        var stats = adminStatsService.stats(FROM, TO);
        assertEquals(0, new BigDecimal("50.00").compareTo(stats.successfulRevenue()));
    }

    @Test
    void reversedWindowIsRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(com.j2ee.tour.common.ApiException.class,
                () -> revenueReportService.csv(TO, FROM));
    }

    private Booking newBooking() {
        AccountRole role = new AccountRole();
        role.setRoleName("USER");
        role = accountRoleRepository.save(role);

        Account account = new Account();
        account.setUsername("revenue-" + UUID.randomUUID().toString().substring(0, 8));
        account.setPassword("secret");
        account.setAccountEmail("revenue@example.com");
        account.setEmailVerified(1);
        account.setStatus(1);
        account.setAccountCreatedAt(LocalDateTime.now());
        account.setAccountUpdatedAt(LocalDateTime.now());
        account.setRole(role);
        account = accountRepository.save(account);

        Accommodation accommodation = new Accommodation();
        accommodation.setAccommodationName("Hotel");
        accommodation.setLocation("HCM");
        accommodation = accommodationRepository.save(accommodation);

        TourGuide guide = new TourGuide();
        guide.setTourGuideName("Guide");
        guide.setTourGuideExperienceYears(1);
        guide = tourGuideRepository.save(guide);

        Tour tour = new Tour();
        tour.setTourName("Revenue tour");
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
        departure.setDeparturePrice(new BigDecimal("100.00"));
        departure.setDepartureStatus("OPEN");
        departure.setDepartureCreatedAt(LocalDateTime.now());
        departure = tourDepartureRepository.save(departure);

        Booking booking = new Booking();
        booking.setBookingCode("REV-" + UUID.randomUUID().toString().substring(0, 12));
        booking.setBookingCreatedAt(LocalDateTime.now());
        booking.setBookingGuestCount(1);
        booking.setBookingUnitPrice(new BigDecimal("100.00"));
        booking.setBookingSubtotal(new BigDecimal("100.00"));
        booking.setBookingDiscountAmount(BigDecimal.ZERO);
        booking.setBookingTotalAmount(new BigDecimal("100.00"));
        booking.setBookingStatus("PAID");
        booking.setAccount(account);
        booking.setDeparture(departure);
        return bookingRepository.save(booking);
    }

    private void addPayment(Booking booking, String amount, LocalDateTime paidAt) {
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setPaymentAmount(new BigDecimal(amount));
        payment.setPaymentMethod("CASH");
        payment.setPaymentStatus("SUCCESS");
        payment.setPaymentPaidAt(paidAt);
        payment.setPaymentCreatedAt(paidAt);
        paymentRepository.save(payment);
    }
}
