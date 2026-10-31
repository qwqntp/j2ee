package com.j2ee.tour.controller;

import com.j2ee.tour.common.ApiResponse;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.TourDepartureRequest;
import com.j2ee.tour.dto.response.TourDepartureResponse;
import com.j2ee.tour.service.TourDepartureService;
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
@RequestMapping("/api/tour-departures")
public class TourDepartureController {
    private final TourDepartureService departureService;

    public TourDepartureController(TourDepartureService departureService) {
        this.departureService = departureService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<TourDepartureResponse>>> list(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(departureService.list(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TourDepartureResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(departureService.get(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TourDepartureResponse>> create(
            @Valid @RequestBody TourDepartureRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(departureService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TourDepartureResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody TourDepartureRequest request) {
        return ResponseEntity.ok(ApiResponse.success(departureService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        departureService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }
}
