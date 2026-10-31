package com.j2ee.tour.controller;

import com.j2ee.tour.common.ApiResponse;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.AccountRoleRequest;
import com.j2ee.tour.dto.response.AccountRoleResponse;
import com.j2ee.tour.service.AccountRoleService;
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
@RequestMapping("/api/account-roles")
public class AccountRoleController {
    private final AccountRoleService accountRoleService;

    public AccountRoleController(AccountRoleService accountRoleService) {
        this.accountRoleService = accountRoleService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AccountRoleResponse>>> list(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(accountRoleService.list(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AccountRoleResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(accountRoleService.get(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AccountRoleResponse>> create(@Valid @RequestBody AccountRoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(accountRoleService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AccountRoleResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody AccountRoleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(accountRoleService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        accountRoleService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }
}
