package com.j2ee.tour.service;

import com.j2ee.tour.dto.request.PaymentCallbackRequest;
import com.j2ee.tour.dto.response.PaymentResponse;
import com.j2ee.tour.model.Booking;
import com.j2ee.tour.model.Payment;
import com.j2ee.tour.repository.BookingRepository;
import com.j2ee.tour.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

@Service
public class PaymentService {
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;

    public PaymentService(BookingRepository bookingRepository, PaymentRepository paymentRepository) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public PaymentResponse handleCallback(Long bookingId, PaymentCallbackRequest request) {
        var existing = paymentRepository.findByPaymentProviderTxnId(request.providerTransactionId());
        if (existing.isPresent()) {
            return PaymentResponse.from(existing.get());
        }

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NoSuchElementException("Booking not found"));
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setPaymentAmount(request.amount());
        payment.setPaymentMethod(request.method());
        payment.setPaymentProviderTxnId(request.providerTransactionId());
        payment.setPaymentStatus(Boolean.TRUE.equals(request.successful()) ? "SUCCESS" : "FAILED");
        payment.setPaymentPaidAt(Boolean.TRUE.equals(request.successful()) ? LocalDateTime.now() : null);
        Payment saved = paymentRepository.save(payment);

        if ("SUCCESS".equals(saved.getPaymentStatus())
                && paymentRepository.sumSuccessfulPayments(bookingId).compareTo(booking.getBookingTotalAmount()) >= 0) {
            booking.setBookingStatus("PAID");
            bookingRepository.save(booking);
        }
        return PaymentResponse.from(saved);
    }
}