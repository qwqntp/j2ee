package com.j2ee.tour;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.dto.request.BookingUpdateRequest;
import com.j2ee.tour.dto.request.CreateBookingRequest;
import com.j2ee.tour.dto.request.PaymentCallbackRequest;
import com.j2ee.tour.dto.request.PaymentCreateRequest;
import com.j2ee.tour.dto.request.PaymentUpdateRequest;
import com.j2ee.tour.integration.VnpayGateway;
import com.j2ee.tour.integration.VnpayGateway.Outcome;
import com.j2ee.tour.integration.VnpayGateway.VerifiedNotification;
import com.j2ee.tour.integration.VnpayQueryClient;
import com.j2ee.tour.integration.VnpayQueryClient.QueryCode;
import com.j2ee.tour.integration.VnpayQueryClient.QueryException;
import com.j2ee.tour.integration.VnpayQueryClient.QueryResult;
import com.j2ee.tour.model.Account;
import com.j2ee.tour.model.AccountRole;
import com.j2ee.tour.model.Invoice;
import com.j2ee.tour.model.Payment;
import com.j2ee.tour.model.Tour;
import com.j2ee.tour.model.TourDeparture;
import com.j2ee.tour.repository.AccountRepository;
import com.j2ee.tour.repository.AccountRoleRepository;
import com.j2ee.tour.repository.BookingRepository;
import com.j2ee.tour.repository.InvoiceRepository;
import com.j2ee.tour.repository.PaymentRepository;
import com.j2ee.tour.repository.TourDepartureRepository;
import com.j2ee.tour.repository.TourRepository;
import com.j2ee.tour.security.AuthPrincipal;
import com.j2ee.tour.service.BookingService;
import com.j2ee.tour.service.PaymentService;
import com.j2ee.tour.service.PaymentSignatureService;
import com.j2ee.tour.service.VnpayPaymentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static com.j2ee.tour.integration.VnpayTestPayload.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "app.vnpay.tmn-code=" + MERCHANT, "app.vnpay.hash-secret=" + KEY,
        "app.vnpay.ipn-token=" + TOKEN, "app.vnpay.pay-url=" + PAY, "app.vnpay.return-url=" + RETURN
})
class VnpayPaymentIntegrationTests {
    @Autowired private VnpayPaymentService vnpay;
    @Autowired private VnpayGateway gateway;
    @Autowired private PaymentService payments;
    @Autowired private PaymentSignatureService legacySignatures;
    @Autowired private BookingService bookings;
    @Autowired private AccountRepository accounts;
    @Autowired private AccountRoleRepository roles;
    @Autowired private TourRepository tours;
    @Autowired private TourDepartureRepository departures;
    @Autowired private PaymentRepository paymentRows;
    @Autowired private BookingRepository bookingRows;
    @Autowired private InvoiceRepository invoices;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transaction;
    @Autowired private Clock clock;
    @MockitoBean private VnpayQueryClient queries;

    private static final BigDecimal TOTAL = new BigDecimal("10000.50");
    private Long bookingId;
    private Long departureId;
    private Long tourId;
    private Long roleId;
    private AuthPrincipal owner;

    @BeforeEach
    void reserveAnOwnedBooking() {
        LocalDateTime now = LocalDateTime.now(clock);
        var account = new Account();
        account.setUsername("vnp-test-" + UUID.randomUUID());
        account.setPassword("synthetic-unused-password");
        account.setEmailVerified(1);
        account.setStatus(1);
        account.setAccountCreatedAt(now);
        account.setAccountUpdatedAt(now);
        var role = new AccountRole();
        role.setRoleName("vnp-test-" + UUID.randomUUID());
        role = roles.save(role);
        roleId = role.getAccountRoleId();
        account.setRole(role);
        account = accounts.save(account);
        owner = new AuthPrincipal(account.getAccountId(), account.getUsername(), "ROLE_USER");

        var tour = new Tour();
        tour.setTourName("VNPay test tour");
        tour.setTourStatus("OPEN");
        tour = tours.save(tour);
        tourId = tour.getTourId();
        var departure = new TourDeparture();
        departure.setTour(tour);
        departure.setDepartureStartDate(now.plusDays(5));
        departure.setDepartureEndDate(now.plusDays(6));
        departure.setDepartureCapacity(4);
        departure.setDepartureBookedSlots(0);
        departure.setDeparturePrice(TOTAL);
        departure.setDepartureStatus("OPEN");
        departure.setDepartureCreatedAt(now);
        departureId = departures.save(departure).getDepartureId();
        bookingId = bookings.create(new CreateBookingRequest(departureId, 1, null, null, null), owner).id();
    }

