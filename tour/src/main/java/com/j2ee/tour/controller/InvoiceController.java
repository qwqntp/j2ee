package com.j2ee.tour.controller;

import com.j2ee.tour.common.ApiResponse;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.InvoiceRequest;
import com.j2ee.tour.dto.response.InvoiceResponse;
import com.j2ee.tour.service.InvoiceService;
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
@RequestMapping("/api/invoices")
public class InvoiceController {
    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<InvoiceResponse>>> list(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(invoiceService.list(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InvoiceResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(invoiceService.get(id)));
    }

    @GetMapping("/by-booking/{bookingId}")
    public ResponseEntity<ApiResponse<InvoiceResponse>> getByBooking(@PathVariable Long bookingId) {
        return ResponseEntity.ok(ApiResponse.success(invoiceService.getByBooking(bookingId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<InvoiceResponse>> create(@Valid @RequestBody InvoiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(invoiceService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<InvoiceResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody InvoiceRequest request) {
        return ResponseEntity.ok(ApiResponse.success(invoiceService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        invoiceService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }
}
