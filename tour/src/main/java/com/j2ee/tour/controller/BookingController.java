package com.j2ee.tour.controller;

import com.j2ee.tour.common.ApiResponse;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.BookingUpdateRequest;
import com.j2ee.tour.dto.request.CreateBookingRequest;
import com.j2ee.tour.dto.response.BookingResponse;
import com.j2ee.tour.service.BookingService;
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
@RequestMapping("/api/bookings")
public class BookingController {
    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<BookingResponse>>> list(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.list(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BookingResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.get(id)));
    }

    @GetMapping("/code/{bookingCode}")
    public ResponseEntity<ApiResponse<BookingResponse>> getByCode(@PathVariable String bookingCode) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.getByCode(bookingCode)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BookingResponse>> create(@Valid @RequestBody CreateBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(bookingService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BookingResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody BookingUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        bookingService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }
}
