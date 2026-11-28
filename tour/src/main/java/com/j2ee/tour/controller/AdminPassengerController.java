package com.j2ee.tour.controller;

import com.j2ee.tour.common.ApiResponse;
import com.j2ee.tour.dto.response.AdminPassengerManifest;
import com.j2ee.tour.service.AdminPassengerService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/departures")
public class AdminPassengerController {
    private final AdminPassengerService service;

    public AdminPassengerController(AdminPassengerService service) {
        this.service = service;
    }

    @GetMapping("/{id}/passengers")
    @Operation(summary = "List passengers for a departure", description = "Requires ROLE_ADMIN; excludes cancelled bookings.")
    public ResponseEntity<ApiResponse<AdminPassengerManifest>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.get(id)));
    }
}
