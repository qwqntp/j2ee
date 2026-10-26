package com.j2ee.tour.controller;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.common.GlobalExceptionHandler;
import com.j2ee.tour.dto.response.AccountResponse;
import com.j2ee.tour.security.AuthCookieService;
import com.j2ee.tour.security.FixedWindowRateLimiter;
import com.j2ee.tour.security.JwtService;
import com.j2ee.tour.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    private static final AccountResponse ACCOUNT =
            new AccountResponse(1L, "user01", "user01@example.com", 1, "ROLE_USER");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AuthCookieService cookieService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private FixedWindowRateLimiter fixedWindowRateLimiter;

    @Test
    void loginReturnsAccessTokenAndSetsRefreshCookie() throws Exception {
        when(authService.login(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AuthService.AuthResult("access-1", "refresh-1", ACCOUNT));
        when(cookieService.create("refresh-1")).thenReturn(cookie("refresh-1", Duration.ofDays(7)));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"user01\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("refresh_token=refresh-1")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("HttpOnly")))
                .andExpect(jsonPath("$.data.accessToken").value("access-1"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
    }

    @Test
    void registerReturnsCreated() throws Exception {
        when(authService.register(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AuthService.AuthResult("access-1", "refresh-1", ACCOUNT));
        when(cookieService.create("refresh-1")).thenReturn(cookie("refresh-1", Duration.ofDays(7)));

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("{\"username\":\"user01\",\"password\":\"password123\","
                                + "\"email\":\"user01@example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.account.username").value("user01"));
    }

    @Test
    void refreshWithoutCookieReturnsUnauthorized() throws Exception {
        when(authService.refresh(null))
                .thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid refresh token"));
    }

    @Test
    void refreshWithCookieRotatesIt() throws Exception {
        when(authService.refresh("old-refresh"))
                .thenReturn(new AuthService.AuthResult("access-2", "refresh-2", ACCOUNT));
        when(cookieService.create("refresh-2")).thenReturn(cookie("refresh-2", Duration.ofDays(7)));

        mockMvc.perform(post("/api/auth/refresh").cookie(new jakarta.servlet.http.Cookie(
                        AuthCookieService.COOKIE_NAME, "old-refresh")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("refresh_token=refresh-2")))
                .andExpect(jsonPath("$.data.accessToken").value("access-2"));
    }

    @Test
    void logoutClearsCookie() throws Exception {
        when(cookieService.clear()).thenReturn(cookie("", Duration.ZERO));

        mockMvc.perform(post("/api/auth/logout").cookie(new jakarta.servlet.http.Cookie(
                        AuthCookieService.COOKIE_NAME, "refresh-1")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("Max-Age=0")));
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(AuthCookieService.COOKIE_NAME, value)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
    }
}
