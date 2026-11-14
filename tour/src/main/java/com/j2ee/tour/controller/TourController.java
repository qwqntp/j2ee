package com.j2ee.tour.controller;

import com.j2ee.tour.common.ApiResponse;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.TourSearchCriteria;
import com.j2ee.tour.dto.response.TourDepartureResponse;
import com.j2ee.tour.dto.response.TourDestinationOptionResponse;
import com.j2ee.tour.dto.response.TourResponse;
import com.j2ee.tour.dto.response.TourSummaryResponse;
import com.j2ee.tour.service.TourService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/tours")
@RequiredArgsConstructor
@Validated
@Tag(name = "Tours", description = "Public tour catalogue")
public class TourController {

    private final TourService tourService;

    @GetMapping
    @Operation(summary = "List open tours",
            description = "Public. Returns OPEN tours only. page is 0-based; size is 1-50; keyword is at most "
                    + "100 characters. Card stats include upcoming OPEN and FULL departures. Optional filters: "
                    + "destinationId, minPrice/maxPrice and startFrom/startTo (one upcoming departure must match all "
                    + "supplied price/date filters), minDays/maxDays. sort is one of newest, priceAsc, priceDesc, "
                    + "dateAsc; default is tourId ascending.")
    public ApiResponse<PageResponse<TourSummaryResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(50) int size,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) Long destinationId,
            @RequestParam(required = false) @DecimalMin(value = "0", message = "minPrice must be at least 0")
            BigDecimal minPrice,
            @RequestParam(required = false) @DecimalMin(value = "0", message = "maxPrice must be at least 0")
            BigDecimal maxPrice,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startTo,
            @RequestParam(required = false) @Min(value = 1, message = "minDays must be at least 1")
            Integer minDays,
            @RequestParam(required = false) @Min(value = 1, message = "maxDays must be at least 1")
            Integer maxDays,
            @RequestParam(required = false) String sort) {
        TourSearchCriteria criteria = new TourSearchCriteria(
                keyword, destinationId, minPrice, maxPrice, startFrom, startTo,
                minDays, maxDays, TourSearchCriteria.parseSort(sort));
        return ApiResponse.success(tourService.listTours(page, size, criteria));
    }

    @GetMapping("/destinations")
    @Operation(summary = "List tourist destinations",
            description = "Public. Returns every tourist destination as {id, name}, sorted by name. "
                    + "Used by the catalogue destination filter.")
    public ApiResponse<List<TourDestinationOptionResponse>> destinations() {
        return ApiResponse.success(tourService.listDestinations());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an open tour",
            description = "Public. Returns an OPEN tour with future OPEN/FULL departures. Non-public or missing tours return 404.")
    public ApiResponse<TourResponse> get(@PathVariable Long id) {
        return ApiResponse.success(tourService.getTour(id));
    }

    @GetMapping("/{id}/departures")
    @Operation(summary = "List upcoming departures for an open tour",
            description = "Public. Returns future OPEN and FULL departures for an OPEN tour, sorted by start date.")
    public ApiResponse<List<TourDepartureResponse>> departures(@PathVariable Long id) {
        return ApiResponse.success(tourService.getDepartures(id));
    }
}
