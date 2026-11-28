package com.j2ee.tour.controller;

import com.j2ee.tour.common.GlobalExceptionHandler;
import com.j2ee.tour.dto.response.VnpayReconcileResponse;
import com.j2ee.tour.security.AuthPrincipal;
import com.j2ee.tour.security.FixedWindowRateLimiter;
import com.j2ee.tour.security.JwtService;
import com.j2ee.tour.security.SecurityConfig;
import com.j2ee.tour.service.VnpayPaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminVnpayReconcileController.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AdminVnpayReconcileControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean VnpayPaymentService service;
    @MockitoBean JwtService jwtService;
    @MockitoBean FixedWindowRateLimiter limiter;

    @Test
    void rejectsNormalUser() throws Exception {
        when(jwtService.parse("user-token")).thenReturn(new AuthPrincipal(1L, "user", "ROLE_USER"));
        mvc.perform(post("/api/admin/payments/3/reconcile").header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void adminCanReconcileAnotherCustomersPayment() throws Exception {
        when(jwtService.parse("admin-token")).thenReturn(new AuthPrincipal(1L, "admin", "ROLE_ADMIN"));
        when(service.reconcileAdminPayment(eq(3L), anyString())).thenReturn(
                new VnpayReconcileResponse(77L, "ref", "PENDING", true, "TRANSACTION_UNRESOLVED", null, null));
        mvc.perform(post("/api/admin/payments/3/reconcile").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.bookingId").value(77));
    }
}
