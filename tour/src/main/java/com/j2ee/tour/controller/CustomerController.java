package com.j2ee.tour.controller;

import com.j2ee.tour.common.ApiResponse;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.CustomerRequest;
import com.j2ee.tour.dto.response.CustomerResponse;
import com.j2ee.tour.service.CustomerService;
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
@RequestMapping("/api/customers")
public class CustomerController {
    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CustomerResponse>>> list(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(customerService.list(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(customerService.get(id)));
    }

    @GetMapping("/by-account/{accountId}")
    public ResponseEntity<ApiResponse<CustomerResponse>> getByAccount(@PathVariable Long accountId) {
        return ResponseEntity.ok(ApiResponse.success(customerService.getByAccount(accountId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CustomerResponse>> create(@Valid @RequestBody CustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(customerService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody CustomerRequest request) {
        return ResponseEntity.ok(ApiResponse.success(customerService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        customerService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }
}
