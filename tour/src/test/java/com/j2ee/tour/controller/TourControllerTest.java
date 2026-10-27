package com.j2ee.tour.controller;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.common.GlobalExceptionHandler;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.response.TourDepartureResponse;
import com.j2ee.tour.dto.response.TourResponse;
import com.j2ee.tour.dto.response.TourSummaryResponse;
import com.j2ee.tour.service.TourService;
import com.j2ee.tour.security.FixedWindowRateLimiter;
import com.j2ee.tour.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TourController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(GlobalExceptionHandler.class)
class TourControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TourService tourService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private FixedWindowRateLimiter fixedWindowRateLimiter;

    @Test
    void listReturnsPage() throws Exception {
        TourSummaryResponse summary = new TourSummaryResponse(
                1L,
                "Da Nang - Hoi An",
                "Ngay 1: Da Nang",
                "OPEN",
                "https://img/1.jpg",
                new BigDecimal("4500000"),
                LocalDateTime.of(2026, 11, 1, 8, 0),
                28,
                1);
        PageResponse<TourSummaryResponse> page = new PageResponse<>(
                List.of(summary), 0, 12, 1, 1, true);
        when(tourService.listTours(0, 12, null)).thenReturn(page);

        mockMvc.perform(get("/api/tours"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].name").value("Da Nang - Hoi An"))
                .andExpect(jsonPath("$.data.content[0].priceFrom").value(4500000))
                .andExpect(jsonPath("$.data.content[0].seatsAvailable").value(28));
    }

    @Test
    void getReturnsTour() throws Exception {
        TourResponse tour = new TourResponse(
                1L, "Da Nang - Hoi An", "Ngay 1: Da Nang", "OPEN",
                null, null, null, null, null,
                List.of(), List.of("Ngay 1: Da Nang"), List.of(), List.of(), List.of());
        when(tourService.getTour(1L)).thenReturn(tour);

        mockMvc.perform(get("/api/tours/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Da Nang - Hoi An"));
    }

    @Test
    void getReturnsNotFound() throws Exception {
        when(tourService.getTour(99L))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Tour not found"));

        mockMvc.perform(get("/api/tours/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Tour not found"));
    }

    @Test
    void invalidPagingReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/tours").param("page", "-1"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/tours").param("size", "51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void keywordTooLongReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/tours").param("keyword", "a".repeat(101)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidIdReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/tours/not-a-number"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void departuresReturnsList() throws Exception {
        TourDepartureResponse departure = new TourDepartureResponse(
                1L, 1L,
                LocalDateTime.of(2026, 11, 1, 8, 0),
                LocalDateTime.of(2026, 11, 3, 17, 0),
                30, 2, 28,
                new BigDecimal("4500000"),
                "San bay Da Nang",
                "OPEN",
                LocalDateTime.of(2026, 10, 1, 8, 0));
        when(tourService.getDepartures(1L)).thenReturn(List.of(departure));

        mockMvc.perform(get("/api/tours/1/departures"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].seatsAvailable").value(28));
    }

    @Test
    void unknownPathReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void unsupportedMethodReturnsMethodNotAllowed() throws Exception {
        mockMvc.perform(post("/api/tours"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false));
    }
}
