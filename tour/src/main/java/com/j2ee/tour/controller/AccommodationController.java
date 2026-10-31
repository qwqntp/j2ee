package com.j2ee.tour.controller;

import com.j2ee.tour.common.ApiResponse;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.AccommodationRequest;
import com.j2ee.tour.dto.response.AccommodationResponse;
import com.j2ee.tour.service.AccommodationService;
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
@RequestMapping("/api/accommodations")
public class AccommodationController {
    private final AccommodationService accommodationService;

    public AccommodationController(AccommodationService accommodationService) {
        this.accommodationService = accommodationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AccommodationResponse>>> list(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(accommodationService.list(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AccommodationResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(accommodationService.get(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AccommodationResponse>> create(@Valid @RequestBody AccommodationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(accommodationService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AccommodationResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody AccommodationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(accommodationService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        accommodationService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }
}
