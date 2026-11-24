package com.j2ee.tour.controller;

import com.j2ee.tour.common.ApiResponse;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.response.AdminPaymentItem;
import com.j2ee.tour.service.AdminPaymentQueryService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/payments")
public class AdminPaymentController {
    private final AdminPaymentQueryService service;
    public AdminPaymentController(AdminPaymentQueryService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "List payments", description = "Requires ROLE_ADMIN; newest first, with status, method, date and keyword filters.")
    public ResponseEntity<ApiResponse<PageResponse<AdminPaymentItem>>> list(
            @PageableDefault(size = 10) Pageable pageable,
            @RequestParam(required = false) String status, @RequestParam(required = false) String method,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(ApiResponse.success(service.list(pageable, status, method, from, to, q)));
    }
}
