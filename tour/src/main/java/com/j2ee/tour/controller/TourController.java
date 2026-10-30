package com.j2ee.tour.controller;

import com.j2ee.tour.common.ApiResponse;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.TourRequest;
import com.j2ee.tour.dto.response.TourResponse;
import com.j2ee.tour.service.TourService;
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
@RequestMapping("/api/tours")
public class TourController {
    private final TourService tourService;

    public TourController(TourService tourService) {
        this.tourService = tourService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<TourResponse>>> list(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(tourService.list(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TourResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(tourService.get(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TourResponse>> create(@Valid @RequestBody TourRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(tourService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TourResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody TourRequest request) {
        return ResponseEntity.ok(ApiResponse.success(tourService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        tourService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }
}
