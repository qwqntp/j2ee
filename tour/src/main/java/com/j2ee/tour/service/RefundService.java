package com.j2ee.tour.service;

import com.j2ee.tour.dto.request.CreateRefundRequest;
import com.j2ee.tour.dto.response.RefundResponse;
import com.j2ee.tour.model.Refund;
import com.j2ee.tour.repository.PaymentRepository;
import com.j2ee.tour.repository.RefundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

@Service
public class RefundService {
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;

    public RefundService(PaymentRepository paymentRepository, RefundRepository refundRepository) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
    }

    @Transactional
    public RefundResponse request(Long paymentId, CreateRefundRequest request) {
        var payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new NoSuchElementException("Payment not found"));
        if (!"SUCCESS".equals(payment.getPaymentStatus())) {
            throw new IllegalStateException("Only successful payments can be refunded");
        }
        if (refundRepository.sumReservedRefunds(paymentId).add(request.amount()).compareTo(payment.getPaymentAmount()) > 0) {
            throw new IllegalStateException("Refund amount exceeds payment amount");
        }

        Refund refund = new Refund();
        refund.setPayment(payment);
        refund.setBooking(payment.getBooking());
        refund.setRefundAmount(request.amount());
        refund.setRefundReason(request.reason());
        refund.setRefundStatus("REQUESTED");
        refund.setRefundRequestedAt(LocalDateTime.now());
        return RefundResponse.from(refundRepository.save(refund));
    }
}