    @AfterEach
    void removeFixtures() {
        if (bookingId != null) {
            jdbc.update("delete from invoices where booking_id = ?", bookingId);
            jdbc.update("delete from payments where booking_id = ?", bookingId);
            jdbc.update("delete from bookings where booking_id = ?", bookingId);
        }
        if (departureId != null) departures.deleteById(departureId);
        if (tourId != null) tours.deleteById(tourId);
        if (owner != null) accounts.deleteById(owner.id());
        if (roleId != null) roles.deleteById(roleId);
    }

    @Test
    void chargesOnlyStoredBalanceAndNeverReopensAnUnresolvedLink() {
        var check = new Payment();
        check.setBooking(bookingRows.findById(bookingId).orElseThrow());
        check.setPaymentMethod("CASH");
        check.setPaymentAmount(new BigDecimal("0.50"));
        check.setPaymentStatus("SUCCESS");
        check.setPaymentCreatedAt(LocalDateTime.now(clock));
        check.setPaymentPaidAt(LocalDateTime.now(clock));
        paymentRows.save(check);

        var first = vnpay.start(bookingId, owner, "127.0.0.1");
        assertTrue(first.paymentUrl().getRawQuery().startsWith("vnp_Amount=1000000&"));
        assertConflict(this::start);
        assertEquals(1, paymentRows.findByBookingBookingId(bookingId).stream()
                .filter(p -> "VNPAY".equals(p.getPaymentMethod())).count());
        assertTrue(invoices.findByBookingBookingId(bookingId).isEmpty());
    }

    @Test
    void belowMinimumBalanceDoesNotLeaveAProtectedAttempt() {
        jdbc.update("update bookings set booking_total_amount = ? where booking_id = ?",
                new BigDecimal("9999.99"), bookingId);
        var error = assertThrows(ApiException.class, this::start);
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertTrue(error.getMessage().contains("10,000 VND"));
        assertTrue(paymentRows.findByBookingBookingId(bookingId).isEmpty());
        assertEquals("CANCELLED", bookings.cancel(bookingId, owner).status());
    }

