package com.j2ee.tour.controller;

import com.j2ee.tour.common.ApiResponse;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.response.AdminDepartureItem;
import com.j2ee.tour.service.AdminDepartureQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/departures")
public class AdminDepartureController {
    private final AdminDepartureQueryService service;

    public AdminDepartureController(AdminDepartureQueryService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminDepartureItem>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "true") boolean upcomingOnly,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(ApiResponse.success(service.list(page, size, upcomingOnly, status, q)));
    }
}
