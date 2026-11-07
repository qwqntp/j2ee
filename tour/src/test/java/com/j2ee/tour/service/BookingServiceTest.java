package com.j2ee.tour.service;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.dto.request.BookingTravelerRequest;
import com.j2ee.tour.dto.request.BookingUpdateRequest;
import com.j2ee.tour.dto.request.CreateBookingRequest;
import com.j2ee.tour.model.Account;
import com.j2ee.tour.model.Booking;
import com.j2ee.tour.model.Promotion;
import com.j2ee.tour.model.TourDeparture;
import com.j2ee.tour.model.Traveler;
import com.j2ee.tour.repository.AccountRepository;
import com.j2ee.tour.repository.BookingRepository;
import com.j2ee.tour.repository.InvoiceRepository;
import com.j2ee.tour.repository.PaymentRepository;
import com.j2ee.tour.repository.PromotionRepository;
import com.j2ee.tour.repository.TourDepartureRepository;
import com.j2ee.tour.repository.TravelerRepository;
import com.j2ee.tour.security.AuthPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingServiceTest {

    private BookingRepository bookingRepository;
    private TourDepartureRepository departureRepository;
    private AccountRepository accountRepository;
    private PromotionRepository promotionRepository;
    private PaymentRepository paymentRepository;
    private InvoiceRepository invoiceRepository;
    private InvoiceService invoiceService;
    private TravelerRepository travelerRepository;
    private EmailService emailService;
    private BookingService bookingService;
    private Clock clock;

    private Account account;
    private Booking booking;
    private TourDeparture departure;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        departureRepository = mock(TourDepartureRepository.class);
        accountRepository = mock(AccountRepository.class);
        promotionRepository = mock(PromotionRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        invoiceRepository = mock(InvoiceRepository.class);
        invoiceService = mock(InvoiceService.class);
        travelerRepository = mock(TravelerRepository.class);
        emailService = mock(EmailService.class);
        clock = Clock.fixed(Instant.parse("2026-11-05T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        bookingService = new BookingService(bookingRepository, departureRepository, accountRepository,
                promotionRepository, paymentRepository, invoiceRepository, invoiceService,
                travelerRepository, emailService, clock);

        account = new Account();
        account.setAccountId(10L);
        account.setUsername("user");
        account.setAccountEmail("user@example.com");

        departure = new TourDeparture();
        departure.setDepartureId(20L);
        departure.setDepartureStatus("OPEN");
        departure.setDepartureStartDate(LocalDateTime.now().plusDays(2));
        departure.setDepartureEndDate(LocalDateTime.now().plusDays(4));
        departure.setDepartureCapacity(10);
        departure.setDepartureBookedSlots(0);
        departure.setDeparturePrice(new BigDecimal("500.00"));

        booking = new Booking();
        booking.setBookingId(30L);
        booking.setBookingCode("BK-20261105-0001");
        booking.setAccount(account);
        booking.setDeparture(departure);
        booking.setBookingGuestCount(2);
        booking.setBookingUnitPrice(new BigDecimal("500.00"));
        booking.setBookingSubtotal(new BigDecimal("1000.00"));
        booking.setBookingDiscountAmount(BigDecimal.ZERO);
        booking.setBookingTotalAmount(new BigDecimal("1000.00"));
        booking.setBookingStatus("PENDING");
        booking.setBookingCreatedAt(LocalDateTime.now());

        when(bookingRepository.findById(30L)).thenReturn(Optional.of(booking));
        when(bookingRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(departureRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(departure));
        when(accountRepository.findById(10L)).thenReturn(Optional.of(account));
        when(paymentRepository.sumSuccessfulPayments(30L)).thenReturn(BigDecimal.ZERO);
        when(travelerRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(promotionRepository.save(any(Promotion.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private AuthPrincipal principal(long id, String role) {
        return new AuthPrincipal(id, "user" + id, role);
    }

    private Promotion promotion(String code, String percentage, Integer usageLimit, int usedCount,
                                String minOrderAmount) {
        Promotion promotion = new Promotion();
        promotion.setPromotionId(40L);
        promotion.setPromotionName("Promo");
        promotion.setPromotionCode(code);
        promotion.setPromotionActive(true);
        promotion.setDiscountPercentage(new BigDecimal(percentage));
        promotion.setStartDate(LocalDate.of(2026, 11, 1));
        promotion.setEndDate(LocalDate.of(2026, 11, 30));
        promotion.setPromotionUsageLimit(usageLimit);
        promotion.setPromotionUsedCount(usedCount);
        promotion.setPromotionMinOrderAmount(minOrderAmount == null ? null : new BigDecimal(minOrderAmount));
        return promotion;
    }

    @Test
    void normalUserCannotChangeBookingStatus() {
        ApiException exception = assertThrows(ApiException.class, () ->
                bookingService.update(30L,
                        new BookingUpdateRequest(null, null, null, "CANCELLED"),
                        principal(10L, "ROLE_USER")));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("PENDING", booking.getBookingStatus());
    }

    @Test
    void normalUserCannotChangeBookingOwner() {
        ApiException exception = assertThrows(ApiException.class, () ->
                bookingService.update(30L,
                        new BookingUpdateRequest(99L, null, null, null),
                        principal(10L, "ROLE_USER")));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals(account.getAccountId(), booking.getAccount().getAccountId());
    }

    @Test
    void otherUserCannotUpdateBooking() {
        ApiException exception = assertThrows(ApiException.class, () ->
                bookingService.update(30L,
                        new BookingUpdateRequest(null, 3, null, null),
                        principal(11L, "ROLE_USER")));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals(2, booking.getBookingGuestCount());
    }

    @Test
    void createRejectsDepartureThatAlreadyStarted() {
        departure.setDepartureStartDate(LocalDateTime.now(clock).minusDays(1));
        departure.setDepartureEndDate(LocalDateTime.now(clock).plusDays(1));

        assertThrows(IllegalStateException.class, () ->
                bookingService.create(new CreateBookingRequest(20L, 1, "note"),
                        principal(10L, "ROLE_USER")));
    }

    @Test
    void adminCanStillChangeBookingStatus() {
        var response = bookingService.update(30L,
                new BookingUpdateRequest(null, null, null, "CANCELLED"),
                principal(99L, "ROLE_ADMIN"));

        assertEquals("CANCELLED", response.status());
    }

    @Test
    void adminSettingStatusPaidCreatesInvoice() {
        when(paymentRepository.sumSuccessfulPayments(30L))
                .thenReturn(new BigDecimal("1000.00"));

        var response = bookingService.update(30L,
                new BookingUpdateRequest(null, null, null, "PAID"),
                principal(99L, "ROLE_ADMIN"));

        assertEquals("PAID", response.status());
        verify(invoiceService).createForPaidBooking(booking);
    }

    @Test
    void cancellationSendsEmail() {
        booking.setBookingStatus("PENDING");

        bookingService.cancel(30L, principal(10L, "ROLE_USER"));

        verify(emailService).sendBookingCancellation("user@example.com", "BK-20261105-0001");
    }

    @Test
    void updateGuestCountRecalculatesSeatsAndTotal() {
        departure.setDepartureBookedSlots(2);

        var response = bookingService.update(30L,
                new BookingUpdateRequest(null, 3, null, null),
                principal(10L, "ROLE_USER"));

        assertEquals(3, departure.getDepartureBookedSlots());
        assertEquals(new BigDecimal("1500.00"), booking.getBookingSubtotal());
        assertEquals(new BigDecimal("1500.00"), booking.getBookingTotalAmount());
        assertEquals(3, response.guestCount());
        assertEquals(new BigDecimal("1500.00"), response.totalAmount());
    }

    @Test
    void reducingGuestCountRecomputesPromotionDiscount() {
        Promotion promotion = promotion("SUMMER", "10", 5, 1, "500.00");
        booking.setPromotion(promotion);
        booking.setBookingDiscountAmount(new BigDecimal("100.00"));
        booking.setBookingTotalAmount(new BigDecimal("900.00"));
        departure.setDepartureBookedSlots(2);

        var response = bookingService.update(30L,
                new BookingUpdateRequest(null, 1, null, null),
                principal(10L, "ROLE_USER"));

        assertEquals(1, departure.getDepartureBookedSlots());
        assertEquals(new BigDecimal("500.00"), response.subtotal());
        assertEquals(new BigDecimal("50.00"), response.discountAmount());
        assertEquals(new BigDecimal("450.00"), response.totalAmount());
        assertEquals(new BigDecimal("50.00"), booking.getBookingDiscountAmount());
        assertEquals(new BigDecimal("450.00"), booking.getBookingTotalAmount());
    }

    @Test
    void reducingGuestCountBelowPromotionMinimumReturnsBadRequest() {
        Promotion promotion = promotion("SUMMER", "10", 5, 1, "600.00");
        booking.setPromotion(promotion);
        booking.setBookingDiscountAmount(new BigDecimal("100.00"));
        booking.setBookingTotalAmount(new BigDecimal("900.00"));
        departure.setDepartureBookedSlots(2);

        ApiException exception = assertThrows(ApiException.class, () ->
                bookingService.update(30L,
                        new BookingUpdateRequest(null, 1, null, null),
                        principal(10L, "ROLE_USER")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("Order does not meet promotion minimum amount", exception.getMessage());
        assertEquals(2, departure.getDepartureBookedSlots());
        assertEquals(2, booking.getBookingGuestCount());
        assertEquals(new BigDecimal("1000.00"), booking.getBookingSubtotal());
        assertEquals(new BigDecimal("100.00"), booking.getBookingDiscountAmount());
    }

    @Test
    void createWithTravelersSavesThemLinkedToBooking() {
        var response = bookingService.create(new CreateBookingRequest(
                        20L, 2, "note",
                        List.of(
                                new BookingTravelerRequest("Alice", "0901234567",
                                        "alice@example.com", LocalDate.of(1990, 1, 1), "FEMALE", "ID1"),
                                new BookingTravelerRequest("Bob", null, null, null, null, null)),
                        null),
                principal(10L, "ROLE_USER"));

        assertEquals(2, response.travelers().size());
        assertEquals("Alice", response.travelers().get(0).name());
        assertEquals("alice@example.com", response.travelers().get(0).email());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Traveler>> captor = ArgumentCaptor.forClass(List.class);
        verify(travelerRepository).saveAll(captor.capture());
        ArgumentCaptor<Booking> bookingCaptor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(bookingCaptor.capture());
        assertSame(bookingCaptor.getValue(), captor.getValue().get(0).getBooking());
        assertEquals("0901234567", captor.getValue().get(0).getTravelerPhone());
    }

    @Test
    void createSendsConfirmationEmail() {
        var response = bookingService.create(
                new CreateBookingRequest(20L, 1, "note"),
                principal(10L, "ROLE_USER"));

        verify(emailService).sendBookingConfirmation(
                "user@example.com", response.bookingCode(), response.totalAmount());
    }

    @Test
    void createRejectsTravelerCountMismatch() {
        ApiException exception = assertThrows(ApiException.class, () ->
                bookingService.create(new CreateBookingRequest(
                                20L, 2, "note",
                                List.of(new BookingTravelerRequest("Alice", null, null, null, null, null)),
                                null),
                        principal(10L, "ROLE_USER")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("Traveler list size must match guestCount", exception.getMessage());
    }

    @Test
    void createAppliesPromotionAndIncrementsUsage() {
        Promotion promotion = promotion("SUMMER", "10", 5, 1, "500.00");
        when(promotionRepository.findByPromotionCodeIgnoreCaseForUpdate("SUMMER"))
                .thenReturn(Optional.of(promotion));

        var response = bookingService.create(
                new CreateBookingRequest(20L, 2, "note", null, "SUMMER"),
                principal(10L, "ROLE_USER"));

        assertEquals(new BigDecimal("1000.00"), response.subtotal());
        assertEquals(new BigDecimal("100.00"), response.discountAmount());
        assertEquals(new BigDecimal("900.00"), response.totalAmount());
        assertEquals("SUMMER", response.promotion().code());
        assertEquals(2, promotion.getPromotionUsedCount());
    }

    @Test
    void createRejectsInactivePromotion() {
        Promotion promotion = promotion("SUMMER", "10", 5, 0, null);
        promotion.setPromotionActive(false);
        when(promotionRepository.findByPromotionCodeIgnoreCaseForUpdate("SUMMER"))
                .thenReturn(Optional.of(promotion));

        ApiException exception = assertThrows(ApiException.class, () ->
                bookingService.create(new CreateBookingRequest(20L, 2, "note", null, "SUMMER"),
                        principal(10L, "ROLE_USER")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("Promotion is not active", exception.getMessage());
    }

    @Test
    void createRejectsExpiredPromotion() {
        Promotion promotion = promotion("SUMMER", "10", 5, 0, null);
        promotion.setEndDate(LocalDate.of(2026, 10, 31));
        when(promotionRepository.findByPromotionCodeIgnoreCaseForUpdate("SUMMER"))
                .thenReturn(Optional.of(promotion));

        ApiException exception = assertThrows(ApiException.class, () ->
                bookingService.create(new CreateBookingRequest(20L, 2, "note", null, "SUMMER"),
                        principal(10L, "ROLE_USER")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("Promotion is not valid today", exception.getMessage());
    }

    @Test
    void createRejectsPromotionOverUsageLimit() {
        Promotion promotion = promotion("SUMMER", "10", 1, 1, null);
        when(promotionRepository.findByPromotionCodeIgnoreCaseForUpdate("SUMMER"))
                .thenReturn(Optional.of(promotion));

        ApiException exception = assertThrows(ApiException.class, () ->
                bookingService.create(new CreateBookingRequest(20L, 2, "note", null, "SUMMER"),
                        principal(10L, "ROLE_USER")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("Promotion usage limit reached", exception.getMessage());
    }

    @Test
    void createRejectsPromotionBelowMinimumOrder() {
        Promotion promotion = promotion("SUMMER", "10", 5, 0, "2000.00");
        when(promotionRepository.findByPromotionCodeIgnoreCaseForUpdate("SUMMER"))
                .thenReturn(Optional.of(promotion));

        ApiException exception = assertThrows(ApiException.class, () ->
                bookingService.create(new CreateBookingRequest(20L, 2, "note", null, "SUMMER"),
                        principal(10L, "ROLE_USER")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("Order does not meet promotion minimum amount", exception.getMessage());
    }

    @Test
    void createRejectsUnknownPromotionCode() {
        when(promotionRepository.findByPromotionCodeIgnoreCaseForUpdate("NOPE"))
                .thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () ->
                bookingService.create(new CreateBookingRequest(20L, 2, "note", null, "NOPE"),
                        principal(10L, "ROLE_USER")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("Promotion code is invalid", exception.getMessage());
    }

    @Test
    void cancelReleasesSeatsAndPromotionUsage() {
        Promotion promotion = promotion("SUMMER", "10", 5, 3, null);
        booking.setPromotion(promotion);
        departure.setDepartureBookedSlots(2);
        when(promotionRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(promotion));

        var response = bookingService.cancel(30L, principal(10L, "ROLE_USER"));

        assertEquals("CANCELLED", response.status());
        assertEquals(0, departure.getDepartureBookedSlots());
        assertEquals(2, promotion.getPromotionUsedCount());
    }

    @Test
    void otherUserCannotCancelBooking() {
        ApiException exception = assertThrows(ApiException.class, () ->
                bookingService.cancel(30L, principal(11L, "ROLE_USER")));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("PENDING", booking.getBookingStatus());
        assertEquals(0, departure.getDepartureBookedSlots());
    }

    @Test
    void cancelRejectsPaidBooking() {
        booking.setBookingStatus("PAID");

        ApiException exception = assertThrows(ApiException.class, () ->
                bookingService.cancel(30L, principal(10L, "ROLE_USER")));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("Only pending unpaid bookings can be cancelled", exception.getMessage());
        assertEquals("PAID", booking.getBookingStatus());
    }

    @Test
    void cancelRejectsAlreadyCancelledBooking() {
        booking.setBookingStatus("CANCELLED");

        ApiException exception = assertThrows(ApiException.class, () ->
                bookingService.cancel(30L, principal(10L, "ROLE_USER")));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("Only pending unpaid bookings can be cancelled", exception.getMessage());
    }

    @Test
    void listScopesToCurrentUserAndDefaultsToNewestFirst() {
        when(bookingRepository.findByAccountAccountId(eq(10L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(booking)));

        var response = bookingService.list(PageRequest.of(0, 10), principal(10L, "ROLE_USER"));

        assertEquals(1, response.content().size());
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(bookingRepository).findByAccountAccountId(eq(10L), captor.capture());
        Sort.Order order = captor.getValue().getSort().getOrderFor("bookingCreatedAt");
        assertNotNull(order);
        assertEquals(Sort.Direction.DESC, order.getDirection());
    }

    @Test
    void detailIncludesAppliedPromotionInfo() {
        Promotion promotion = promotion("SUMMER", "10", 5, 1, null);
        booking.setPromotion(promotion);

        var response = bookingService.get(30L, principal(10L, "ROLE_USER"));

        assertEquals("SUMMER", response.promotion().code());
        assertEquals("Promo", response.promotion().name());
    }
}
