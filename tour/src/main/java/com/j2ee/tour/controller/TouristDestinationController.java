package com.j2ee.tour.controller;

import com.j2ee.tour.common.ApiResponse;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.TouristDestinationRequest;
import com.j2ee.tour.dto.response.TouristDestinationResponse;
import com.j2ee.tour.service.TouristDestinationService;
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
@RequestMapping("/api/tourist-destinations")
public class TouristDestinationController {
    private final TouristDestinationService destinationService;

    public TouristDestinationController(TouristDestinationService destinationService) {
        this.destinationService = destinationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<TouristDestinationResponse>>> list(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(destinationService.list(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TouristDestinationResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(destinationService.get(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TouristDestinationResponse>> create(
            @Valid @RequestBody TouristDestinationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(destinationService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TouristDestinationResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody TouristDestinationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(destinationService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        destinationService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }
}
