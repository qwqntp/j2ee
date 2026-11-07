package com.j2ee.tour.controller;

import com.j2ee.tour.common.GlobalExceptionHandler;
import com.j2ee.tour.dto.response.TourRatingPageResponse;
import com.j2ee.tour.dto.response.TourRatingResponse;
import com.j2ee.tour.security.FixedWindowRateLimiter;
import com.j2ee.tour.security.JwtService;
import com.j2ee.tour.service.TourRatingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TourRatingController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(GlobalExceptionHandler.class)
class TourRatingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TourRatingService ratingService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private FixedWindowRateLimiter fixedWindowRateLimiter;

    @Test
    void listReturnsAverageAndPage() throws Exception {
        TourRatingResponse rating = new TourRatingResponse(
                7L, 1L, 2L, "traveler", 30L, 5, "Great", LocalDateTime.of(2026, 11, 7, 10, 0));
        TourRatingPageResponse page = new TourRatingPageResponse(
                List.of(rating), 0, 20, 1, 1, true, false, false,
                new BigDecimal("5.00"), 1);
        when(ratingService.list(eq(1L), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/tours/1/ratings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.averageRating").value(5.00))
                .andExpect(jsonPath("$.data.content[0].comment").value("Great"));
    }

    @Test
    void createReturnsCreatedRating() throws Exception {
        TourRatingResponse rating = new TourRatingResponse(
                7L, 1L, 2L, "traveler", 30L, 5, "Great", LocalDateTime.of(2026, 11, 7, 10, 0));
        when(ratingService.create(eq(1L), any(), any())).thenReturn(rating);

        mockMvc.perform(post("/api/tours/1/ratings")
                        .contentType("application/json")
                        .content("{\"bookingId\":30,\"rating\":5,\"comment\":\"Great\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.rating").value(5));
    }
}
