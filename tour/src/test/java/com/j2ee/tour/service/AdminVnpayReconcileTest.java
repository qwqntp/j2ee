package com.j2ee.tour.service;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.integration.VnpayGateway;
import com.j2ee.tour.integration.VnpayQueryClient;
import com.j2ee.tour.model.Payment;
import com.j2ee.tour.model.Booking;
import com.j2ee.tour.model.Account;
import com.j2ee.tour.repository.BookingRepository;
import com.j2ee.tour.repository.PaymentRepository;
import com.j2ee.tour.security.AuthPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminVnpayReconcileTest {
    @Test
    void customerReconciliationStillRejectsAnotherOwner() {
        var bookings = mock(BookingRepository.class);
        var account = new Account();
        account.setAccountId(2L);
        var booking = new Booking();
        booking.setAccount(account);
        when(bookings.findById(77L)).thenReturn(Optional.of(booking));
        var queries = mock(VnpayQueryClient.class);
        var service = new VnpayPaymentService(mock(VnpayGateway.class), queries,
                bookings, mock(PaymentRepository.class), mock(PaymentService.class),
                mock(TransactionTemplate.class), Clock.systemUTC());
        assertThrows(ApiException.class, () -> service.reconcileBooking(77L,
                new AuthPrincipal(1L, "other", "ROLE_USER"), "127.0.0.1"));
        verifyNoInteractions(queries);
    }

    @Test
    void queriesPendingPaymentForAnyBookingWithoutFabricatingAnOwner() {
        var payments = mock(PaymentRepository.class);
        var queries = mock(VnpayQueryClient.class);
        var booking = new Booking();
        booking.setBookingId(77L);
        var payment = new Payment();
        payment.setBooking(booking);
        payment.setPaymentMethod("VNPAY");
        payment.setPaymentStatus("PENDING");
        payment.setPaymentAmount(BigDecimal.TEN);
        payment.setPaymentCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        payment.setPaymentProviderTxnId("VNP" + "A".repeat(32));
        when(payments.findById(3L)).thenReturn(Optional.of(payment));
        var service = new VnpayPaymentService(mock(VnpayGateway.class), queries,
                mock(BookingRepository.class), payments, mock(PaymentService.class),
                mock(TransactionTemplate.class), Clock.systemUTC());

        var result = service.reconcileAdminPayment(3L, "127.0.0.1");

        assertEquals(77L, result.bookingId());
        assertEquals("PENDING", result.status());
        verify(queries).query(anyString(), eq(BigDecimal.TEN), any(), eq("127.0.0.1"));
        verify(payments, never()).save(any());
    }

    @Test
    void rejectsNonPendingPaymentWithoutCallingProvider() {
        var payments = mock(PaymentRepository.class);
        var queries = mock(VnpayQueryClient.class);
        var payment = new Payment();
        payment.setPaymentMethod("VNPAY");
        payment.setPaymentStatus("SUCCESS");
        when(payments.findById(3L)).thenReturn(Optional.of(payment));
        var service = new VnpayPaymentService(mock(VnpayGateway.class), queries,
                mock(BookingRepository.class), payments, mock(PaymentService.class),
                mock(TransactionTemplate.class), Clock.systemUTC());
        assertThrows(ApiException.class, () -> service.reconcileAdminPayment(3L, "127.0.0.1"));
        verifyNoInteractions(queries);
    }
}
