package com.j2ee.tour.service;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.PaymentCallbackRequest;
import com.j2ee.tour.dto.request.PaymentCreateRequest;
import com.j2ee.tour.dto.request.PaymentUpdateRequest;
import com.j2ee.tour.dto.response.PaymentResponse;
import com.j2ee.tour.model.Booking;
import com.j2ee.tour.model.Payment;
import com.j2ee.tour.repository.BookingRepository;
import com.j2ee.tour.repository.PaymentRepository;
import com.j2ee.tour.repository.RefundRepository;
import com.j2ee.tour.security.AuthPrincipal;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;

@Service
public class PaymentService {
    private static final String ADMIN_ROLE = "ROLE_ADMIN";

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final PaymentSignatureService paymentSignatureService;
    private final InvoiceService invoiceService;
    private final Clock clock;

    public PaymentService(BookingRepository bookingRepository,
                          PaymentRepository paymentRepository,
                          RefundRepository refundRepository,
                          PaymentSignatureService paymentSignatureService,
                          InvoiceService invoiceService,
                          Clock clock) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.paymentSignatureService = paymentSignatureService;
        this.invoiceService = invoiceService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> list(Pageable pageable) {
        return PageResponse.from(paymentRepository.findAll(pageable), PaymentResponse::from);
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(Long id) {
        return PaymentResponse.from(find(id));
    }

    @Transactional
    public PaymentResponse handleCallback(Long bookingId, PaymentCallbackRequest request,
                                          AuthPrincipal principal) {
        paymentSignatureService.verify(bookingId, request);

        var existing = paymentRepository.findByPaymentProviderTxnId(request.providerTransactionId());
        if (existing.isPresent()) {
            if (!bookingId.equals(existing.get().getBooking().getBookingId())) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Provider transaction ID belongs to another booking");
            }
            return PaymentResponse.from(existing.get());
        }

        Booking booking = findOwnedBooking(bookingId, principal);
        BigDecimal expectedAmount = booking.getBookingTotalAmount();
        if (request.amount().compareTo(expectedAmount) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Payment amount does not match booking total");
        }

        boolean successful = Boolean.TRUE.equals(request.successful());
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setPaymentAmount(expectedAmount);
        payment.setPaymentMethod(request.method());
        payment.setPaymentProviderTxnId(request.providerTransactionId());
        payment.setPaymentStatus(successful ? "SUCCESS" : "FAILED");
        payment.setPaymentPaidAt(successful ? LocalDateTime.now(clock) : null);
        payment.setPaymentCreatedAt(LocalDateTime.now(clock));
        Payment saved = paymentRepository.save(payment);
        recomputeBookingStatus(booking);
        return PaymentResponse.from(saved);
    }

    @Transactional
    public PaymentResponse create(PaymentCreateRequest request) {
        if (request.providerTxnId() != null) {
            var existing = paymentRepository.findByPaymentProviderTxnId(request.providerTxnId());
            if (existing.isPresent()) {
                if (!request.bookingId().equals(existing.get().getBooking().getBookingId())) {
                    throw new ApiException(HttpStatus.CONFLICT,
                            "Provider transaction ID is already used");
                }
                return PaymentResponse.from(existing.get());
            }
        }
        Booking booking = findBooking(request.bookingId());
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setPaymentAmount(booking.getBookingTotalAmount());
        payment.setPaymentMethod(request.paymentMethod());
        payment.setPaymentProviderTxnId(request.providerTxnId());
        payment.setPaymentStatus("PENDING");
        payment.setPaymentPaidAt(null);
        payment.setPaymentCreatedAt(LocalDateTime.now(clock));
        Payment saved = paymentRepository.save(payment);
        recomputeBookingStatus(booking);
        return PaymentResponse.from(saved);
    }

    @Transactional
    public PaymentResponse update(Long id, PaymentUpdateRequest request) {
        Payment payment = paymentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NoSuchElementException("Payment not found"));

        if (request.providerTxnId() != null) {
            paymentRepository.findByPaymentProviderTxnId(request.providerTxnId())
                    .filter(existing -> !existing.getPaymentId().equals(id))
                    .ifPresent(existing -> {
                        throw new IllegalStateException("Provider transaction ID is already used");
                    });
            payment.setPaymentProviderTxnId(request.providerTxnId());
        }
        if (request.paymentMethod() != null) {
            payment.setPaymentMethod(request.paymentMethod());
        }
        if (request.status() != null) {
            payment.setPaymentStatus(request.status());
        }
        payment.setPaymentPaidAt(resolvePaidAt(payment.getPaymentStatus(), payment.getPaymentPaidAt()));
        Payment saved = paymentRepository.save(payment);
        recomputeBookingStatus(saved.getBooking());
        return PaymentResponse.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        Payment payment = find(id);
        if (refundRepository.existsByPaymentPaymentId(id)) {
            throw new IllegalStateException("Cannot delete a payment that has refunds");
        }
        Booking booking = payment.getBooking();
        paymentRepository.delete(payment);
        recomputeBookingStatus(booking);
    }

    private LocalDateTime resolvePaidAt(String status, LocalDateTime currentPaidAt) {
        if ("SUCCESS".equals(status)) {
            return currentPaidAt == null ? LocalDateTime.now(clock) : currentPaidAt;
        }
        return null;
    }

    private void recomputeBookingStatus(Booking booking) {
        String status = booking.getBookingStatus();
        if (!"PENDING".equals(status) && !"PAID".equals(status)) {
            return;
        }
        BigDecimal paid = paymentRepository.sumSuccessfulPayments(booking.getBookingId());
        String newStatus = paid.compareTo(booking.getBookingTotalAmount()) >= 0 ? "PAID" : "PENDING";
        if (!newStatus.equals(status)) {
            booking.setBookingStatus(newStatus);
            bookingRepository.save(booking);
            if ("PAID".equals(newStatus)) {
                invoiceService.createForPaidBooking(booking);
            }
        }
    }

    private Booking findBooking(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Booking not found"));
    }

    private Booking findOwnedBooking(Long id, AuthPrincipal principal) {
        Booking booking = findBooking(id);
        if (ADMIN_ROLE.equals(principal.role())) {
            return booking;
        }
        if (booking.getAccount() == null
                || !principal.id().equals(booking.getAccount().getAccountId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Booking not found");
        }
        return booking;
    }

    private Payment find(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Payment not found"));
    }
}
