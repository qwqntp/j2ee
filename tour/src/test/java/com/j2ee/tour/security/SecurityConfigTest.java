package com.j2ee.tour.security;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.common.GlobalExceptionHandler;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.controller.AdminController;
import com.j2ee.tour.controller.AuthController;
import com.j2ee.tour.controller.BookingController;
import com.j2ee.tour.controller.InvoiceController;
import com.j2ee.tour.dto.request.CreateBookingRequest;
import com.j2ee.tour.dto.response.AccountResponse;
import com.j2ee.tour.service.AdminService;
import com.j2ee.tour.service.AuthService;
import com.j2ee.tour.service.BookingService;
import com.j2ee.tour.service.InvoiceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, AdminController.class,
        BookingController.class, InvoiceController.class})
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
    private BookingService bookingService;

    @MockitoBean
    private InvoiceService invoiceService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private FixedWindowRateLimiter fixedWindowRateLimiter;

    @Test
    void corsPreflightAllowsFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

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

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/accounts",
            "/api/account-roles",
            "/api/tour-departures",
            "/api/promotions",
            "/api/payments",
            "/api/refunds",
            "/api/invoices",
            "/api/customers",
            "/api/travelers",
            "/api/tour-guides",
            "/api/tour-images",
            "/api/accommodations",
            "/api/travel-vehicles",
            "/api/tourist-destinations"
    })
    void userCannotReadAdminOnlyCrudEndpoints(String path) throws Exception {
        when(jwtService.parse("user-token"))
                .thenReturn(new AuthPrincipal(1L, "user01", "ROLE_USER"));

        mockMvc.perform(get(path).header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void userCannotCreatePaymentOrInvokeFakeCallback() throws Exception {
        when(jwtService.parse("user-token"))
                .thenReturn(new AuthPrincipal(1L, "user01", "ROLE_USER"));

        mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer user-token")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/bookings/1/payments/callback")
                        .header("Authorization", "Bearer user-token")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void alreadyStartedDepartureReturns409() throws Exception {
        when(jwtService.parse("user-token"))
                .thenReturn(new AuthPrincipal(1L, "user01", "ROLE_USER"));
        when(bookingService.create(any(CreateBookingRequest.class), any(AuthPrincipal.class)))
                .thenThrow(new IllegalStateException("Departure has already started"));

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer user-token")
                        .contentType("application/json")
                        .content("{\"departureId\":1,\"guestCount\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Departure has already started"));
    }

    @Test
    void forgotPasswordEndpointIsPublic() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType("application/json")
                        .content("{\"email\":\"user01@example.com\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void userCanReadOwnInvoices() throws Exception {
        when(jwtService.parse("user-token"))
                .thenReturn(new AuthPrincipal(1L, "user01", "ROLE_USER"));
        when(invoiceService.listMine(anyLong(), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, true));

        mockMvc.perform(get("/api/invoices/me")
                        .header("Authorization", "Bearer user-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty());
    }

    @Test
    void publicTourReadDoesNotRequireAuthentication() throws Exception {
        when(jwtService.parse("user-token"))
                .thenReturn(new AuthPrincipal(1L, "user01", "ROLE_USER"));

        mockMvc.perform(get("/api/tours"))
                .andExpect(status().isNotFound());
    }
}
