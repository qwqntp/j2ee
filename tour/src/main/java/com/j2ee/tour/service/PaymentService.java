package com.j2ee.tour.service;

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
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;

@Service
public class PaymentService {
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;

    public PaymentService(BookingRepository bookingRepository,
                          PaymentRepository paymentRepository,
                          RefundRepository refundRepository) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
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
    public PaymentResponse handleCallback(Long bookingId, PaymentCallbackRequest request) {
        var existing = paymentRepository.findByPaymentProviderTxnId(request.providerTransactionId());
        if (existing.isPresent()) {
            return PaymentResponse.from(existing.get());
        }

        Booking booking = findBooking(bookingId);
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setPaymentAmount(request.amount());
        payment.setPaymentMethod(request.method());
        payment.setPaymentProviderTxnId(request.providerTransactionId());
        payment.setPaymentStatus(Boolean.TRUE.equals(request.successful()) ? "SUCCESS" : "FAILED");
        payment.setPaymentPaidAt(Boolean.TRUE.equals(request.successful()) ? LocalDateTime.now() : null);
        payment.setPaymentCreatedAt(LocalDateTime.now());
        Payment saved = paymentRepository.save(payment);
        recomputeBookingStatus(booking);
        return PaymentResponse.from(saved);
    }

    @Transactional
    public PaymentResponse create(PaymentCreateRequest request) {
        if (request.providerTxnId() != null) {
            var existing = paymentRepository.findByPaymentProviderTxnId(request.providerTxnId());
            if (existing.isPresent()) {
                return PaymentResponse.from(existing.get());
            }
        }
        Booking booking = findBooking(request.bookingId());
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setPaymentAmount(request.amount());
        payment.setPaymentMethod(request.paymentMethod());
        payment.setPaymentProviderTxnId(request.providerTxnId());
        payment.setPaymentStatus(request.status() == null ? "PENDING" : request.status());
        payment.setPaymentPaidAt(resolvePaidAt(payment.getPaymentStatus(), request.paidAt(), null));
        payment.setPaymentCreatedAt(LocalDateTime.now());
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
        payment.setPaymentPaidAt(resolvePaidAt(payment.getPaymentStatus(), request.paidAt(), payment.getPaymentPaidAt()));
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

    private LocalDateTime resolvePaidAt(String status, LocalDateTime requestedPaidAt, LocalDateTime currentPaidAt) {
        if ("SUCCESS".equals(status)) {
            if (requestedPaidAt != null) {
                return requestedPaidAt;
            }
            return currentPaidAt == null ? LocalDateTime.now() : currentPaidAt;
        }
        if (requestedPaidAt != null) {
            throw new IllegalStateException("Only successful payments can have a paid timestamp");
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
        }
    }

    private Booking findBooking(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Booking not found"));
    }

    private Payment find(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Payment not found"));
    }
}
