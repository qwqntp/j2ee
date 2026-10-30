package com.j2ee.tour.controller;

import com.j2ee.tour.common.ApiResponse;
import com.j2ee.tour.dto.request.CreateRefundRequest;
import com.j2ee.tour.dto.response.RefundResponse;
import com.j2ee.tour.service.RefundService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments/{paymentId}/refunds")
public class RefundController {
    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RefundResponse>> request(
            @PathVariable Long paymentId,
            @Valid @RequestBody CreateRefundRequest request) {
        return ResponseEntity.ok(ApiResponse.success(refundService.request(paymentId, request)));
    }
}