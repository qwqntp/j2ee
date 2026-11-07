package com.j2ee.tour.service;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.dto.request.PaymentCallbackRequest;
import com.j2ee.tour.dto.request.PaymentCreateRequest;
import com.j2ee.tour.dto.request.PaymentUpdateRequest;
import com.j2ee.tour.model.Account;
import com.j2ee.tour.model.Booking;
import com.j2ee.tour.model.Payment;
import com.j2ee.tour.repository.BookingRepository;
import com.j2ee.tour.repository.PaymentRepository;
import com.j2ee.tour.repository.RefundRepository;
import com.j2ee.tour.security.AuthPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    private static final String SECRET = "unit-test-callback-secret";
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 11, 5, 10, 30);

    private BookingRepository bookingRepository;
    private PaymentRepository paymentRepository;
    private InvoiceService invoiceService;
    private PaymentService paymentService;

    private Booking booking;
    private Account account;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        RefundRepository refundRepository = mock(RefundRepository.class);
        invoiceService = mock(InvoiceService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-05T03:30:00Z"),
                ZoneId.of("Asia/Ho_Chi_Minh"));
        paymentService = new PaymentService(bookingRepository, paymentRepository, refundRepository,
                new PaymentSignatureService(SECRET), invoiceService, clock);

        account = new Account();
        account.setAccountId(10L);
        account.setUsername("user");

        booking = new Booking();
        booking.setBookingId(30L);
        booking.setBookingCode("BK-20261105-0001");
        booking.setAccount(account);
        booking.setBookingGuestCount(2);
        booking.setBookingUnitPrice(new BigDecimal("500.00"));
        booking.setBookingSubtotal(new BigDecimal("1000.00"));
        booking.setBookingDiscountAmount(BigDecimal.ZERO);
        booking.setBookingTotalAmount(new BigDecimal("1000.00"));
        booking.setBookingStatus("PENDING");
        booking.setBookingCreatedAt(NOW);

        when(bookingRepository.findById(30L)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByPaymentProviderTxnId(any())).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.sumSuccessfulPayments(30L)).thenReturn(new BigDecimal("1000.00"));
    }

    private AuthPrincipal owner() {
        return new AuthPrincipal(10L, "user", "ROLE_USER");
    }

    private AuthPrincipal otherUser() {
        return new AuthPrincipal(11L, "other", "ROLE_USER");
    }

    private AuthPrincipal admin() {
        return new AuthPrincipal(99L, "admin", "ROLE_ADMIN");
    }

    private String sign(Long bookingId, String txnId, BigDecimal amount, String method,
                        boolean successful) {
        String payload = bookingId + "|" + txnId + "|" + method + "|" + successful + "|"
                + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private PaymentCallbackRequest callback(String txn, String amount, boolean successful) {
        BigDecimal value = new BigDecimal(amount);
        return new PaymentCallbackRequest(txn, value, "CARD", successful,
                sign(30L, txn, value, "CARD", successful));
    }

    @Test
    void callbackRejectsInvalidSignature() {
        PaymentCallbackRequest request = new PaymentCallbackRequest(
                "txn-bad", new BigDecimal("1000.00"), "CARD", true, "not-a-signature");

        ApiException exception = assertThrows(ApiException.class,
                () -> paymentService.handleCallback(30L, request, owner()));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void callbackRejectsTamperedAmount() {
        PaymentCallbackRequest request = callback("txn-small", "1.00", true);

        ApiException exception = assertThrows(ApiException.class,
                () -> paymentService.handleCallback(30L, request, owner()));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void callbackStoresBookingTotalAndServerPaidAt() {
        var response = paymentService.handleCallback(30L, callback("txn-ok", "1000.00", true), owner());

        assertEquals(new BigDecimal("1000.00"), response.amount());
        assertEquals("SUCCESS", response.status());
        assertEquals(NOW, response.paidAt());
        assertEquals("PAID", booking.getBookingStatus());
        verify(invoiceService).createForPaidBooking(booking);
    }

    @Test
    void otherUserCannotConfirmPaymentForBooking() {
        ApiException exception = assertThrows(ApiException.class,
                () -> paymentService.handleCallback(30L, callback("txn-other", "1000.00", true), otherUser()));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    void callbackIsIdempotentForTheSameBooking() {
        Payment existing = new Payment();
        existing.setPaymentId(50L);
        existing.setBooking(booking);
        existing.setPaymentAmount(new BigDecimal("1000.00"));
        existing.setPaymentMethod("CARD");
        existing.setPaymentProviderTxnId("txn-ok");
        existing.setPaymentStatus("SUCCESS");
        existing.setPaymentPaidAt(NOW);
        existing.setPaymentCreatedAt(NOW);
        when(paymentRepository.findByPaymentProviderTxnId("txn-ok")).thenReturn(Optional.of(existing));

        var response = paymentService.handleCallback(30L, callback("txn-ok", "1000.00", true), owner());

        assertEquals(50L, response.id());
    }

    @Test
    void callbackRejectsProviderTransactionForAnotherBooking() {
        Booking otherBooking = new Booking();
        otherBooking.setBookingId(31L);
        Payment existing = new Payment();
        existing.setPaymentId(51L);
        existing.setBooking(otherBooking);
        existing.setPaymentProviderTxnId("txn-ok");
        when(paymentRepository.findByPaymentProviderTxnId("txn-ok")).thenReturn(Optional.of(existing));

        ApiException exception = assertThrows(ApiException.class,
                () -> paymentService.handleCallback(30L, callback("txn-ok", "1000.00", true), admin()));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
    }

    @Test
    void createDerivesPendingPaymentFromBooking() {
        var response = paymentService.create(
                new PaymentCreateRequest(30L, "CASH", "txn-cash"));

        assertEquals(new BigDecimal("1000.00"), response.amount());
        assertEquals("PENDING", response.status());
        assertNull(response.paidAt());
    }

    @Test
    void updateDerivesPaidAtFromServerForSuccessfulPayment() {
        Payment payment = new Payment();
        payment.setPaymentId(50L);
        payment.setBooking(booking);
        payment.setPaymentAmount(new BigDecimal("1000.00"));
        payment.setPaymentMethod("CASH");
        payment.setPaymentStatus("PENDING");
        payment.setPaymentCreatedAt(NOW);
        when(paymentRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(payment));

        var response = paymentService.update(50L,
                new PaymentUpdateRequest("CASH", null, "SUCCESS"));

        assertEquals("SUCCESS", response.status());
        assertNotNull(response.paidAt());
    }
}
