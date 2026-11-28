package com.j2ee.tour.controller;

import com.j2ee.tour.common.GlobalExceptionHandler;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.response.AdminDepartureItem;
import com.j2ee.tour.security.*;
import com.j2ee.tour.service.AdminDepartureQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminDepartureController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AdminDepartureControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AdminDepartureQueryService service;
    @MockitoBean JwtService jwt;
    @MockitoBean FixedWindowRateLimiter limiter;

    @Test
    void forwardsFiltersAndRejectsMalformedParameters() throws Exception {
        when(jwt.parse("staff")).thenReturn(new AuthPrincipal(2L, "staff", "ROLE_STAFF"));
        when(service.list(2, 20, false, "FULL", "Bay"))
                .thenReturn(new PageResponse<>(List.of(), 2, 20, 0, 0, true));
        mvc.perform(get("/api/admin/departures?page=2&size=20&upcomingOnly=false&status=FULL&q=Bay")
                        .header("Authorization", "Bearer staff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.content").isEmpty());
        mvc.perform(get("/api/admin/departures?upcomingOnly=not-a-boolean")
                        .header("Authorization", "Bearer staff"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @CsvSource({"anonymous,401", "ROLE_USER,403", "ROLE_STAFF,200", "ROLE_ADMIN,200"})
    void departuresHaveRoleProtectionAndListShape(String role, int expected) throws Exception {
        var req = get("/api/admin/departures");
        if (!"anonymous".equals(role)) {
            when(jwt.parse(role)).thenReturn(new AuthPrincipal(1L, "demo", role));
            req.header("Authorization", "Bearer " + role);
        }
        when(service.list(0, 10, true, null, null)).thenReturn(new PageResponse<>(
                List.of(new AdminDepartureItem(4L, 2L, "Bay tour",
                        LocalDateTime.of(2026, 12, 1, 8, 0), LocalDateTime.of(2026, 12, 2, 17, 0),
                        "OPEN", 20, 6, 14)), 0, 10, 1, 1, true));
        var response = mvc.perform(req).andExpect(status().is(expected));
        if (expected == 200) response
                .andExpect(jsonPath("$.data.content[0].id").value(4))
                .andExpect(jsonPath("$.data.content[0].tourId").value(2))
                .andExpect(jsonPath("$.data.content[0].tourName").value("Bay tour"))
                .andExpect(jsonPath("$.data.content[0].startDate").value("2026-12-01T08:00:00"))
                .andExpect(jsonPath("$.data.content[0].endDate").value("2026-12-02T17:00:00"))
                .andExpect(jsonPath("$.data.content[0].capacity").value(20))
                .andExpect(jsonPath("$.data.content[0].booked").value(6))
                .andExpect(jsonPath("$.data.content[0].remaining").value(14))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }
}
