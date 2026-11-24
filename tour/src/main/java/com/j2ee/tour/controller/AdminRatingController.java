package com.j2ee.tour.controller;

import com.j2ee.tour.common.ApiResponse;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.RatingModerationRequest;
import com.j2ee.tour.dto.response.TourRatingResponse;
import com.j2ee.tour.service.TourRatingService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ratings")
public class AdminRatingController {
    private final TourRatingService ratingService;

    public AdminRatingController(TourRatingService ratingService) {
        this.ratingService = ratingService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<TourRatingResponse>>> list(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(ratingService.listAll(pageable)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TourRatingResponse>> moderate(
            @PathVariable Long id,
            @Valid @RequestBody RatingModerationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(ratingService.moderate(id, request.status())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        ratingService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }
}
