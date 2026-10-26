package com.j2ee.tour.controller;

import com.j2ee.tour.common.ApiResponse;
import com.j2ee.tour.common.OpenApiConfig;
import com.j2ee.tour.dto.request.LoginRequest;
import com.j2ee.tour.dto.request.RegisterRequest;
import com.j2ee.tour.dto.response.AccountResponse;
import com.j2ee.tour.dto.response.AuthResponse;
import com.j2ee.tour.security.AuthCookieService;
import com.j2ee.tour.security.AuthPrincipal;
import com.j2ee.tour.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Registration, login, refresh, logout")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieService cookieService;

    @PostMapping("/register")
    @Operation(summary = "Register a new account",
            description = "Creates an account, returns a 15-minute access token and sets the HttpOnly refresh_token cookie. Rate limit: 10 requests per minute per IP, shared with login.")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        return withCookie(authService.register(request), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in and receive an access token",
            description = "Returns a 15-minute access token and sets the HttpOnly refresh_token cookie. Rate limit: 10 requests per minute per IP, shared with register.")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        return withCookie(authService.login(request), HttpStatus.OK);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate the refresh token and issue a new access token",
            description = "Reads the refresh_token cookie, revokes it and sets a new one. Reusing a revoked token revokes every token for the account. Rate limit: 30 requests per minute per IP.")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @CookieValue(name = AuthCookieService.COOKIE_NAME, required = false) String refreshToken) {
        return withCookie(authService.refresh(refreshToken), HttpStatus.OK);
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the presented refresh token",
            description = "Idempotent. Revokes the cookie token when present and always clears the cookie.")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = AuthCookieService.COOKIE_NAME, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieService.clear().toString())
                .body(ApiResponse.<Void>success("Logged out", null));
    }

    @GetMapping("/me")
    @Operation(summary = "Get the current account",
            description = "Requires a Bearer access token.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    public ApiResponse<AccountResponse> me(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.success(authService.me(principal.id()));
    }

    private ResponseEntity<ApiResponse<AuthResponse>> withCookie(
            AuthService.AuthResult result, HttpStatus status) {
        AuthResponse body = new AuthResponse(result.accessToken(), result.account());
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookieService.create(result.refreshToken()).toString())
                .body(ApiResponse.success(body));
    }
}
