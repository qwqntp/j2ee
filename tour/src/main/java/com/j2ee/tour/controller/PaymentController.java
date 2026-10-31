package com.j2ee.tour.controller;

import com.j2ee.tour.common.ApiResponse;
import com.j2ee.tour.dto.request.PaymentCallbackRequest;
import com.j2ee.tour.dto.response.PaymentResponse;
import com.j2ee.tour.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings/{bookingId}/payments")
public class PaymentController {
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/callback")
    public ResponseEntity<ApiResponse<PaymentResponse>> callback(
            @PathVariable Long bookingId,
            @Valid @RequestBody PaymentCallbackRequest request) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.handleCallback(bookingId, request)));
    }
}