package com.j2ee.tour.controller;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.common.ApiResponse;
import com.j2ee.tour.common.OpenApiConfig;
import com.j2ee.tour.dto.response.PaymentResponse;
import com.j2ee.tour.security.AuthPrincipal;
import com.j2ee.tour.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demo-only payment endpoint. Disable with app.payment.mock-enabled=false.
 * The mock records a successful card payment; no provider or card data is used.
 */
@RestController
@RequestMapping("/api/bookings/{bookingId}/payments")
@Tag(name = "Payments", description = "Demo payment for the booking owner")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class MockPaymentController {

    private final PaymentService paymentService;
    private final boolean mockEnabled;

    public MockPaymentController(PaymentService paymentService,
                                 @Value("${app.payment.mock-enabled:false}") boolean mockEnabled) {
        this.paymentService = paymentService;
        this.mockEnabled = mockEnabled;
    }

    @PostMapping("/mock")
    @Operation(summary = "Pay a booking with the demo card",
            description = "Marks the owner's booking as PAID and creates an invoice. "
                    + "No real charge is made. Requires PAYMENT_MOCK_ENABLED=true.")
    public ResponseEntity<ApiResponse<PaymentResponse>> pay(
            @PathVariable Long bookingId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        if (!mockEnabled) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Mock payment is disabled");
        }
        return ResponseEntity.ok(ApiResponse.success(
                paymentService.payMock(bookingId, principal)));
    }
}
