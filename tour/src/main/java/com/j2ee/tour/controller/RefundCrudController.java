package com.j2ee.tour.controller;

import com.j2ee.tour.common.ApiResponse;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.RefundUpdateRequest;
import com.j2ee.tour.dto.response.RefundResponse;
import com.j2ee.tour.service.RefundService;
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
@RequestMapping("/api/refunds")
public class RefundCrudController {
    private final RefundService refundService;

    public RefundCrudController(RefundService refundService) {
        this.refundService = refundService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<RefundResponse>>> list(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(refundService.list(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RefundResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(refundService.get(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RefundResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody RefundUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(refundService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        refundService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }
}
