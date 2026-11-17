package com.j2ee.tour.controller;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.common.ApiResponse;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.AdminBookingFilter;
import com.j2ee.tour.dto.request.BookingUpdateRequest;
import com.j2ee.tour.dto.request.CreateBookingRequest;
import com.j2ee.tour.dto.response.BookingResponse;
import com.j2ee.tour.security.AuthPrincipal;
import com.j2ee.tour.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {
    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<BookingResponse>>> list(
            Pageable pageable,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q,
            @AuthenticationPrincipal AuthPrincipal principal) {
        if (pageable.getPageSize() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "size must be between 1 and 100");
        }
        AdminBookingFilter filter = new AdminBookingFilter(status, from, to, q);
        return ResponseEntity.ok(ApiResponse.success(
                bookingService.list(pageable, principal, filter)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BookingResponse>> get(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.get(id, principal)));
    }

    @GetMapping("/code/{bookingCode}")
    public ResponseEntity<ApiResponse<BookingResponse>> getByCode(
            @PathVariable String bookingCode,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                bookingService.getByCode(bookingCode, principal)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BookingResponse>> create(
            @Valid @RequestBody CreateBookingRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                bookingService.create(request, principal)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BookingResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody BookingUpdateRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.update(id, request, principal)));
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirm a PAID booking",
            description = "Moves a booking from PAID to CONFIRMED. Owner or admin.")
    public ResponseEntity<ApiResponse<BookingResponse>> confirm(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.confirm(id, principal)));
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Mark a CONFIRMED booking completed",
            description = "Moves a booking from CONFIRMED to COMPLETED after the trip. Owner or admin.")
    public ResponseEntity<ApiResponse<BookingResponse>> complete(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.complete(id, principal)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<BookingResponse>> cancel(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.cancel(id, principal)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthPrincipal principal) {
        bookingService.delete(id, principal);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }
}
