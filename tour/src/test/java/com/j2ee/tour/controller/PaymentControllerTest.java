package com.j2ee.tour.controller;

import com.j2ee.tour.common.GlobalExceptionHandler;
import com.j2ee.tour.dto.response.PaymentResponse;
import com.j2ee.tour.security.FixedWindowRateLimiter;
import com.j2ee.tour.security.JwtService;
import com.j2ee.tour.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(GlobalExceptionHandler.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private FixedWindowRateLimiter fixedWindowRateLimiter;

    @Test
    void paymentsReturnsBookingHistory() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 11, 12, 10, 0);
        when(paymentService.findByBooking(42L)).thenReturn(List.of(
                new PaymentResponse(9L, 42L, new BigDecimal("1000.00"), "CARD",
                        "MOCK-9", "SUCCESS", now, now)));

        mockMvc.perform(get("/api/bookings/42/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("SUCCESS"));

        verify(paymentService).findByBooking(42L);
    }
}
