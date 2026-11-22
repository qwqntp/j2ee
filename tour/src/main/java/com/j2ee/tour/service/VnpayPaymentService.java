package com.j2ee.tour.service;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.dto.response.VnpayReconcileResponse;
import com.j2ee.tour.dto.response.VnpayStartResponse;
import com.j2ee.tour.dto.response.VnpayStatusResponse;
import com.j2ee.tour.integration.VnpayGateway;
import com.j2ee.tour.integration.VnpayGateway.Outcome;
import com.j2ee.tour.integration.VnpayGateway.VerifiedNotification;
import com.j2ee.tour.integration.VnpayQueryClient;
import com.j2ee.tour.integration.VnpayQueryClient.QueryCode;
import com.j2ee.tour.integration.VnpayQueryClient.QueryException;
import com.j2ee.tour.integration.VnpayQueryClient.QueryResult;
import com.j2ee.tour.model.Booking;
import com.j2ee.tour.model.Payment;
import com.j2ee.tour.repository.BookingRepository;
import com.j2ee.tour.repository.PaymentRepository;
import com.j2ee.tour.security.AuthPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class VnpayPaymentService {
    static final String METHOD = "VNPAY";
    private static final String PREFIX = "VNP";
    private static final String UNRESOLVED_MESSAGE =
            "An earlier VNPay payment is still unresolved. Check its status before trying again; "
                    + "it can be released once VNPay confirms it was not paid.";

    private final VnpayGateway gateway;
    private final VnpayQueryClient queries;
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final PaymentService paymentService;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public VnpayPaymentService(VnpayGateway gateway, VnpayQueryClient queries,
                               BookingRepository bookings, PaymentRepository payments,
                               PaymentService paymentService, TransactionTemplate transaction,
                               Clock clock) {
        this.gateway = gateway;
        this.queries = queries;
        this.bookings = bookings;
        this.payments = payments;
        this.paymentService = paymentService;
        this.transaction = transaction;
        this.clock = clock;
    }

    public VnpayStartResponse start(Long bookingId, AuthPrincipal principal, String clientIp) {
        // QueryDr needs a server-to-server call, so it runs before the booking lock and its
        // own transaction. A slow provider must not stall other bookings.
        reconcilePending(bookingId, principal, clientIp);
        return transaction.execute(status -> beginAttempt(bookingId, principal, clientIp));
    }

    VnpayStartResponse beginAttempt(Long bookingId, AuthPrincipal principal, String clientIp) {
        Booking booking = bookings.findByIdForUpdate(bookingId).orElseThrow(VnpayPaymentService::notFound);
        requireOwner(booking, principal);
        gateway.requireConfigured();
        String departureStatus = booking.getDeparture().getDepartureStatus();
        if (!"PENDING".equals(booking.getBookingStatus())
                || !("OPEN".equals(departureStatus) || "FULL".equals(departureStatus))
                || !booking.getDeparture().getDepartureStartDate().isAfter(LocalDateTime.now(clock))) {
            throw new ApiException(HttpStatus.CONFLICT, "Booking is not awaiting payment");
        }

        BigDecimal remaining = remaining(booking);
        try {
            VnpayGateway.encodeAmount(remaining);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "Booking balance is not payable with VNPay");
        }
        if (remaining.compareTo(VnpayGateway.MINIMUM_AMOUNT) < 0) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "VNPay requires a balance of at least 10,000 VND; this booking has "
                            + remaining.stripTrailingZeros().toPlainString() + " VND left to pay");
        }
        // A local timeout or an expired link does not prove the customer was not charged.
        // Only a verified gateway outcome may release the attempt's protection.
        if (managedAttempts(payments, bookingId).stream()
                .anyMatch(p -> "PENDING".equals(p.getPaymentStatus()))) {
            throw new ApiException(HttpStatus.CONFLICT, UNRESOLVED_MESSAGE);
        }

        Instant now = clock.instant();
        Payment attempt = new Payment();
        attempt.setBooking(booking);
        attempt.setPaymentMethod(METHOD);
        attempt.setPaymentAmount(remaining);
        attempt.setPaymentStatus("PENDING");
        attempt.setPaymentProviderTxnId(PREFIX + UUID.randomUUID().toString().replace("-", "")
                .toUpperCase(Locale.ROOT));
        attempt.setPaymentCreatedAt(LocalDateTime.ofInstant(now.truncatedTo(ChronoUnit.SECONDS), clock.getZone()));
        return paymentLink(payments.save(attempt), clientIp);
    }

    public VnpayStatusResponse verifyReturn(Map<String, List<String>> params, AuthPrincipal principal,
                                            String clientIp) {
        var notification = gateway.verify(params);
        Payment payment = ownedAttempt(notification.reference(), principal);
        if (!matchesAmount(payment, notification)) {
            throw new VnpayGateway.VerificationException(VnpayGateway.Reason.INVALID_AMOUNT);
        }
        return statusOf(reconcile(payment, clientIp), notification.outcome());
    }

    public VnpayStatusResponse status(String reference, AuthPrincipal principal, String clientIp) {
        return statusOf(reconcile(ownedAttempt(reference, principal), clientIp), null);
    }

    public VnpayReconcileResponse reconcileBooking(Long bookingId, AuthPrincipal principal, String clientIp) {
        Booking booking = bookings.findById(bookingId).orElseThrow(VnpayPaymentService::notFound);
        requireOwner(booking, principal);
        Payment pending = pendingAttempt(bookingId);
        if (pending == null) return VnpayReconcileResponse.nothingToCheck(bookingId);
        Reconciled checked = reconcile(pending, clientIp);
        return new VnpayReconcileResponse(bookingId, checked.payment().getPaymentProviderTxnId(),
                checked.payment().getPaymentStatus(), true,
                checked.reason() == null ? null : checked.reason().name(),
                checked.providerCode(), checked.providerStatus());
    }

    @Transactional
    public IpnCode applyIpn(VerifiedNotification notification) {
        return applyVerified(notification.reference(), notification.amountTimes100(), notification.outcome());
    }

    /**
     * Applies an already verified gateway outcome. The booking lock serialises this with
     * IPN delivery, competing payments, and the departure cancellation cascade.
     */
    IpnCode applyVerified(String reference, long amountTimes100, Outcome outcome) {
        if (!isManagedReference(reference)) return IpnCode.NOT_FOUND;
        Long bookingId = payments.findVnpayBookingId(reference).orElse(null);
        if (bookingId == null) return IpnCode.NOT_FOUND;
        Booking booking = bookings.findByIdForUpdate(bookingId).orElse(null);
        Payment payment = findAttempt(reference).orElse(null);
        if (booking == null || payment == null) return IpnCode.NOT_FOUND;
        if (!matchesAmount(payment, amountTimes100)) return IpnCode.INVALID_AMOUNT;
        if (outcome == Outcome.UNRESOLVED) return IpnCode.UNRESOLVED;

        boolean success = outcome == Outcome.SUCCESS;
        String status = success ? "SUCCESS" : "FAILED";
        if (!"PENDING".equals(payment.getPaymentStatus())) {
            return status.equals(payment.getPaymentStatus()) ? IpnCode.ALREADY_PROCESSED : IpnCode.UNRESOLVED;
        }
        if (success && ("CANCELLED".equals(booking.getDeparture().getDepartureStatus())
                || !"PENDING".equals(booking.getBookingStatus())
                || remaining(booking).compareTo(payment.getPaymentAmount()) != 0)) {
            return IpnCode.UNRESOLVED;
        }

        payment.setPaymentStatus(status);
        if (success) payment.setPaymentPaidAt(LocalDateTime.now(clock));
        payments.save(payment);
        if (success) paymentService.recomputeBookingStatus(booking, METHOD);
        return IpnCode.APPLIED;
    }

    /**
     * Asks VNPay about one pending attempt and settles only on a verified answer.
     * Provider or signature failures leave the attempt PENDING.
     */
    private Reconciled reconcile(Payment attempt, String clientIp) {
        if (!"PENDING".equals(attempt.getPaymentStatus())) return Reconciled.notQueried(attempt);
        QueryResult result;
        try {
            result = queries.query(attempt.getPaymentProviderTxnId(), attempt.getPaymentAmount(),
                    attempt.getPaymentCreatedAt().atZone(clock.getZone()).toInstant(), clientIp);
        } catch (QueryException exception) {
            return new Reconciled(attempt, exception.code(), exception.providerCode(),
                    exception.providerStatus());
        }
        if (result == null || result.notification() == null) {
            return new Reconciled(attempt, QueryCode.PROVIDER_MALFORMED_RESPONSE, null, null);
        }
        VerifiedNotification notification = result.notification();
        if (!attempt.getPaymentProviderTxnId().equals(notification.reference())
                || VnpayGateway.encodeAmount(attempt.getPaymentAmount()) != notification.amountTimes100()) {
            return new Reconciled(attempt, QueryCode.PROVIDER_MISMATCH,
                    result.providerCode(), result.providerStatus());
        }
        if (notification.outcome() == Outcome.UNRESOLVED) {
            return new Reconciled(attempt, result.code(), result.providerCode(), result.providerStatus());
        }
        transaction.executeWithoutResult(status ->
                applyVerified(notification.reference(), notification.amountTimes100(), notification.outcome()));
        return new Reconciled(findAttempt(notification.reference()).orElse(attempt), result.code(),
                result.providerCode(), result.providerStatus());
    }

    private void reconcilePending(Long bookingId, AuthPrincipal principal, String clientIp) {
        Booking booking = bookings.findById(bookingId).orElseThrow(VnpayPaymentService::notFound);
        requireOwner(booking, principal);
        Payment pending = pendingAttempt(bookingId);
        if (pending != null) reconcile(pending, clientIp);
    }

    private Payment pendingAttempt(Long bookingId) {
        return managedAttempts(payments, bookingId).stream()
                .filter(p -> "PENDING".equals(p.getPaymentStatus())).findFirst().orElse(null);
    }

    static boolean isManagedReference(String reference) {
        return reference != null && reference.matches("VNP[0-9A-F]{32}");
    }

    static List<Payment> managedAttempts(PaymentRepository repository, Long bookingId) {
        return repository.findByBookingBookingIdAndPaymentMethodAndPaymentProviderTxnIdStartingWith(
                bookingId, METHOD, PREFIX).stream()
                .filter(payment -> isManagedReference(payment.getPaymentProviderTxnId())).toList();
    }

    static void requireNoUnresolvedAttempt(PaymentRepository repository, Long bookingId) {
        if (managedAttempts(repository, bookingId).stream().anyMatch(p -> "PENDING".equals(p.getPaymentStatus()))) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "VNPay has not confirmed this payment yet. Check its status before changing this booking.");
        }
    }

    private Optional<Payment> findAttempt(String reference) {
        return isManagedReference(reference) ? payments.findByPaymentProviderTxnId(reference)
                .filter(p -> METHOD.equals(p.getPaymentMethod())) : Optional.empty();
    }

    private Payment ownedAttempt(String reference, AuthPrincipal principal) {
        Payment payment = findAttempt(reference).orElseThrow(VnpayPaymentService::notFound);
        requireOwner(payment.getBooking(), principal);
        return payment;
    }

    private static void requireOwner(Booking booking, AuthPrincipal principal) {
        if (booking.getAccount() == null || !principal.id().equals(booking.getAccount().getAccountId())) {
            throw notFound();
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Booking or VNPay attempt not found");
    }

    private BigDecimal remaining(Booking booking) {
        return booking.getBookingTotalAmount().subtract(payments.sumSuccessfulPayments(booking.getBookingId()));
    }

    private static boolean matchesAmount(Payment payment, VerifiedNotification notification) {
        return matchesAmount(payment, notification.amountTimes100());
    }

    private static boolean matchesAmount(Payment payment, long amountTimes100) {
        return VnpayGateway.encodeAmount(payment.getPaymentAmount()) == amountTimes100;
    }

    private VnpayStartResponse paymentLink(Payment payment, String ip) {
        var url = gateway.createPaymentUrl(payment.getPaymentProviderTxnId(), payment.getPaymentAmount(),
                payment.getPaymentCreatedAt().atZone(clock.getZone()).toInstant(), ip);
        return new VnpayStartResponse(payment.getPaymentProviderTxnId(), url, expiresAt(payment));
    }

    private VnpayStatusResponse statusOf(Reconciled reconciled, Outcome returnOutcome) {
        Payment payment = reconciled.payment();
        return new VnpayStatusResponse(payment.getBooking().getBookingId(), payment.getPaymentProviderTxnId(),
                payment.getPaymentStatus(), !clock.instant().isBefore(expiresAt(payment)), returnOutcome,
                reconciled.reason() == null ? null : reconciled.reason().name(),
                reconciled.providerCode(), reconciled.providerStatus());
    }

    private Instant expiresAt(Payment payment) {
        return payment.getPaymentCreatedAt().atZone(clock.getZone()).toInstant().plus(VnpayGateway.PAYMENT_WINDOW);
    }

    public enum IpnCode { APPLIED, ALREADY_PROCESSED, NOT_FOUND, INVALID_AMOUNT, UNRESOLVED }

    /**
     * One reconciliation outcome: the attempt after any verified settlement, plus the safe
     * reason the attempt is still pending. {@code reason} is null when no query was needed.
     */
    private record Reconciled(Payment payment, QueryCode reason, String providerCode,
                              String providerStatus) {
        static Reconciled notQueried(Payment payment) {
            return new Reconciled(payment, null, null, null);
        }
    }
}
