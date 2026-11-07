package com.j2ee.tour.controller;

import com.j2ee.tour.common.ApiResponse;
import com.j2ee.tour.dto.request.TourRatingRequest;
import com.j2ee.tour.dto.response.TourRatingPageResponse;
import com.j2ee.tour.dto.response.TourRatingResponse;
import com.j2ee.tour.security.AuthPrincipal;
import com.j2ee.tour.service.TourRatingService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tours")
public class TourRatingController {

    private final TourRatingService ratingService;

    public TourRatingController(TourRatingService ratingService) {
        this.ratingService = ratingService;
    }

    @GetMapping("/{tourId}/ratings")
    public ResponseEntity<ApiResponse<TourRatingPageResponse>> list(
            @PathVariable Long tourId, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(ratingService.list(tourId, pageable)));
    }

    @PostMapping("/{tourId}/ratings")
    public ResponseEntity<ApiResponse<TourRatingResponse>> create(
            @PathVariable Long tourId,
            @Valid @RequestBody TourRatingRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ratingService.create(tourId, request, principal)));
    }
}
