package com.j2ee.tour.controller;

import com.j2ee.tour.common.ApiResponse;
import com.j2ee.tour.dto.response.VnpayReconcileResponse;
import com.j2ee.tour.service.VnpayPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/payments")
public class AdminVnpayReconcileController {
    private final VnpayPaymentService service;
    public AdminVnpayReconcileController(VnpayPaymentService service) { this.service = service; }

    @PostMapping("/{id}/reconcile")
    @Operation(summary = "Reconcile a pending VNPay payment", description = "Requires ROLE_ADMIN. VNPay verifies the outcome; no amount or status is accepted from the client.")
    public ResponseEntity<ApiResponse<VnpayReconcileResponse>> reconcile(@PathVariable Long id,
                                                                          HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(service.reconcileAdminPayment(id, request.getRemoteAddr())));
    }
}
