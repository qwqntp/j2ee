package com.j2ee.tour.controller;

import com.j2ee.tour.common.ApiResponse;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.TourImageRequest;
import com.j2ee.tour.dto.response.TourImageResponse;
import com.j2ee.tour.service.TourImageService;
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
@RequestMapping("/api/tour-images")
public class TourImageController {
    private final TourImageService tourImageService;

    public TourImageController(TourImageService tourImageService) {
        this.tourImageService = tourImageService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<TourImageResponse>>> list(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(tourImageService.list(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TourImageResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(tourImageService.get(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TourImageResponse>> create(@Valid @RequestBody TourImageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(tourImageService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TourImageResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody TourImageRequest request) {
        return ResponseEntity.ok(ApiResponse.success(tourImageService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        tourImageService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }
}
