package com.j2ee.tour.service;

import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.CreateRefundRequest;
import com.j2ee.tour.dto.request.RefundUpdateRequest;
import com.j2ee.tour.dto.response.RefundResponse;
import com.j2ee.tour.model.Refund;
import com.j2ee.tour.repository.PaymentRepository;
import com.j2ee.tour.repository.RefundRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
public class RefundService {
    private static final Set<String> RESERVED_STATUSES = Set.of("REQUESTED", "APPROVED", "DONE");

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;

    public RefundService(PaymentRepository paymentRepository, RefundRepository refundRepository) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<RefundResponse> list(Pageable pageable) {
        return PageResponse.from(refundRepository.findAll(pageable), RefundResponse::from);
    }

    @Transactional(readOnly = true)
    public RefundResponse get(Long id) {
        return RefundResponse.from(find(id));
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

    @Transactional
    public RefundResponse update(Long id, RefundUpdateRequest request) {
        Refund refund = find(id);
        if (request.reason() != null) {
            refund.setRefundReason(request.reason());
        }
        if (request.status() != null && !request.status().equals(refund.getRefundStatus())) {
            String previousStatus = refund.getRefundStatus();
            if ("REJECTED".equals(previousStatus) && RESERVED_STATUSES.contains(request.status())) {
                var payment = paymentRepository.findByIdForUpdate(refund.getPayment().getPaymentId())
                        .orElseThrow(() -> new NoSuchElementException("Payment not found"));
                if (refundRepository.sumReservedRefunds(payment.getPaymentId()).add(refund.getRefundAmount())
                        .compareTo(payment.getPaymentAmount()) > 0) {
                    throw new IllegalStateException("Refund amount exceeds payment amount");
                }
            }
            refund.setRefundStatus(request.status());
            if (RESERVED_STATUSES.contains(request.status()) && !"REQUESTED".equals(request.status())) {
                refund.setRefundProcessedAt(LocalDateTime.now());
            } else if ("REJECTED".equals(request.status()) || "REQUESTED".equals(request.status())) {
                refund.setRefundProcessedAt("REJECTED".equals(request.status()) ? LocalDateTime.now() : null);
            }
        }
        return RefundResponse.from(refundRepository.save(refund));
    }

    @Transactional
    public void delete(Long id) {
        refundRepository.delete(find(id));
    }

    private Refund find(Long id) {
        return refundRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Refund not found"));
    }
}
