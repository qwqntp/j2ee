package com.j2ee.tour.security;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.common.GlobalExceptionHandler;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.controller.AdminController;
import com.j2ee.tour.controller.AuthController;
import com.j2ee.tour.dto.response.AccountResponse;
import com.j2ee.tour.service.AdminService;
import com.j2ee.tour.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, AdminController.class})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class SecurityConfigTest {

    private static final AccountResponse ACCOUNT =
            new AccountResponse(1L, "user01", "user01@example.com", 1, "ROLE_USER");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AuthCookieService authCookieService;

    @MockitoBean
    private AdminService adminService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private FixedWindowRateLimiter fixedWindowRateLimiter;

    @Test
    void meWithoutTokenReturnsJson401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void meWithValidTokenReturnsAccount() throws Exception {
        when(jwtService.parse("user-token"))
                .thenReturn(new AuthPrincipal(1L, "user01", "ROLE_USER"));
        when(authService.me(1L)).thenReturn(ACCOUNT);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer user-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("user01"));
    }

    @Test
    void invalidTokenReturnsJson401() throws Exception {
        when(jwtService.parse("bad-token"))
                .thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired token"));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired token"));
    }

    @Test
    void userCannotReadAdminAccounts() throws Exception {
        when(jwtService.parse("user-token"))
                .thenReturn(new AuthPrincipal(1L, "user01", "ROLE_USER"));

        mockMvc.perform(get("/api/admin/accounts").header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void adminCanReadAdminAccounts() throws Exception {
        when(jwtService.parse("admin-token"))
                .thenReturn(new AuthPrincipal(2L, "admin", "ROLE_ADMIN"));
        when(adminService.listAccounts(0, 20)).thenReturn(
                new PageResponse<>(List.of(ACCOUNT), 0, 20, 1, 1, true));

        mockMvc.perform(get("/api/admin/accounts").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].username").value("user01"));
    }
}
