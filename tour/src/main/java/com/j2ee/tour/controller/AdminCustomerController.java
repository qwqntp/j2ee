package com.j2ee.tour.controller;

import com.j2ee.tour.common.ApiResponse;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.response.AdminCustomerBooking;
import com.j2ee.tour.dto.response.CustomerResponse;
import com.j2ee.tour.service.AdminCustomerQueryService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/customers")
public class AdminCustomerController {
    private final AdminCustomerQueryService service;
    public AdminCustomerController(AdminCustomerQueryService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Search customers", description = "Requires ROLE_ADMIN; searches name, email and phone.")
    public ResponseEntity<ApiResponse<PageResponse<CustomerResponse>>> list(@PageableDefault(size = 10) Pageable page,
                                                                              @RequestParam(required = false) String q) {
        return ResponseEntity.ok(ApiResponse.success(service.list(page, q)));
    }

    @GetMapping("/{id}/bookings")
    @Operation(summary = "List a customer's bookings", description = "Requires ROLE_ADMIN; newest first.")
    public ResponseEntity<ApiResponse<PageResponse<AdminCustomerBooking>>> bookings(@PathVariable Long id,
                                                                                    @PageableDefault(size = 10) Pageable page) {
        return ResponseEntity.ok(ApiResponse.success(service.bookings(id, page)));
    }
}
