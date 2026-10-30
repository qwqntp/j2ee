package com.j2ee.tour.controller;

import com.j2ee.tour.common.ApiResponse;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.TravelVehicleRequest;
import com.j2ee.tour.dto.response.TravelVehicleResponse;
import com.j2ee.tour.service.TravelVehicleService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/travel-vehicles")
public class TravelVehicleController {
    private final TravelVehicleService vehicleService;

    public TravelVehicleController(TravelVehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<TravelVehicleResponse>>> list(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(vehicleService.list(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TravelVehicleResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(vehicleService.get(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TravelVehicleResponse>> create(@Valid @RequestBody TravelVehicleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(vehicleService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TravelVehicleResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody TravelVehicleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(vehicleService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        vehicleService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }
}
