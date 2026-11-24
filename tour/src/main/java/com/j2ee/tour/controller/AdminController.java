package com.j2ee.tour.controller;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.common.ApiResponse;
import com.j2ee.tour.common.OpenApiConfig;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.response.AccountResponse;
import com.j2ee.tour.dto.response.AdminStatsResponse;
import com.j2ee.tour.service.AdminService;
import com.j2ee.tour.service.AdminStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Validated
@Tag(name = "Admin", description = "Admin-only operations")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AdminController {

    private final AdminService adminService;

    private final AdminStatsService adminStatsService;

    @GetMapping("/stats")
    @Operation(summary = "Admin dashboard summary",
            description = "Optional from/to dates (ISO yyyy-MM-dd) filter successful payments.")
    public ApiResponse<AdminStatsResponse> stats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if ((from == null) != (to == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "from and to must be supplied together");
        }
        return ApiResponse.success(from == null
                ? adminStatsService.stats()
                : adminStatsService.stats(from, to));
    }

    @GetMapping("/accounts")
    @Operation(summary = "List accounts",
            description = "Requires ROLE_ADMIN. page is 0-based; size is 1-100. Results are sorted by account id.")
    public ApiResponse<PageResponse<AccountResponse>> accounts(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(adminService.listAccounts(page, size));
    }
}
