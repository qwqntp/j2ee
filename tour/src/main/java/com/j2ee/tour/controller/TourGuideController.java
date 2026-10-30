package com.j2ee.tour.controller;

import com.j2ee.tour.common.ApiResponse;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.TourGuideRequest;
import com.j2ee.tour.dto.response.TourGuideResponse;
import com.j2ee.tour.service.TourGuideService;
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
@RequestMapping("/api/tour-guides")
public class TourGuideController {
    private final TourGuideService tourGuideService;

    public TourGuideController(TourGuideService tourGuideService) {
        this.tourGuideService = tourGuideService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<TourGuideResponse>>> list(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(tourGuideService.list(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TourGuideResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(tourGuideService.get(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TourGuideResponse>> create(@Valid @RequestBody TourGuideRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(tourGuideService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TourGuideResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody TourGuideRequest request) {
        return ResponseEntity.ok(ApiResponse.success(tourGuideService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        tourGuideService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }
}
