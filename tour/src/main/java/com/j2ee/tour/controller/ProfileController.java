package com.j2ee.tour.controller;

import com.j2ee.tour.common.ApiResponse;
import com.j2ee.tour.common.OpenApiConfig;
import com.j2ee.tour.dto.request.ProfileRequest;
import com.j2ee.tour.dto.response.ProfileResponse;
import com.j2ee.tour.security.AuthPrincipal;
import com.j2ee.tour.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@Tag(name = "Profile", description = "Self-service profile for the authenticated account")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    @Operation(summary = "Get the current profile",
            description = "Requires a Bearer access token. Customer fields are null until the profile is saved.")
    public ApiResponse<ProfileResponse> get(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.success(profileService.getProfile(principal.id()));
    }

    @PutMapping
    @Operation(summary = "Update the current profile",
            description = "Creates the customer row when missing. Email is optional; blank keeps the current "
                    + "account email. Changing the email resets verification and sends a verification link.")
    public ApiResponse<ProfileResponse> update(@AuthenticationPrincipal AuthPrincipal principal,
                                               @Valid @RequestBody ProfileRequest request) {
        return ApiResponse.success(profileService.updateProfile(principal.id(), request));
    }
}
