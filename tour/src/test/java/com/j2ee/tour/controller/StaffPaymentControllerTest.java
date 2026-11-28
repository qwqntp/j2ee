package com.j2ee.tour.controller;

import com.j2ee.tour.common.GlobalExceptionHandler;
import com.j2ee.tour.security.AuthPrincipal;
import com.j2ee.tour.security.FixedWindowRateLimiter;
import com.j2ee.tour.security.JwtService;
import com.j2ee.tour.security.SecurityConfig;
import com.j2ee.tour.service.PaymentService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentCrudController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class StaffPaymentControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean PaymentService payments;
    @MockitoBean JwtService jwt;
    @MockitoBean FixedWindowRateLimiter limiter;

    @ParameterizedTest
    @CsvSource({"ROLE_STAFF,CASH,SUCCESS,201", "ROLE_STAFF,CARD,SUCCESS,403",
            "ROLE_STAFF,VNPAY,SUCCESS,403", "ROLE_STAFF,MOMO,SUCCESS,403",
            "ROLE_STAFF,CASH,PENDING,403", "ROLE_STAFF,CASH,FAILED,403",
            "ROLE_ADMIN,CARD,SUCCESS,201", "ROLE_USER,CASH,SUCCESS,403"})
    void staffCanOnlyRecordSuccessfulCash(String role, String method, String state, int expected) throws Exception {
        when(jwt.parse("token")).thenReturn(new AuthPrincipal(7L, "demo", role));
        mvc.perform(post("/api/payments").header("Authorization", "Bearer token")
                        .contentType("application/json").content("""
                                {"bookingId":1,"paymentMethod":"%s","status":"%s"}
                                """.formatted(method, state)))
                .andExpect(status().is(expected));
        if (expected == 403) verify(payments, never()).create(any());
        else verify(payments).create(any());
    }
}
