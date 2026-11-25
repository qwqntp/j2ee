package com.j2ee.tour.service;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.response.AdminDepartureItem;
import com.j2ee.tour.repository.AdminDepartureRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;

@Service
public class AdminDepartureQueryService {
    private static final Set<String> STATUSES = Set.of("OPEN", "FULL", "CLOSED", "CANCELLED");
    private final AdminDepartureRepository repository;
    private final Clock clock;
    private final int maxPageSize;

    public AdminDepartureQueryService(AdminDepartureRepository repository, Clock clock,
                                     @Value("${app.admin.list.max-page-size:50}") int maxPageSize) {
        this.repository = repository;
        this.clock = clock;
        this.maxPageSize = maxPageSize;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminDepartureItem> list(int page, int size, boolean upcomingOnly, String status, String q) {
        if (page < 0 || size < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "page must be non-negative and size must be positive");
        }
        String state = status == null || status.isBlank() ? null : status.trim();
        if (state != null && !STATUSES.contains(state)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid departure status");
        }
        if (q != null && q.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Tour name keyword must not exceed 100 characters");
        }
        String keyword = q == null || q.isBlank() ? null : q.trim();
        return PageResponse.from(repository.search(PageRequest.of(page, Math.min(size, maxPageSize)),
                upcomingOnly, state, keyword, LocalDateTime.now(clock)));
    }
}
