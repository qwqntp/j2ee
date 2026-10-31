package com.j2ee.tour.controller;

import com.j2ee.tour.common.ApiResponse;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.TravelerRequest;
import com.j2ee.tour.dto.response.TravelerResponse;
import com.j2ee.tour.service.TravelerService;
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
@RequestMapping("/api/travelers")
public class TravelerController {
    private final TravelerService travelerService;

    public TravelerController(TravelerService travelerService) {
        this.travelerService = travelerService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<TravelerResponse>>> list(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(travelerService.list(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TravelerResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(travelerService.get(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TravelerResponse>> create(@Valid @RequestBody TravelerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(travelerService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TravelerResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody TravelerRequest request) {
        return ResponseEntity.ok(ApiResponse.success(travelerService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        travelerService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }
}