    @Test
    void hidesForeignBookingAndAttemptEvenFromAdmin() {
        var other = new AuthPrincipal(owner.id() + 9000, "other", "ROLE_ADMIN");
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ApiException.class, () -> vnpay.start(bookingId, other, "127.0.0.1")).getStatus());
        String ref = start();
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ApiException.class, () -> vnpay.status(ref, other, "127.0.0.1")).getStatus());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () ->
                vnpay.verifyReturn(fields(ref, "1000050", "00", "00"), other, "127.0.0.1")).getStatus());
    }

    @Test
    void browserReturnBeforeIpnVerifiesButDoesNotFinalize() {
        String ref = start();
        var result = vnpay.verifyReturn(fields(ref, "1000050", "00", "00"), owner, "127.0.0.1");
        assertEquals("PENDING", result.status());
        assertEquals(VnpayGateway.Outcome.SUCCESS, result.returnOutcome());
        assertUnpaid(ref);
    }

    @Test
    void rejectsWrongAmountsAndUnknownReferences() {
        String ref = start();
        assertEquals("INVALID_AMOUNT", apply(ref, "1000000", "00", "00"));
        assertEquals("NOT_FOUND", apply("VNP" + "A".repeat(32), "1000050", "00", "00"));
        assertUnpaid(ref);
    }

    @Test
    void expiredUnresolvedAttemptBlocksRetriesAndFinancialMutations() {
        String ref = start();
        ageAttempt(ref);
        assertTrue(vnpay.status(ref, owner, "127.0.0.1").expired());
        assertConflict(this::start);
        assertConflict(() -> bookings.cancel(bookingId, owner));
        assertConflict(() -> bookings.update(bookingId, new BookingUpdateRequest(null, 2, null, null), owner));
        assertConflict(() -> payments.payMock(bookingId, owner));
        assertConflict(() -> payments.create(new PaymentCreateRequest(bookingId, "CASH", null, "SUCCESS")));
        String signature = legacySignatures.sign(bookingId, "other-txn", "CARD", TOTAL, true);
        assertConflict(() -> payments.handleCallback(bookingId,
                new PaymentCallbackRequest("other-txn", TOTAL, "CARD", true, signature), owner));
        Long paymentId = paymentRows.findByPaymentProviderTxnId(ref).orElseThrow().getPaymentId();
        assertConflict(() -> payments.update(paymentId, new PaymentUpdateRequest("VNPAY", null, "SUCCESS")));
        assertConflict(() -> { payments.delete(paymentId); return null; });
        assertUnpaid(ref);
    }

    @ParameterizedTest
    @ValueSource(strings = {"11", "24", "51"})
    void verifiedDefinitiveNonpaymentAllowsNewAttempt(String failure) {
        String ref = start();
        assertEquals("APPLIED", apply(ref, "1000050", failure, "02"));
        assertEquals("FAILED", vnpay.status(ref, owner, "127.0.0.1").status());
        assertEquals("ALREADY_PROCESSED", apply(ref, "1000050", failure, "02"));
        assertEquals("UNRESOLVED", apply(ref, "1000050", "00", "00"));
        assertNotEquals(ref, start());
        assertEquals("PENDING", bookings.get(bookingId, owner).status());
        assertTrue(invoices.findByBookingBookingId(bookingId).isEmpty());
    }

    @Test
    void potentiallyChargedOutcomeRemainsPending() {
        String ref = start();
        ageAttempt(ref);
        assertEquals("UNRESOLVED", apply(ref, "1000050", "07", "00"));
        assertConflict(this::start);
        assertUnpaid(ref);
    }

    @Test
    void verifiedQuerySettlesWhenIpnNeverArrives() {
        String ref = start();
        when(queries.query(eq(ref), any(), any(), any())).thenReturn(query(ref, Outcome.SUCCESS));

        var checked = vnpay.status(ref, owner, "127.0.0.1");

        assertEquals("SUCCESS", checked.status());
        assertEquals(QueryCode.VERIFIED_SUCCESS.name(), checked.reason());
        assertEquals("00", checked.providerCode());
        assertEquals("00", checked.providerStatus());
        assertEquals("PAID", bookings.get(bookingId, owner).status());
        var invoice = invoices.findByBookingBookingId(bookingId).orElseThrow();
        assertEquals("PAID", invoice.getStatus());
        assertEquals("VNPAY", invoice.getPaymentMethod());
        assertEquals("SUCCESS", vnpay.status(ref, owner, "127.0.0.1").status());
        verify(queries, times(1)).query(eq(ref), any(), any(), any());
    }

    @Test
    void reconciliationReportsTheActualProviderReason() {
        String ref = start();
        when(queries.query(eq(ref), any(), any(), any()))
                .thenReturn(new QueryResult(new VerifiedNotification(ref, 1000050L, Outcome.UNRESOLVED),
                        QueryCode.TRANSACTION_NOT_FOUND, "91", null));

        var notFound = vnpay.reconcileBooking(bookingId, owner, "127.0.0.1");

        assertEquals("PENDING", notFound.status());
        assertEquals(QueryCode.TRANSACTION_NOT_FOUND.name(), notFound.reason());
        assertEquals("91", notFound.providerCode());
        assertNull(notFound.providerStatus());

        when(queries.query(eq(ref), any(), any(), any()))
                .thenThrow(new QueryException(QueryCode.PROVIDER_TIMEOUT));

        var timedOut = vnpay.reconcileBooking(bookingId, owner, "127.0.0.1");

        assertEquals("PENDING", timedOut.status());
        assertEquals(QueryCode.PROVIDER_TIMEOUT.name(), timedOut.reason());
        assertNull(timedOut.providerCode());

        when(queries.query(eq(ref), any(), any(), any()))
                .thenThrow(new QueryException(QueryCode.PROVIDER_REJECTED, "99", null));

        var rejected = vnpay.reconcileBooking(bookingId, owner, "127.0.0.1");

        assertEquals("PENDING", rejected.status());
        assertEquals(QueryCode.PROVIDER_REJECTED.name(), rejected.reason());
        assertEquals("99", rejected.providerCode());
    }

    @Test
    void signedBrowserReturnSettlesOnlyThroughVerifiedQuery() {
        String ref = start();
        when(queries.query(eq(ref), any(), any(), any())).thenReturn(query(ref, Outcome.SUCCESS));

        var result = vnpay.verifyReturn(fields(ref, "1000050", "00", "00"), owner, "127.0.0.1");

        assertEquals("SUCCESS", result.status());
        assertEquals(Outcome.SUCCESS, result.returnOutcome());
        assertEquals(QueryCode.VERIFIED_SUCCESS.name(), result.reason());
        assertEquals("PAID", bookings.get(bookingId, owner).status());
    }

    @Test
    void rejectedQueryReleasesTheAttemptForRetry() {
        String ref = start();
        when(queries.query(eq(ref), any(), any(), any())).thenReturn(query(ref, Outcome.FAILED));

        String next = start();

        assertNotEquals(ref, next);
        assertEquals("FAILED", paymentRows.findByPaymentProviderTxnId(ref).orElseThrow().getPaymentStatus());
        assertEquals("PENDING", paymentRows.findByPaymentProviderTxnId(next).orElseThrow().getPaymentStatus());
        assertEquals("PENDING", bookings.get(bookingId, owner).status());
    }

    @Test
    void rejectedQueryAllowsCancellationOnlyAfterReconciliation() {
        String ref = start();
        when(queries.query(eq(ref), any(), any(), any())).thenReturn(query(ref, Outcome.FAILED));
        assertConflict(() -> bookings.cancel(bookingId, owner));

        var checked = vnpay.reconcileBooking(bookingId, owner, "127.0.0.1");

        assertTrue(checked.checked());
        assertEquals("FAILED", checked.status());
        assertEquals(QueryCode.VERIFIED_NONPAYMENT.name(), checked.reason());
        assertEquals("CANCELLED", bookings.cancel(bookingId, owner).status());
        assertEquals("FAILED", paymentRows.findByPaymentProviderTxnId(ref).orElseThrow().getPaymentStatus());
    }

    @Test
    void ambiguousOrUnavailableQueryKeepsAttemptProtected() {
        String ref = start();
        when(queries.query(eq(ref), any(), any(), any()))
                .thenReturn(new QueryResult(new VerifiedNotification(ref, 1000050L, Outcome.UNRESOLVED),
                        QueryCode.TRANSACTION_UNRESOLVED, "00", "01"));

        var checked = vnpay.reconcileBooking(bookingId, owner, "127.0.0.1");

        assertTrue(checked.checked());
        assertEquals("PENDING", checked.status());
        assertEquals(QueryCode.TRANSACTION_UNRESOLVED.name(), checked.reason());
        assertEquals("00", checked.providerCode());
        assertEquals("01", checked.providerStatus());
        assertConflict(this::start);
        assertConflict(() -> bookings.cancel(bookingId, owner));

        when(queries.query(eq(ref), any(), any(), any()))
                .thenThrow(new QueryException(QueryCode.PROVIDER_UNREACHABLE));
        var unavailable = vnpay.status(ref, owner, "127.0.0.1");
        assertEquals("PENDING", unavailable.status());
        assertEquals(QueryCode.PROVIDER_UNREACHABLE.name(), unavailable.reason());
        assertConflict(this::start);
        assertUnpaid(ref);
    }

    @Test
    void queryAnswerForAnotherAttemptOrAmountIsIgnored() {
        String ref = start();
        when(queries.query(eq(ref), any(), any(), any()))
                .thenReturn(query("VNP" + "B".repeat(32), Outcome.SUCCESS));
        var foreign = vnpay.reconcileBooking(bookingId, owner, "127.0.0.1");
        assertEquals("PENDING", foreign.status());
        assertEquals(QueryCode.PROVIDER_MISMATCH.name(), foreign.reason());

        when(queries.query(eq(ref), any(), any(), any()))
                .thenReturn(new QueryResult(new VerifiedNotification(ref, 1000000L, Outcome.SUCCESS),
                        QueryCode.VERIFIED_SUCCESS, "00", "00"));
        assertEquals("PENDING", vnpay.status(ref, owner, "127.0.0.1").status());
        assertUnpaid(ref);
    }

    @Test
    void settledAttemptAndEmptyBookingAreNotQueriedAgain() {
        var empty = vnpay.reconcileBooking(bookingId, owner, "127.0.0.1");
        assertFalse(empty.checked());
        assertNull(empty.reference());
        verify(queries, never()).query(any(), any(), any(), any());

        String ref = start();
        assertEquals("APPLIED", apply(ref, "1000050", "00", "00"));
        assertEquals("SUCCESS", vnpay.status(ref, owner, "127.0.0.1").status());
        verify(queries, never()).query(any(), any(), any(), any());
    }


    @Test
    void concurrentDuplicateDelayedIpnsSettleExactlyOnce() throws Exception {
        String ref = start();
        ageAttempt(ref);
        var gate = new CountDownLatch(1);
        Callable<String> callback = () -> {
            assertTrue(gate.await(5, TimeUnit.SECONDS));
            return apply(ref, "1000050", "00", "00");
        };
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(callback);
            var second = pool.submit(callback);
            gate.countDown();
            assertEquals(Set.of("APPLIED", "ALREADY_PROCESSED"),
                    Set.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)));
        }
        assertEquals("PAID", bookings.get(bookingId, owner).status());
        assertEquals("SUCCESS", vnpay.status(ref, owner, "127.0.0.1").status());
        assertEquals(1, paymentRows.findByBookingBookingId(bookingId).size());
        var invoice = invoices.findByBookingBookingId(bookingId).orElseThrow();
        assertEquals("PAID", invoice.getStatus());
        assertEquals("VNPAY", invoice.getPaymentMethod());
        assertEquals("UNRESOLVED", apply(ref, "1000050", "24", "02"));
        assertEquals(invoice.getInvoiceId(), invoices.findByBookingBookingId(bookingId).orElseThrow().getInvoiceId());
    }

    @Test
    void lateContradictoryIpnDoesNotWaitOnTheDepartureLock() throws Exception {
        String ref = start();
        assertEquals("APPLIED", apply(ref, "1000050", "24", "02"));
        try (var pool = Executors.newSingleThreadExecutor()) {
            transaction.executeWithoutResult(tx -> {
                departures.findByIdForUpdate(departureId).orElseThrow();
                var lateIpn = pool.submit(() -> apply(ref, "1000050", "00", "00"));
                try {
                    assertEquals("UNRESOLVED", lateIpn.get(2, TimeUnit.SECONDS));
                } catch (Exception failure) {
                    throw new AssertionError("Terminal IPN should not wait on the departure lock", failure);
                }
            });
        }
        assertEquals("CANCELLED", bookings.cancel(bookingId, owner).status());
    }

    @Test
    void ipnDoesNotOverwriteAnInvoiceConcurrentlyMarkedPaid() throws Exception {
        String ref = start();
        var invoice = createInvoice("UNPAID");
        try (var pool = Executors.newSingleThreadExecutor()) {
            var pending = transaction.execute(tx -> {
                jdbc.update("update invoices set status = 'PAID', payment_method = 'CASH' where invoice_id = ?",
                        invoice.getInvoiceId());
                var callback = pool.submit(() -> apply(ref, "1000050", "00", "00"));
                awaitCallbackBlockedByInvoiceUpdate();
                return callback;
            });
            assertEquals("APPLIED", pending.get(15, TimeUnit.SECONDS));
        }
        assertEquals("CASH", invoices.findByBookingBookingId(bookingId).orElseThrow().getPaymentMethod());
    }

    @Test
    void manualSuccessCannotRaceAnUnresolvedOrJustSettledAttempt() throws Exception {
        String ref = start();
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var callback = pool.submit(() -> {
                assertTrue(gate.await(5, TimeUnit.SECONDS));
                return apply(ref, "1000050", "00", "00");
            });
            var manual = pool.submit(() -> {
                assertTrue(gate.await(5, TimeUnit.SECONDS));
                assertConflict(() -> payments.create(new PaymentCreateRequest(bookingId, "CASH", null, "SUCCESS")));
                return true;
            });
            gate.countDown();
            assertEquals("APPLIED", callback.get(15, TimeUnit.SECONDS));
            assertTrue(manual.get(15, TimeUnit.SECONDS));
        }
        assertEquals(1, paymentRows.findByBookingBookingId(bookingId).size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNPAID", "PAID"})
    void settlesExistingUnpaidInvoiceButDoesNotRewritePaidInvoice(String initial) {
        String ref = start();
        Long id = createInvoice(initial).getInvoiceId();

        assertEquals("APPLIED", apply(ref, "1000050", "00", "00"));
        var settled = invoices.findByBookingBookingId(bookingId).orElseThrow();
        assertEquals(id, settled.getInvoiceId());
        assertEquals("PAID", settled.getStatus());
        assertEquals("PAID".equals(initial) ? "CASH" : "VNPAY", settled.getPaymentMethod());
    }

    private Invoice createInvoice(String initial) {
        var invoice = new Invoice();
        invoice.setBooking(bookingRows.findById(bookingId).orElseThrow());
        invoice.setInvoiceCreatedAt(LocalDateTime.now(clock));
        invoice.setStatus(initial);
        invoice.setTotalAmount(TOTAL);
        invoice.setDiscountAmount(BigDecimal.ZERO);
        invoice.setTaxAmount(BigDecimal.ZERO);
        invoice.setServiceFee(BigDecimal.ZERO);
        invoice.setPaymentMethod("CASH");
        return invoices.save(invoice);
    }

    private void awaitCallbackBlockedByInvoiceUpdate() {
        Integer holdingPid = jdbc.queryForObject("select pg_backend_pid()", Integer.class);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            jdbc.execute("select pg_stat_clear_snapshot()");
            if (Boolean.TRUE.equals(jdbc.queryForObject(
                    "select exists (select 1 from pg_stat_activity where ? = any(pg_blocking_pids(pid)))",
                    Boolean.class, holdingPid))) {
                return;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(15));
        }
        fail("The callback never reached the locked invoice");
    }

    private String start() {
        return vnpay.start(bookingId, owner, "127.0.0.1").reference();
    }

    private static QueryResult query(String reference, Outcome outcome) {
        QueryCode code = outcome == Outcome.SUCCESS ? QueryCode.VERIFIED_SUCCESS
                : outcome == Outcome.FAILED ? QueryCode.VERIFIED_NONPAYMENT
                : QueryCode.TRANSACTION_UNRESOLVED;
        String status = outcome == Outcome.SUCCESS ? "00" : outcome == Outcome.FAILED ? "02" : "01";
        return new QueryResult(new VerifiedNotification(reference, 1000050L, outcome), code, "00", status);
    }

    private String apply(String ref, String amount, String response, String status) {
        return vnpay.applyIpn(gateway.verify(fields(ref, amount, response, status))).name();
    }

    private void ageAttempt(String ref) {
        jdbc.update("update payments set payment_created_at = ? where payment_provider_txn_id = ?",
                LocalDateTime.now(clock).minusMinutes(16), ref);
    }

    private void assertUnpaid(String ref) {
        assertEquals("PENDING", vnpay.status(ref, owner, "127.0.0.1").status());
        assertEquals("PENDING", bookings.get(bookingId, owner).status());
        assertTrue(invoices.findByBookingBookingId(bookingId).isEmpty());
        assertEquals(1, paymentRows.findByBookingBookingId(bookingId).size());
    }

    private static void assertConflict(Callable<?> action) {
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class, action::call).getStatus());
    }
}
