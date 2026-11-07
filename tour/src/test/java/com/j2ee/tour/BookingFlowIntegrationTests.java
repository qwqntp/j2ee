package com.j2ee.tour;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.dto.request.BookingTravelerRequest;
import com.j2ee.tour.dto.request.BookingUpdateRequest;
import com.j2ee.tour.dto.request.CreateBookingRequest;
import com.j2ee.tour.dto.request.CreateRefundRequest;
import com.j2ee.tour.dto.request.PaymentCallbackRequest;
import com.j2ee.tour.dto.request.TourRatingRequest;
import com.j2ee.tour.model.Account;
import com.j2ee.tour.model.AccountRole;
import com.j2ee.tour.model.Accommodation;
import com.j2ee.tour.model.Booking;
import com.j2ee.tour.model.Promotion;
import com.j2ee.tour.model.Tour;
import com.j2ee.tour.model.TourDeparture;
import com.j2ee.tour.model.TourGuide;
import com.j2ee.tour.security.AuthPrincipal;
import com.j2ee.tour.repository.AccountRepository;
import com.j2ee.tour.repository.AccountRoleRepository;
import com.j2ee.tour.repository.AccommodationRepository;
import com.j2ee.tour.repository.BookingRepository;
import com.j2ee.tour.repository.InvoiceRepository;
import com.j2ee.tour.repository.PromotionRepository;
import com.j2ee.tour.repository.TourDepartureRepository;
import com.j2ee.tour.repository.TourGuideRepository;
import com.j2ee.tour.repository.TourRepository;
import com.j2ee.tour.service.BookingService;
import com.j2ee.tour.service.InvoiceService;
import com.j2ee.tour.service.PaymentService;
import com.j2ee.tour.service.PaymentSignatureService;
import com.j2ee.tour.service.RefundService;
import com.j2ee.tour.service.TourRatingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BookingFlowIntegrationTests {

    @Autowired private BookingService bookingService;
    @Autowired private PaymentService paymentService;
    @Autowired private RefundService refundService;
    @Autowired private PaymentSignatureService paymentSignatureService;
    @Autowired private TourRatingService tourRatingService;
    @Autowired private InvoiceService invoiceService;

    @Autowired private AccountRoleRepository accountRoleRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AccommodationRepository accommodationRepository;
    @Autowired private TourGuideRepository tourGuideRepository;
    @Autowired private TourRepository tourRepository;
    @Autowired private TourDepartureRepository tourDepartureRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private InvoiceRepository invoiceRepository;

    private TourDeparture departure;
    private Account account;
    private AuthPrincipal principal;

    @BeforeEach
    void setUp() {
        AccountRole role = new AccountRole();
        role.setRoleName("USER");
        role = accountRoleRepository.save(role);

        account = new Account();
        account.setUsername("tester");
        account.setPassword("secret");
        account.setAccountEmail("tester@example.com");
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

    private AuthPrincipal adminPrincipal() {
        return new AuthPrincipal(account.getAccountId() + 1, "admin", "ROLE_ADMIN");
    }

    private PaymentCallbackRequest callback(Long bookingId, String txnId, String amount,
                                            String method, boolean successful) {
        BigDecimal value = new BigDecimal(amount);
        return new PaymentCallbackRequest(txnId, value, method, successful,
                paymentSignatureService.sign(bookingId, txnId, method, value, successful));
    }

    private Promotion makePromotion(String code) {
        Promotion promotion = new Promotion();
        promotion.setPromotionName("Promo " + code);
        promotion.setPromotionCode(code);
        promotion.setPromotionActive(true);
        promotion.setDiscountPercentage(new BigDecimal("10"));
        promotion.setStartDate(LocalDate.now().minusDays(1));
        promotion.setEndDate(LocalDate.now().plusDays(10));
        promotion.setPromotionUsageLimit(5);
        promotion.setPromotionUsedCount(0);
        promotion.setPromotionMinOrderAmount(new BigDecimal("500.00"));
        return promotionRepository.save(promotion);
    }

    private Account createAccount(String username) {
        Account other = new Account();
        other.setUsername(username);
        other.setPassword("secret");
        other.setAccountEmail(username + "@example.com");
        other.setEmailVerified(1);
        other.setStatus(1);
        other.setAccountCreatedAt(LocalDateTime.now());
        other.setAccountUpdatedAt(LocalDateTime.now());
        other.setRole(account.getRole());
        return accountRepository.save(other);
    }

    private TourDeparture createDeparture(int capacity) {
        TourDeparture other = new TourDeparture();
        other.setTour(departure.getTour());
        other.setDepartureStartDate(LocalDateTime.now().plusDays(5));
        other.setDepartureEndDate(LocalDateTime.now().plusDays(7));
        other.setDepartureCapacity(capacity);
        other.setDepartureBookedSlots(0);
        other.setDeparturePrice(new BigDecimal("500.00"));
        other.setDepartureStatus("OPEN");
        other.setDepartureCreatedAt(LocalDateTime.now());
        return tourDepartureRepository.save(other);
    }

    @Test
    void bookingReservesSeatsAndMarksDepartureFull() {
        bookingService.create(new CreateBookingRequest(departure.getDepartureId(), 2, "note"), principal);

        TourDeparture updated = tourDepartureRepository.findById(departure.getDepartureId()).orElseThrow();
        assertEquals(2, updated.getDepartureBookedSlots());
        assertEquals("FULL", updated.getDepartureStatus());
    }

    @Test
    void bookingFailsWhenNotEnoughSeats() {
        assertThrows(IllegalStateException.class,
                () -> bookingService.create(new CreateBookingRequest(departure.getDepartureId(), 3, "note"), principal));
    }

    @Test
    void bookingRejectsDepartureThatAlreadyStarted() {
        departure.setDepartureStartDate(LocalDateTime.now().minusDays(2));
        departure.setDepartureEndDate(LocalDateTime.now().minusDays(1));
        tourDepartureRepository.save(departure);

        assertThrows(IllegalStateException.class,
                () -> bookingService.create(
                        new CreateBookingRequest(departure.getDepartureId(), 1, "note"), principal));
    }

    @Test
    void normalUserCannotChangeOwnBookingStatusOrOwner() {
        var booking = bookingService.create(
                new CreateBookingRequest(departure.getDepartureId(), 1, "note"), principal);

        assertThrows(ApiException.class, () -> bookingService.update(
                booking.id(), new BookingUpdateRequest(null, null, null, "CANCELLED"), principal));
        assertThrows(ApiException.class, () -> bookingService.update(
                booking.id(), new BookingUpdateRequest(account.getAccountId() + 1, null, null, null),
                principal));
    }

    @Test
    void cancellationReleasesSeats() {
        var booking = bookingService.create(new CreateBookingRequest(departure.getDepartureId(), 2, "note"), principal);
        bookingService.update(booking.id(), new BookingUpdateRequest(null, null, null, "CANCELLED"), adminPrincipal());

        TourDeparture updated = tourDepartureRepository.findById(departure.getDepartureId()).orElseThrow();
        assertEquals(0, updated.getDepartureBookedSlots());
        assertEquals("OPEN", updated.getDepartureStatus());
    }

    @Test
    void paymentCompletesBookingWhenSumReachesTotal() {
        var booking = bookingService.create(new CreateBookingRequest(departure.getDepartureId(), 2, "note"), principal);

        paymentService.handleCallback(booking.id(),
                callback(booking.id(), "txn-1", "1000.00", "CARD", true), principal);

        var updated = bookingService.get(booking.id(), principal);
        assertEquals("PAID", updated.status());
        assertTrue(invoiceRepository.findByBookingBookingId(booking.id()).isPresent());
    }

    @Test
    void paidBookingInvoiceIsVisibleOnlyToOwner() {
        var booking = bookingService.create(
                new CreateBookingRequest(departure.getDepartureId(), 1, "note"), principal);
        paymentService.handleCallback(booking.id(),
                callback(booking.id(), "txn-invoice-1", "500.00", "CARD", true), principal);

        var mine = invoiceService.listMine(account.getAccountId(), PageRequest.of(0, 10));

        assertEquals(1, mine.totalElements());
        assertEquals(booking.id(), mine.content().get(0).bookingId());
        assertEquals("PAID", mine.content().get(0).status());

        Account other = createAccount("other-invoice");
        var otherInvoices = invoiceService.listMine(other.getAccountId(), PageRequest.of(0, 10));
        assertEquals(0, otherInvoices.totalElements());
    }

    @Test
    void ownerCanRatePaidBookingAndAverageIsPublic() {
        var booking = bookingService.create(
                new CreateBookingRequest(departure.getDepartureId(), 1, "note"), principal);
        paymentService.handleCallback(booking.id(),
                callback(booking.id(), "txn-rating-1", "500.00", "CARD", true), principal);

        var rating = tourRatingService.create(departure.getTour().getTourId(),
                new TourRatingRequest(booking.id(), 5, "Great"),
                principal);

        assertEquals(5, rating.rating());
        var page = tourRatingService.list(departure.getTour().getTourId(),
                PageRequest.of(0, 10));
        assertEquals(new BigDecimal("5.00"), page.averageRating());
        assertEquals(1, page.ratingCount());
    }

    @Test
    void ratingAverageIncludesAllRatingsForTour() {
        TourDeparture wide = createDeparture(10);
        var first = bookingService.create(
                new CreateBookingRequest(wide.getDepartureId(), 1, "first"), principal);
        var second = bookingService.create(
                new CreateBookingRequest(wide.getDepartureId(), 1, "second"), principal);
        paymentService.handleCallback(first.id(),
                callback(first.id(), "txn-rating-2", "500.00", "CARD", true), principal);
        paymentService.handleCallback(second.id(),
                callback(second.id(), "txn-rating-3", "500.00", "CARD", true), principal);

        tourRatingService.create(departure.getTour().getTourId(),
                new TourRatingRequest(first.id(), 5, null), principal);
        tourRatingService.create(departure.getTour().getTourId(),
                new TourRatingRequest(second.id(), 3, null), principal);

        var page = tourRatingService.list(departure.getTour().getTourId(),
                PageRequest.of(0, 10));
        assertEquals(new BigDecimal("4.00"), page.averageRating());
        assertEquals(2, page.ratingCount());
    }

    @Test
    void unpaidBookingCannotBeRated() {
        var booking = bookingService.create(
                new CreateBookingRequest(departure.getDepartureId(), 1, "note"), principal);

        ApiException exception = assertThrows(ApiException.class, () ->
                tourRatingService.create(departure.getTour().getTourId(),
                        new TourRatingRequest(booking.id(), 5, null), principal));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void bookingCanOnlyBeRatedOnce() {
        var booking = bookingService.create(
                new CreateBookingRequest(departure.getDepartureId(), 1, "note"), principal);
        paymentService.handleCallback(booking.id(),
                callback(booking.id(), "txn-rating-4", "500.00", "CARD", true), principal);
        tourRatingService.create(departure.getTour().getTourId(),
                new TourRatingRequest(booking.id(), 5, null), principal);

        ApiException exception = assertThrows(ApiException.class, () ->
                tourRatingService.create(departure.getTour().getTourId(),
                        new TourRatingRequest(booking.id(), 4, null), principal));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
    }

    @Test
    void otherUserCannotRateBooking() {
        var booking = bookingService.create(
                new CreateBookingRequest(departure.getDepartureId(), 1, "note"), principal);
        paymentService.handleCallback(booking.id(),
                callback(booking.id(), "txn-rating-5", "500.00", "CARD", true), principal);
        Account other = createAccount("other-rating");
        AuthPrincipal otherPrincipal = new AuthPrincipal(
                other.getAccountId(), other.getUsername(), "ROLE_USER");

        ApiException exception = assertThrows(ApiException.class, () ->
                tourRatingService.create(departure.getTour().getTourId(),
                        new TourRatingRequest(booking.id(), 5, null), otherPrincipal));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    void refundRejectsAmountExceedingPayment() {
        var booking = bookingService.create(new CreateBookingRequest(departure.getDepartureId(), 2, "note"), principal);
        var payment = paymentService.handleCallback(booking.id(),
                callback(booking.id(), "txn-2", "1000.00", "CARD", true), principal);

        refundService.request(payment.id(), new CreateRefundRequest(new BigDecimal("600.00"), "r1"));

        assertThrows(IllegalStateException.class,
                () -> refundService.request(payment.id(), new CreateRefundRequest(new BigDecimal("500.00"), "r2")));
    }

    @Test
    void bookingDeleteRejectedWhenPaymentsExist() {
        var booking = bookingService.create(new CreateBookingRequest(departure.getDepartureId(), 1, "note"), principal);
        paymentService.handleCallback(booking.id(),
                callback(booking.id(), "txn-3", "500.00", "CASH", true), principal);

        assertThrows(IllegalStateException.class, () -> bookingService.delete(booking.id(), principal));
    }

    @Test
    void bookingIsLinkedToSignedInAccount() {
        var booking = bookingService.create(
                new CreateBookingRequest(departure.getDepartureId(), 1, "note"), principal);

        assertEquals(account.getAccountId(), booking.accountId());
    }

    @Test
    void otherUserCannotReadOrDeleteBooking() {
        var booking = bookingService.create(
                new CreateBookingRequest(departure.getDepartureId(), 1, "note"), principal);
        AuthPrincipal other = new AuthPrincipal(account.getAccountId() + 1, "other", "ROLE_USER");

        assertThrows(ApiException.class, () -> bookingService.get(booking.id(), other));
        assertThrows(ApiException.class, () -> bookingService.delete(booking.id(), other));
        assertThrows(ApiException.class, () -> bookingService.update(
                booking.id(), new BookingUpdateRequest(null, 3, null, null), other));
    }

    @Test
    void adminCanReadAnyBooking() {
        var booking = bookingService.create(
                new CreateBookingRequest(departure.getDepartureId(), 1, "note"), principal);
        AuthPrincipal admin = new AuthPrincipal(account.getAccountId() + 1, "admin", "ROLE_ADMIN");

        assertEquals(booking.id(), bookingService.get(booking.id(), admin).id());
    }

    @Test
    void updatingGuestCountRecalculatesSeatsAndTotal() {
        var booking = bookingService.create(
                new CreateBookingRequest(departure.getDepartureId(), 1, "note"), principal);

        var updated = bookingService.update(booking.id(),
                new BookingUpdateRequest(null, 2, null, null), principal);

        assertEquals(2, updated.guestCount());
        assertEquals(new BigDecimal("1000.00"), updated.totalAmount());
        TourDeparture stored = tourDepartureRepository.findById(departure.getDepartureId()).orElseThrow();
        assertEquals(2, stored.getDepartureBookedSlots());
    }

    @Test
    void reducingGuestCountRecomputesPromotionDiscount() {
        makePromotion("REDUCE10");
        var booking = bookingService.create(new CreateBookingRequest(
                        departure.getDepartureId(), 2, "note", null, "REDUCE10"),
                principal);

        var updated = bookingService.update(booking.id(),
                new BookingUpdateRequest(null, 1, null, null), principal);

        assertEquals(1, updated.guestCount());
        assertEquals(new BigDecimal("500.00"), updated.subtotal());
        assertEquals(new BigDecimal("50.00"), updated.discountAmount());
        assertEquals(new BigDecimal("450.00"), updated.totalAmount());
        TourDeparture stored = tourDepartureRepository.findById(departure.getDepartureId()).orElseThrow();
        assertEquals(1, stored.getDepartureBookedSlots());
    }

    @Test
    void reducingGuestCountBelowPromotionMinimumReturnsBadRequest() {
        Promotion promotion = makePromotion("MIN600");
        promotion.setPromotionMinOrderAmount(new BigDecimal("600.00"));
        promotionRepository.save(promotion);
        var booking = bookingService.create(new CreateBookingRequest(
                        departure.getDepartureId(), 2, "note", null, "MIN600"),
                principal);

        ApiException exception = assertThrows(ApiException.class, () ->
                bookingService.update(booking.id(),
                        new BookingUpdateRequest(null, 1, null, null), principal));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("Order does not meet promotion minimum amount", exception.getMessage());
        var unchanged = bookingService.get(booking.id(), principal);
        assertEquals(2, unchanged.guestCount());
        assertEquals(new BigDecimal("100.00"), unchanged.discountAmount());
        TourDeparture stored = tourDepartureRepository.findById(departure.getDepartureId()).orElseThrow();
        assertEquals(2, stored.getDepartureBookedSlots());
    }

    @Test
    void createWithTravelersAndPromotionPersistsEverything() {
        makePromotion("SAVE10");
        var booking = bookingService.create(new CreateBookingRequest(
                        departure.getDepartureId(), 2, "note",
                        List.of(
                                new BookingTravelerRequest("Alice", "0901234567",
                                        "alice@example.com", LocalDate.of(1990, 1, 1), "FEMALE", "ID1"),
                                new BookingTravelerRequest("Bob", "0907654321",
                                        "bob@example.com", null, "MALE", "ID2")),
                        "SAVE10"),
                principal);

        assertEquals(2, booking.travelers().size());
        assertEquals("Alice", booking.travelers().get(0).name());
        assertEquals("0901234567", booking.travelers().get(0).phone());
        assertEquals(new BigDecimal("100.00"), booking.discountAmount());
        assertEquals(new BigDecimal("900.00"), booking.totalAmount());
        assertEquals("SAVE10", booking.promotion().code());

        var detail = bookingService.get(booking.id(), principal);
        assertEquals(2, detail.travelers().size());
        assertEquals("bob@example.com", detail.travelers().get(1).email());
        assertEquals("SAVE10", detail.promotion().code());
        assertEquals(1, promotionRepository.findByPromotionCodeIgnoreCase("SAVE10")
                .orElseThrow().getPromotionUsedCount());
    }

    @Test
    void cancelReleasesSeatsAndPromotionUsage() {
        Promotion promotion = makePromotion("CANCEL10");
        var booking = bookingService.create(new CreateBookingRequest(
                        departure.getDepartureId(), 1, "note",
                        List.of(new BookingTravelerRequest("Alice", null, null, null, null, null)),
                        "CANCEL10"),
                principal);

        var cancelled = bookingService.cancel(booking.id(), principal);

        assertEquals("CANCELLED", cancelled.status());
        TourDeparture stored = tourDepartureRepository.findById(departure.getDepartureId()).orElseThrow();
        assertEquals(0, stored.getDepartureBookedSlots());
        assertEquals(0, promotionRepository.findById(promotion.getPromotionId())
                .orElseThrow().getPromotionUsedCount());
    }

    @Test
    void otherUserCannotCancelBooking() {
        var booking = bookingService.create(
                new CreateBookingRequest(departure.getDepartureId(), 1, "note"), principal);
        Account other = createAccount("other-cancel");
        AuthPrincipal otherPrincipal = new AuthPrincipal(
                other.getAccountId(), other.getUsername(), "ROLE_USER");

        ApiException exception = assertThrows(ApiException.class, () ->
                bookingService.cancel(booking.id(), otherPrincipal));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("PENDING", bookingService.get(booking.id(), principal).status());
        assertEquals(1, tourDepartureRepository.findById(departure.getDepartureId())
                .orElseThrow().getDepartureBookedSlots());
    }

    @Test
    void bookingListIsOwnerScopedAndNewestFirst() {
        TourDeparture wide = createDeparture(10);
        var first = bookingService.create(
                new CreateBookingRequest(wide.getDepartureId(), 1, "first"), principal);
        Booking firstEntity = bookingRepository.findById(first.id()).orElseThrow();
        firstEntity.setBookingCreatedAt(LocalDateTime.now().minusHours(1));
        bookingRepository.save(firstEntity);
        var second = bookingService.create(
                new CreateBookingRequest(wide.getDepartureId(), 1, "second"), principal);

        Account other = createAccount("other-list");
        AuthPrincipal otherPrincipal = new AuthPrincipal(
                other.getAccountId(), other.getUsername(), "ROLE_USER");
        var otherBooking = bookingService.create(
                new CreateBookingRequest(wide.getDepartureId(), 1, "other"), otherPrincipal);

        var page = bookingService.list(PageRequest.of(0, 10), principal);

        assertEquals(List.of(second.id(), first.id()),
                page.content().stream().map(item -> item.id()).toList());
        assertTrue(page.content().stream().noneMatch(item -> item.id().equals(otherBooking.id())));
    }
}
