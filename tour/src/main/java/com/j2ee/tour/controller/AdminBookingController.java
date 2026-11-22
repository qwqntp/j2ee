package com.j2ee.tour.controller;

import com.j2ee.tour.common.ApiResponse;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.AdminBookingFilter;
import com.j2ee.tour.dto.response.AdminBookingListItem;
import com.j2ee.tour.service.AdminBookingQueryService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/bookings")
public class AdminBookingController {
    private final AdminBookingQueryService adminBookingQueryService;

    public AdminBookingController(AdminBookingQueryService adminBookingQueryService) {
        this.adminBookingQueryService = adminBookingQueryService;
    }

    @GetMapping
    @Operation(summary = "List bookings for the admin table",
            description = "Requires ROLE_ADMIN. page is 0-based; size defaults to 10 and is capped by "
                    + "app.admin.list.max-page-size. Search, status and date filters apply to the full dataset.")
    public ResponseEntity<ApiResponse<PageResponse<AdminBookingListItem>>> list(
            @PageableDefault(size = 10) Pageable pageable,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q) {
        AdminBookingFilter filter = new AdminBookingFilter(status, from, to, q);
        return ResponseEntity.ok(ApiResponse.success(
                adminBookingQueryService.list(pageable, filter)));
    }
}
